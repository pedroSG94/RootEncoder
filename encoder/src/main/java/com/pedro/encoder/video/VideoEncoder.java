package com.pedro.encoder.video;

import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.BaseEncoder;
import com.pedro.encoder.Frame;
import com.pedro.encoder.input.video.FpsLimiter;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.utils.yuv.YUVUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by pedro on 19/01/17.
 * This class need use same resolution, fps and imageFormat that Camera1ApiManagerGl
 */

public class VideoEncoder extends BaseEncoder implements GetCameraData {

  private static final String TAG = "VideoEncoder";
  private GetVideoData getVideoData;
  private boolean spsPpsSetted = false;
  private boolean hardwareRotation = false;

  //surface to buffer encoder
  private Surface inputSurface;

  private int width = 640;
  private int height = 480;
  private int fps = 30;
  private int bitRate = 1200 * 1024; //in kbps
  private int rotation = 90;
  private int iFrameInterval = 2;
  //for disable video
  private FpsLimiter fpsLimiter = new FpsLimiter();
  private String type = CodecUtil.H264_MIME;
  private FormatVideoEncoder formatVideoEncoder = FormatVideoEncoder.YUV420Dynamical;
  private HandlerThread handlerThread;
  private BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(80);

  public VideoEncoder(GetVideoData getVideoData) {
    this.getVideoData = getVideoData;
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
    isBufferMode = true;
    MediaCodecInfo encoder = chooseEncoder(type);
    try {
      if (encoder != null) {
        codec = MediaCodec.createByCodecName(encoder.getName());
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
        videoFormat = MediaFormat.createVideoFormat(type, height, width);
      } else {
        resolution = width + "x" + height;
        videoFormat = MediaFormat.createVideoFormat(type, width, height);
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
      codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      running = false;
      if (formatVideoEncoder == FormatVideoEncoder.SURFACE
          && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        isBufferMode = false;
        inputSurface = codec.createInputSurface();
      }
      Log.i(TAG, "prepared");
      return true;
    } catch (IOException | IllegalStateException e) {
      Log.e(TAG, "Create VideoEncoder failed.", e);
      return false;
    }
  }

  @Override
  public void start(boolean resetTs) {
    spsPpsSetted = false;
    if (resetTs) {
      presentTimeUs = System.nanoTime() / 1000;
      fpsLimiter.setFPS(fps);
    }
    if (formatVideoEncoder != FormatVideoEncoder.SURFACE) {
      YUVUtil.preAllocateBuffers(width * height * 3 / 2);
    }
    handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      codec.setCallback(callback, handler);
      codec.start();
    } else {
      codec.start();
      handler.post(new Runnable() {
        @Override
        public void run() {
          while (running) {
            try {
              getDataFromEncoder(null);
            } catch (IllegalStateException e) {
              Log.i(TAG, "Encoding error", e);
            }
          }
        }
      });
    }
    running = true;
    Log.i(TAG, "started");
  }

