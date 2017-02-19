package com.pedro.encoder.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.YUVUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 * This class need use same resolution, fps and rotation that CameraManager
 */

public class VideoEncoder implements GetCameraData {

  private String TAG = "VideoEncoder";
  private MediaCodec videoEncoder;
  private GetH264Data getH264Data;
  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private long mPresentTimeUs;
  private boolean running;

  //default parameters for encoder
  private String codec = "video/avc";
  private int width = 1280;
  private int height = 720;
  private int fps = 30;
  private int bitRate = 3000 * 1000; //in kbps
  private int rotation = 90;
  private FormatVideoEncoder formatVideoEncoder = FormatVideoEncoder.YUV420SEMIPLANAR;

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
      } else {
        Log.e(TAG, "valid encoder not found");
        return false;
      }
    } catch (IOException e) {
      Log.e(TAG, "create videoEncoder failed.");
      e.printStackTrace();
      return false;
    }

    // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
    MediaFormat videoFormat = MediaFormat.createVideoFormat(codec, width, height);
    videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, formatVideoEncoder.getFormatCodec());
    videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
    videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
    videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
    videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
    videoFormat.setInteger("rotation-degrees", rotation);
    videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    running = false;
    return true;
  }

  /**
   * Prepare encoder with default parameters
   */
  public boolean prepareVideoEncoder() {
    return prepareVideoEncoder(width, height, fps, bitRate, rotation, formatVideoEncoder);
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
    running = true;
  }

  public void stop() {
    running = false;
    if (videoEncoder != null) {
      videoEncoder.stop();
      videoEncoder.release();
      videoEncoder = null;
    }
  }

  @Override
  public void inputYv12Data(byte[] buffer, int width, int height) {
    byte[] i420 = YUVUtil.YV12toYUV420SemiPlanar(buffer, width, height);
    if (Build.VERSION.SDK_INT >= 21) {
      getDataFromEncoderAPI21(i420);
    } else {
      getDataFromEncoder(i420);
    }
  }

  @Override
  public void inputNv21Data(byte[] buffer, int width, int height) {
    byte[] i420 = YUVUtil.NV21toYUV420SemiPlanar(buffer, width, height);
    if (Build.VERSION.SDK_INT >= 21) {
      getDataFromEncoderAPI21(i420);
    } else {
      getDataFromEncoder(i420);
    }
  }

  /**
   * call it to encoder YUV 420, 422, 444
   * remember create encoder with correct color format before
   */
  public void inputYuv4XX(byte[] buffer) {
    if (Build.VERSION.SDK_INT >= 21) {
      getDataFromEncoderAPI21(buffer);
    } else {
      getDataFromEncoder(buffer);
    }
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
      if (outBufferIndex >= 0) {
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
          Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), type));
          return mci;
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
          Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), type));
          return mci;
        }
      }
    }
    return null;
  }
}
