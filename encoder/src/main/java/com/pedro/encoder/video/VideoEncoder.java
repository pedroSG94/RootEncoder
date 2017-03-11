package com.pedro.encoder.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;

import android.view.Surface;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.YUVUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.PARAMETER_KEY_VIDEO_BITRATE;

/**
 * Created by pedro on 19/01/17.
 * This class need use same resolution, fps and rotation that Camera1ApiManager
 */

public class VideoEncoder implements GetCameraData {

  private String TAG = "VideoEncoder";
  private MediaCodec videoEncoder;
  private Thread thread;
  private GetH264Data getH264Data;
  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private long mPresentTimeUs;
  private boolean running;
  //for surface to buffer encoder
  private Surface inputSurface;

  //default parameters for encoder
  private String codec = "video/avc";
  private int width = 1280;
  private int height = 720;
  private int fps = 30;
  private int bitRate = 1500 * 1000; //in kbps
  private int rotation = 0;
  private FormatVideoEncoder formatVideoEncoder = FormatVideoEncoder.YUV420Dynamical;

  public VideoEncoder(GetH264Data getH264Data) {
    this.getH264Data = getH264Data;
  }

  /**
   * Prepare encoder with custom parameters
   */
  public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, int rotation,
      FormatVideoEncoder formatVideoEncoder) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.bitRate = bitRate;
    this.formatVideoEncoder = formatVideoEncoder;
    MediaCodecInfo encoder;
    if (Build.VERSION.SDK_INT >= 21) {
      encoder = chooseVideoEncoderAPI21(codec);
    } else {
      encoder = chooseVideoEncoder(codec);
    }
    try {
      if (encoder != null) {
        videoEncoder = MediaCodec.createByCodecName(encoder.getName());
        if (this.formatVideoEncoder == FormatVideoEncoder.YUV420Dynamical) {
          this.formatVideoEncoder = chooseColorDynamically(encoder);
          if (this.formatVideoEncoder == null) {
            Log.e(TAG, "YUV420 dynamical choose failed");
            return false;
          }
        }
      } else {
        Log.e(TAG, "valid encoder not found");
        return false;
      }

      MediaFormat videoFormat = MediaFormat.createVideoFormat(codec, width, height);
      videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
          this.formatVideoEncoder.getFormatCodec());
      videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
      videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
      videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
      videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
      videoFormat.setInteger("rotation-degrees", rotation);
      videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      running = false;
      if (formatVideoEncoder == FormatVideoEncoder.SURFACE
          && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        inputSurface = videoEncoder.createInputSurface();
      }
      return true;
    } catch (IOException e) {
      Log.e(TAG, "create videoEncoder failed.");
      e.printStackTrace();
      return false;
    } catch (IllegalStateException e) {
      e.printStackTrace();
      return false;
    }
  }

  private FormatVideoEncoder chooseColorDynamically(MediaCodecInfo mediaCodecInfo) {
    for (int color : mediaCodecInfo.getCapabilitiesForType(codec).colorFormats) {
      if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()) {
        return FormatVideoEncoder.YUV420PLANAR;
      } else if (color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()) {
        return FormatVideoEncoder.YUV420SEMIPLANAR;
      } else if (color == FormatVideoEncoder.YUV420PACKEDPLANAR.getFormatCodec()) {
        return FormatVideoEncoder.YUV420PACKEDPLANAR;
      }
    }
    return null;
  }

  /**
   * Prepare encoder with default parameters
   */
  public boolean prepareVideoEncoder() {
    return prepareVideoEncoder(width, height, fps, bitRate, rotation, formatVideoEncoder);
  }

  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  public void setVideoBitrateOnFly(int bitrate) {
    this.bitRate = bitrate;
    Bundle bundle = new Bundle();
    bundle.putInt(PARAMETER_KEY_VIDEO_BITRATE, bitrate);
    try {
      videoEncoder.setParameters(bundle);
    } catch (IllegalStateException e){
      Log.e(TAG, "encoder need be running");
      e.printStackTrace();
    }
  }

  public Surface getInputSurface() {
    return inputSurface;
  }

  public void setInputSurface(Surface inputSurface) {
    this.inputSurface = inputSurface;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public boolean isRunning() {
    return running;
  }

  public void start() {
    mPresentTimeUs = System.nanoTime() / 1000;
    videoEncoder.start();
    if (formatVideoEncoder == FormatVideoEncoder.SURFACE
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      if (Build.VERSION.SDK_INT >= 21) {
        getDataFromSurfaceAPI21();
      } else {
        getDataFromSurface();
      }
    }
    running = true;
  }

  public void stop() {
    running = false;
    if (thread != null) {
      thread.interrupt();
      thread = null;
    }
    if (videoEncoder != null) {
      videoEncoder.stop();
      videoEncoder.release();
      videoEncoder = null;
    }
  }

  @Override
  public void inputYv12Data(final byte[] buffer, final int width, final int height) {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        if (formatVideoEncoder != FormatVideoEncoder.SURFACE) {
          byte[] i420 = YUVUtil.YV12toYUV420byColor(buffer, width, height, formatVideoEncoder);
          if (Build.VERSION.SDK_INT >= 21) {
            getDataFromEncoderAPI21(i420);
          } else {
            getDataFromEncoder(i420);
          }
        }
      }
    });
    thread.start();
  }

  @Override
  public void inputNv21Data(final byte[] buffer, final int width, final int height) {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        if (formatVideoEncoder != FormatVideoEncoder.SURFACE) {
          byte[] i420 = YUVUtil.NV21toYUV420byColor(buffer, width, height, formatVideoEncoder);
          if (Build.VERSION.SDK_INT >= 21) {
            getDataFromEncoderAPI21(i420);
          } else {
            getDataFromEncoder(i420);
          }
        }
      }
    });
    thread.start();
  }

  /**
   * call it to encoder YUV 420, 422, 444
   * remember create encoder with correct color format before
   */

  public void inputYuv4XX(final byte[] buffer) {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        if (formatVideoEncoder == FormatVideoEncoder.SURFACE) {
          if (Build.VERSION.SDK_INT >= 21) {
            getDataFromEncoderAPI21(buffer);
          } else {
            getDataFromEncoder(buffer);
          }
        }
      }
    });
    thread.start();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void getDataFromSurfaceAPI21() {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!Thread.interrupted()) {
          for (; ; ) {
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
            if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
              MediaFormat mediaFormat = videoEncoder.getOutputFormat();
              getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                  mediaFormat.getByteBuffer("csd-1"));
            } else if (outBufferIndex >= 0) {
              //This ByteBuffer is H264
              ByteBuffer bb = videoEncoder.getOutputBuffer(outBufferIndex);
              getH264Data.getH264Data(bb, videoInfo);
              videoEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
              break;
            }
          }
        }
      }
    });
    thread.start();
  }

  private void getDataFromSurface() {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!Thread.interrupted()) {
          ByteBuffer[] outputBuffers = videoEncoder.getOutputBuffers();
          for (; ; ) {
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
            if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
              MediaFormat mediaFormat = videoEncoder.getOutputFormat();
              getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                  mediaFormat.getByteBuffer("csd-1"));
            } else if (outBufferIndex >= 0) {
              //This ByteBuffer is H264
              ByteBuffer bb = outputBuffers[outBufferIndex];
              getH264Data.getH264Data(bb, videoInfo);
              videoEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
              break;
            }
          }
        }
      }
    });
    thread.start();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private synchronized void getDataFromEncoderAPI21(byte[] buffer) {
    int inBufferIndex = videoEncoder.dequeueInputBuffer(-1);
    if (inBufferIndex >= 0) {
      ByteBuffer bb = videoEncoder.getInputBuffer(inBufferIndex);
      bb.put(buffer, 0, buffer.length);
      long pts = System.nanoTime() / 1000 - mPresentTimeUs;
      videoEncoder.queueInputBuffer(inBufferIndex, 0, buffer.length, pts, 0);
    }

    for (; ; ) {
      int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
      if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat mediaFormat = videoEncoder.getOutputFormat();
        getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
            mediaFormat.getByteBuffer("csd-1"));
      } else if (outBufferIndex >= 0) {
        //This ByteBuffer is H264
        ByteBuffer bb = videoEncoder.getOutputBuffer(outBufferIndex);
        getH264Data.getH264Data(bb, videoInfo);
        videoEncoder.releaseOutputBuffer(outBufferIndex, false);
      } else {
        break;
      }
    }
  }

  private synchronized void getDataFromEncoder(byte[] buffer) {
    ByteBuffer[] inputBuffers = videoEncoder.getInputBuffers();
    ByteBuffer[] outputBuffers = videoEncoder.getOutputBuffers();

    int inBufferIndex = videoEncoder.dequeueInputBuffer(-1);
    if (inBufferIndex >= 0) {
      ByteBuffer bb = inputBuffers[inBufferIndex];
      bb.clear();
      bb.put(buffer, 0, buffer.length);
      long pts = System.nanoTime() / 1000 - mPresentTimeUs;
      videoEncoder.queueInputBuffer(inBufferIndex, 0, buffer.length, pts, 0);
    }

    for (; ; ) {
      int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
      if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat mediaFormat = videoEncoder.getOutputFormat();
        getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
            mediaFormat.getByteBuffer("csd-1"));
      } else if (outBufferIndex >= 0) {
        //This ByteBuffer is H264
        ByteBuffer bb = outputBuffers[outBufferIndex];
        getH264Data.getH264Data(bb, videoInfo);
        videoEncoder.releaseOutputBuffer(outBufferIndex, false);
      } else {
        break;
      }
    }
  }

  /**
   * choose the video encoder by mime. API 21+
   */
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private MediaCodecInfo chooseVideoEncoderAPI21(String mime) {
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
    MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
    for (MediaCodecInfo mci : mediaCodecInfos) {
      if (!mci.isEncoder()) {
        continue;
      }
      String[] types = mci.getSupportedTypes();
      for (String type : types) {
        if (type.equalsIgnoreCase(mime)) {
          Log.i(TAG, String.format("videoEncoder %s type supported: %s", mci.getName(), type));
          MediaCodecInfo.CodecCapabilities codecCapabilities = mci.getCapabilitiesForType(mime);
          for (int color : codecCapabilities.colorFormats) {
            Log.i(TAG, "Color supported: " + color);
            //check if encoder support any yuv420 color
            if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()
                || color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()
                || color == FormatVideoEncoder.YUV420PACKEDPLANAR.getFormatCodec()) {
              return mci;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * choose the video encoder by mime. API < 21
   */
  private MediaCodecInfo chooseVideoEncoder(String mime) {
    int count = MediaCodecList.getCodecCount();
    for (int i = 0; i < count; i++) {
      MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
      if (!mci.isEncoder()) {
        continue;
      }
      String[] types = mci.getSupportedTypes();
      for (String type : types) {
        if (type.equalsIgnoreCase(mime)) {
          Log.i(TAG, String.format("videoEncoder %s type supported: %s", mci.getName(), type));
          MediaCodecInfo.CodecCapabilities codecCapabilities = mci.getCapabilitiesForType(mime);
          for (int color : codecCapabilities.colorFormats) {
            Log.i(TAG, "Color supported: " + color);
            //check if encoder support any yuv420 color
            if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()
                || color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()
                || color == FormatVideoEncoder.YUV420PACKEDPLANAR.getFormatCodec()) {
              return mci;
            }
          }
        }
      }
    }
    return null;
  }
}