  @Override
  protected void stopImp() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      handlerThread.quitSafely();
    } else {
      handlerThread.quit();
    }
    queue.clear();
    spsPpsSetted = false;
    inputSurface = null;
    Log.i(TAG, "stopped");
  }

  public void reset() {
    stop();
    prepareVideoEncoder(width, height, fps, bitRate, rotation, hardwareRotation, iFrameInterval,
        formatVideoEncoder);
    start(false);
  }

  private FormatVideoEncoder chooseColorDynamically(MediaCodecInfo mediaCodecInfo) {
    for (int color : mediaCodecInfo.getCapabilitiesForType(CodecUtil.H264_MIME).colorFormats) {
      if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()) {
        return FormatVideoEncoder.YUV420PLANAR;
      } else if (color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()) {
        return FormatVideoEncoder.YUV420SEMIPLANAR;
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
        codec.setParameters(bundle);
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
        codec.setParameters(bundle);
      } catch (IllegalStateException e) {
        Log.e(TAG, "encoder need be running", e);
      }
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

  public boolean isHardwareRotation() {
    return hardwareRotation;
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

  public int getBitRate() {
    return bitRate;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @Override
  public void inputYUVData(Frame frame) {
    if (running && !queue.offer(frame)) {
      Log.i(TAG, "frame discarded");
    }
  }

  private void sendSPSandPPS(MediaFormat mediaFormat) {
    //H265
    if (type.equals(CodecUtil.H265_MIME)) {
      List<ByteBuffer> byteBufferList =
          extractVpsSpsPpsFromH265(mediaFormat.getByteBuffer("csd-0"));
      getVideoData.onSpsPpsVps(byteBufferList.get(1), byteBufferList.get(2), byteBufferList.get(0));
      //H264
    } else {
      getVideoData.onSpsPps(mediaFormat.getByteBuffer("csd-0"), mediaFormat.getByteBuffer("csd-1"));
    }
  }

  /**
   * choose the video encoder by mime.
   */
  @Override
  protected MediaCodecInfo chooseEncoder(String mime) {
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
        if (formatVideoEncoder == FormatVideoEncoder.SURFACE) {
          if (color == FormatVideoEncoder.SURFACE.getFormatCodec()) return mci;
        } else {
          //check if encoder support any yuv420 color
          if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()
              || color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()) {
            return mci;
          }
        }
      }
    }
    return null;
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

  /**
   * You need find 0 0 0 1 byte sequence that is the initiation of vps, sps and pps
   * buffers.
   *
   * @param csd0byteBuffer get in mediacodec case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
   * @return list with vps, sps and pps
   */
  private List<ByteBuffer> extractVpsSpsPpsFromH265(ByteBuffer csd0byteBuffer) {
    List<ByteBuffer> byteBufferList = new ArrayList<>();
    int vpsPosition = -1;
    int spsPosition = -1;
    int ppsPosition = -1;
    int contBufferInitiation = 0;
    byte[] csdArray = csd0byteBuffer.array();
    for (int i = 0; i < csdArray.length; i++) {
      if (contBufferInitiation == 3 && csdArray[i] == 1) {
        if (vpsPosition == -1) {
          vpsPosition = i - 3;
        } else if (spsPosition == -1) {
          spsPosition = i - 3;
        } else {
          ppsPosition = i - 3;
        }
      }
      if (csdArray[i] == 0) {
        contBufferInitiation++;
      } else {
        contBufferInitiation = 0;
      }
    }
    byte[] vps = new byte[spsPosition];
    byte[] sps = new byte[ppsPosition - spsPosition];
    byte[] pps = new byte[csdArray.length - ppsPosition];
    for (int i = 0; i < csdArray.length; i++) {
      if (i < spsPosition) {
        vps[i] = csdArray[i];
      } else if (i < ppsPosition) {
        sps[i - spsPosition] = csdArray[i];
      } else {
        pps[i - ppsPosition] = csdArray[i];
      }
    }
    byteBufferList.add(ByteBuffer.wrap(vps));
    byteBufferList.add(ByteBuffer.wrap(sps));
    byteBufferList.add(ByteBuffer.wrap(pps));
    return byteBufferList;
  }

  @Override
  protected Frame getInputFrame() throws InterruptedException {
    Frame frame = queue.take();
    if (fpsLimiter.limitFPS()) return getInputFrame();
    byte[] buffer = frame.getBuffer();
    boolean isYV12 = frame.getFormat() == ImageFormat.YV12;
    if (!hardwareRotation) {
      int orientation = frame.isFlip() ? frame.getOrientation() + 180 : frame.getOrientation();
      if (orientation >= 360) orientation -= 360;
      buffer = isYV12 ? YUVUtil.rotateYV12(buffer, width, height, orientation)
          : YUVUtil.rotateNV21(buffer, width, height, orientation);
    }
    buffer = isYV12 ? YUVUtil.YV12toYUV420byColor(buffer, width, height, formatVideoEncoder)
        : YUVUtil.NV21toYUV420byColor(buffer, width, height, formatVideoEncoder);
    frame.setBuffer(buffer);
    return frame;
  }

  @Override
  public void formatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
    getVideoData.onVideoFormat(mediaFormat);
    sendSPSandPPS(mediaFormat);
    spsPpsSetted = true;
  }

  @Override
  protected void checkBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      if (!spsPpsSetted) {
        Pair<ByteBuffer, ByteBuffer> buffers =
            decodeSpsPpsFromBuffer(byteBuffer.duplicate(), bufferInfo.size);
        if (buffers != null) {
          getVideoData.onSpsPps(buffers.first, buffers.second);
          spsPpsSetted = true;
        }
      }
    }
  }

  @Override
  protected void sendBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    bufferInfo.presentationTimeUs = System.nanoTime() / 1000 - presentTimeUs;
    getVideoData.getVideoData(byteBuffer, bufferInfo);
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  private MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inBufferIndex) {
          try {
            inputAvailable(mediaCodec, inBufferIndex, null);
          } catch (IllegalStateException e) {
            Log.i(TAG, "Encoding error", e);
          }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outBufferIndex,
            @NonNull MediaCodec.BufferInfo bufferInfo) {
          try {
            outputAvailable(mediaCodec, outBufferIndex, bufferInfo);
          } catch (IllegalStateException e) {
            Log.i(TAG, "Encoding error", e);
          }
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
          Log.e(TAG, "Error", e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec,
            @NonNull MediaFormat mediaFormat) {
          formatChanged(mediaCodec, mediaFormat);
        }
      };
}