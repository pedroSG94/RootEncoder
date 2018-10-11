package com.pedro.encoder.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import com.pedro.encoder.input.video.FpsLimiter;
import com.pedro.encoder.input.video.Frame;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.utils.YUVUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by pedro on 19/01/17.
 * This class need use same resolution, fps and imageFormat that Camera1ApiManagerGl
 */

public class VideoEncoder implements GetCameraData {

  private String TAG = "VideoEncoder";
  private MediaCodec videoEncoder;
  private Thread thread;
  private GetH264Data getH264Data;
  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private long mPresentTimeUs;
  private boolean running = false;
  private boolean spsPpsSetted = false;
  private boolean hardwareRotation = false;

  //surface to buffer encoder
  private Surface inputSurface;
  //buffer to buffer, 3 queue to optimize frames on rotation
  private BlockingQueue<Frame> queue = new LinkedBlockingQueue<>(80);
  private final Object sync = new Object();

  //default parameters for encoder
  private CodecUtil.Force force = CodecUtil.Force.FIRST_COMPATIBLE_FOUND;
  private int width = 640;
  private int height = 480;
  private int fps = 30;
  private int bitRate = 1200 * 1024; //in kbps
  private int rotation = 90;
  private int iFrameInterval = 2;
  private FormatVideoEncoder formatVideoEncoder = FormatVideoEncoder.YUV420Dynamical;
  //for disable video
  private boolean sendBlackImage = false;
  private byte[] blackImage;
  private FpsLimiter fpsLimiter = new FpsLimiter();

  public VideoEncoder(GetH264Data getH264Data) {
    this.getH264Data = getH264Data;
  }

  /**
   * Prepare encoder with custom parameters
   */
  public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, int rotation,
      boolean hardwareRotation, int iFrameInterval, FormatVideoEncoder formatVideoEncoder) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.bitRate = bitRate;
    this.rotation = rotation;
    this.hardwareRotation = hardwareRotation;
    this.formatVideoEncoder = formatVideoEncoder;
    MediaCodecInfo encoder = chooseVideoEncoder(CodecUtil.H264_MIME);
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
        Log.e(TAG, "Valid encoder not found");
        return false;
      }
      MediaFormat videoFormat;
      //if you dont use mediacodec rotation you need swap width and height in rotation 90 or 270
      // for correct encoding resolution
      String resolution;
      if (!hardwareRotation && (rotation == 90 || rotation == 270)) {
        resolution = height + "x" + width;
        videoFormat = MediaFormat.createVideoFormat(CodecUtil.H264_MIME, height, width);
      } else {
        resolution = width + "x" + height;
        videoFormat = MediaFormat.createVideoFormat(CodecUtil.H264_MIME, width, height);
      }
      Log.i(TAG, "Prepare video info: " + this.formatVideoEncoder.name() + ", " + resolution);
      videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
          this.formatVideoEncoder.getFormatCodec());
      videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
      videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
      videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
      videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
      if (hardwareRotation) {
        videoFormat.setInteger("rotation-degrees", rotation);
      }
      videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      running = false;
      if (formatVideoEncoder == FormatVideoEncoder.SURFACE
          && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        inputSurface = videoEncoder.createInputSurface();
      }
      prepareBlackImage();
      return true;
    } catch (IOException | IllegalStateException e) {
      Log.e(TAG, "create videoEncoder failed.", e);
      return false;
    }
  }

  private FormatVideoEncoder chooseColorDynamically(MediaCodecInfo mediaCodecInfo) {
    for (int color : mediaCodecInfo.getCapabilitiesForType(CodecUtil.H264_MIME).colorFormats) {
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
    return prepareVideoEncoder(width, height, fps, bitRate, rotation, false, iFrameInterval,
        formatVideoEncoder);
  }

  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  public void setVideoBitrateOnFly(int bitrate) {
    if (isRunning()) {
      this.bitRate = bitrate;
      Bundle bundle = new Bundle();
      bundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
      try {
        videoEncoder.setParameters(bundle);
      } catch (IllegalStateException e) {
        Log.e(TAG, "encoder need be running", e);
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  public void forceSyncFrame() {
    if (isRunning()) {
      Bundle bundle = new Bundle();
      bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
      try {
        videoEncoder.setParameters(bundle);
      } catch (IllegalStateException e) {
        Log.e(TAG, "encoder need be running", e);
      }
    }
  }

  public void setForce(CodecUtil.Force force) {
    this.force = force;
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

  public boolean isHardwareRotation() {
    return hardwareRotation;
  }

  public boolean isRunning() {
    return running;
  }

  public int getRotation() {
    return rotation;
  }

  public void setFps(int fps) {
    this.fps = fps;
  }

  public int getFps() {
    return fps;
  }

  public void start() {
    start(true);
  }

  public int getBitRate() {
    return bitRate;
  }

  public void start(boolean resetTs) {
    synchronized (sync) {
      if (videoEncoder != null) {
        spsPpsSetted = false;
        if (resetTs) mPresentTimeUs = System.nanoTime() / 1000;
        videoEncoder.start();
        //surface to buffer
        if (formatVideoEncoder == FormatVideoEncoder.SURFACE
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
          if (Build.VERSION.SDK_INT >= 21) {
            getDataFromSurfaceAPI21();
          } else {
            getDataFromSurface();
          }
          //buffer to buffer
        } else {
          if (!(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270)) {
            throw new RuntimeException(
                "rotation value unsupported, select value 0, 90, 180 or 270");
          }
          thread = new Thread(new Runnable() {
            @Override
            public void run() {
              YUVUtil.preAllocateBuffers(width * height * 3 / 2);
              while (!Thread.interrupted()) {
                try {
                  Frame frame = queue.take();
                  if (fpsLimiter.limitFPS(fps)) continue;
                  byte[] buffer = frame.getBuffer();
                  if (frame.getFormat() == ImageFormat.YV12) {
                    buffer = YUVUtil.YV12toNV21(buffer, width, height);
                  }
                  if (!hardwareRotation) {
                    int orientation =
                        frame.isFlip() ? frame.getOrientation() + 180 : frame.getOrientation();
                    if (orientation >= 360) orientation -= 360;
                    buffer = YUVUtil.rotateNV21(buffer, width, height, orientation);
                  }
                  buffer = (sendBlackImage) ? blackImage
                      : YUVUtil.NV21toYUV420byColor(buffer, width, height, formatVideoEncoder);
                  if (Build.VERSION.SDK_INT >= 21) {
                    getDataFromEncoderAPI21(buffer);
                  } else {
                    getDataFromEncoder(buffer);
                  }
                } catch (InterruptedException e) {
                  if (thread != null) thread.interrupt();
                }
              }
            }
          });
          thread.start();
        }
        running = true;
      } else {
        Log.e(TAG, "VideoEncoder need be prepared, VideoEncoder not enabled");
      }
    }
  }

  public void stop() {
    synchronized (sync) {
      running = false;
      if (thread != null) {
        thread.interrupt();
        try {
          thread.join(1000);
        } catch (InterruptedException e) {
          thread.interrupt();
        }
        thread = null;
      }
      if (videoEncoder != null) {
        videoEncoder.stop();
        videoEncoder.release();
        videoEncoder = null;
      }
      queue.clear();
      fpsLimiter.reset();
      spsPpsSetted = false;
      inputSurface = null;
    }
  }

  public void reset() {
    synchronized (sync) {
      stop();
      prepareVideoEncoder(width, height, fps, bitRate, rotation, hardwareRotation, iFrameInterval,
          formatVideoEncoder);
      start(false);
    }
  }

  @Override
  public void inputYUVData(Frame frame) {
    synchronized (sync) {
      if (running) {
        try {
          queue.add(frame);
        } catch (IllegalStateException e) {
          Log.i(TAG, "frame discarded");
        }
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void getDataFromSurfaceAPI21() {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!Thread.interrupted()) {
          for (; ; ) {
            if (fpsLimiter.limitFPS(fps)) continue;
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
            if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
              MediaFormat mediaFormat = videoEncoder.getOutputFormat();
              getH264Data.onVideoFormat(mediaFormat);
              getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                  mediaFormat.getByteBuffer("csd-1"));
              spsPpsSetted = true;
            } else if (outBufferIndex >= 0) {
              //This ByteBuffer is H264
              ByteBuffer bb = videoEncoder.getOutputBuffer(outBufferIndex);
              if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                if (!spsPpsSetted) {
                  Pair<ByteBuffer, ByteBuffer> buffers =
                      decodeSpsPpsFromBuffer(bb.duplicate(), videoInfo.size);
                  if (buffers != null) {
                    getH264Data.onSPSandPPS(buffers.first, buffers.second);
                    spsPpsSetted = true;
                  }
                }
              }
              videoInfo.presentationTimeUs = System.nanoTime() / 1000 - mPresentTimeUs;
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
            if (fpsLimiter.limitFPS(fps)) continue;
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 10000);
            if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
              MediaFormat mediaFormat = videoEncoder.getOutputFormat();
              getH264Data.onVideoFormat(mediaFormat);
              getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                  mediaFormat.getByteBuffer("csd-1"));
              spsPpsSetted = true;
            } else if (outBufferIndex >= 0) {
              //This ByteBuffer is H264
              ByteBuffer bb = outputBuffers[outBufferIndex];
              if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                if (!spsPpsSetted) {
                  Pair<ByteBuffer, ByteBuffer> buffers =
                      decodeSpsPpsFromBuffer(bb.duplicate(), videoInfo.size);
                  if (buffers != null) {
                    getH264Data.onSPSandPPS(buffers.first, buffers.second);
                    spsPpsSetted = true;
                  }
                }
              }
              videoInfo.presentationTimeUs = System.nanoTime() / 1000 - mPresentTimeUs;
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
  private void getDataFromEncoderAPI21(byte[] buffer) {
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
        getH264Data.onVideoFormat(mediaFormat);
        getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
            mediaFormat.getByteBuffer("csd-1"));
        spsPpsSetted = true;
      } else if (outBufferIndex >= 0) {
        //This ByteBuffer is H264
        ByteBuffer bb = videoEncoder.getOutputBuffer(outBufferIndex);
        if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          if (!spsPpsSetted) {
            Pair<ByteBuffer, ByteBuffer> buffers =
                decodeSpsPpsFromBuffer(bb.duplicate(), videoInfo.size);
            if (buffers != null) {
              getH264Data.onSPSandPPS(buffers.first, buffers.second);
              spsPpsSetted = true;
            }
          }
        }
        videoInfo.presentationTimeUs = System.nanoTime() / 1000 - mPresentTimeUs;
        getH264Data.getH264Data(bb, videoInfo);
        videoEncoder.releaseOutputBuffer(outBufferIndex, false);
      } else {
        break;
      }
    }
  }

  private void getDataFromEncoder(byte[] buffer) {
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
        getH264Data.onVideoFormat(mediaFormat);
        getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
            mediaFormat.getByteBuffer("csd-1"));
        spsPpsSetted = true;
      } else if (outBufferIndex >= 0) {
        //This ByteBuffer is H264
        ByteBuffer bb = outputBuffers[outBufferIndex];
        if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          if (!spsPpsSetted) {
            Pair<ByteBuffer, ByteBuffer> buffers =
                decodeSpsPpsFromBuffer(bb.duplicate(), videoInfo.size);
            if (buffers != null) {
              getH264Data.onSPSandPPS(buffers.first, buffers.second);
              spsPpsSetted = true;
            }
          }
        }
        videoInfo.presentationTimeUs = System.nanoTime() / 1000 - mPresentTimeUs;
        getH264Data.getH264Data(bb, videoInfo);
        videoEncoder.releaseOutputBuffer(outBufferIndex, false);
      } else {
        break;
      }
    }
  }

  /**
   * choose the video encoder by mime.
   */
  private MediaCodecInfo chooseVideoEncoder(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList;
    if (force == CodecUtil.Force.HARDWARE) {
      mediaCodecInfoList = CodecUtil.getAllHardwareEncoders(mime);
    } else if (force == CodecUtil.Force.SOFTWARE) {
      mediaCodecInfoList = CodecUtil.getAllSoftwareEncoders(mime);
    } else {
      mediaCodecInfoList = CodecUtil.getAllEncoders(mime);
    }
    for (MediaCodecInfo mci : mediaCodecInfoList) {
      Log.i(TAG, String.format("VideoEncoder %s", mci.getName()));
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
    return null;
  }

  private void prepareBlackImage() {
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(b);
    canvas.drawColor(Color.BLACK);
    int x = b.getWidth();
    int y = b.getHeight();
    int[] data = new int[x * y];
    b.getPixels(data, 0, x, 0, 0, x, y);
    blackImage = YUVUtil.ARGBtoYUV420SemiPlanar(data, width, height);
  }

  public void startSendBlackImage() {
    sendBlackImage = true;
    if (Build.VERSION.SDK_INT >= 19) {
      if (isRunning()) {
        Bundle bundle = new Bundle();
        bundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, 100 * 1024);
        try {
          videoEncoder.setParameters(bundle);
        } catch (IllegalStateException e) {
          Log.e(TAG, "encoder need be running");
          e.printStackTrace();
        }
      }
    }
  }

  public void stopSendBlackImage() {
    sendBlackImage = false;
    if (Build.VERSION.SDK_INT >= 19) {
      setVideoBitrateOnFly(bitRate);
    }
  }

  /**
   * decode sps and pps if the encoder never call to MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
   */
  private Pair<ByteBuffer, ByteBuffer> decodeSpsPpsFromBuffer(ByteBuffer outputBuffer, int length) {
    byte[] mSPS = null, mPPS = null;
    byte[] csd = new byte[length];
    outputBuffer.get(csd, 0, length);
    int i = 0;
    int spsIndex = -1;
    int ppsIndex = -1;
    while (i < length - 4) {
      if (csd[i] == 0 && csd[i + 1] == 0 && csd[i + 2] == 0 && csd[i + 3] == 1) {
        if (spsIndex == -1) {
          spsIndex = i;
        } else {
          ppsIndex = i;
          break;
        }
      }
      i++;
    }
    if (spsIndex != -1 && ppsIndex != -1) {
      mSPS = new byte[ppsIndex];
      System.arraycopy(csd, spsIndex, mSPS, 0, ppsIndex);
      mPPS = new byte[length - ppsIndex];
      System.arraycopy(csd, ppsIndex, mPPS, 0, length - ppsIndex);
    }
    if (mSPS != null && mPPS != null) {
      return new Pair<>(ByteBuffer.wrap(mSPS), ByteBuffer.wrap(mPPS));
    }
    return null;
  }
}