/*
 * Copyright (C) 2021 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.encoder.video;

import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pedro on 19/01/17.
 * This class need use same resolution, fps and imageFormat that Camera1ApiManagerGl
 */

public class VideoEncoder extends BaseEncoder implements GetCameraData {

  private final GetVideoData getVideoData;
  private boolean spsPpsSetted = false;
  private boolean forceKey = false;
  //video data necessary to send after requestKeyframe.
  private ByteBuffer oldSps, oldPps, oldVps;

  //surface to buffer encoder
  private Surface inputSurface;

  private int width = 640;
  private int height = 480;
  private int fps = 30;
  private int bitRate = 1200 * 1024; //in kbps
  private int rotation = 90;
  private int iFrameInterval = 2;
  //for disable video
  private final FpsLimiter fpsLimiter = new FpsLimiter();
  private String type = CodecUtil.H264_MIME;
  private FormatVideoEncoder formatVideoEncoder = FormatVideoEncoder.YUV420Dynamical;
  private int avcProfile = -1;
  private int avcProfileLevel = -1;

  public VideoEncoder(GetVideoData getVideoData) {
    this.getVideoData = getVideoData;
    TAG = "VideoEncoder";
  }

  public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, int rotation,
      int iFrameInterval, FormatVideoEncoder formatVideoEncoder) {
    return prepareVideoEncoder(width, height, fps, bitRate, rotation, iFrameInterval,
        formatVideoEncoder, -1, -1);
  }

  /**
   * Prepare encoder with custom parameters
   */
  public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, int rotation,
      int iFrameInterval, FormatVideoEncoder formatVideoEncoder, int avcProfile,
      int avcProfileLevel) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.bitRate = bitRate;
    this.rotation = rotation;
    this.iFrameInterval = iFrameInterval;
    this.formatVideoEncoder = formatVideoEncoder;
    this.avcProfile = avcProfile;
    this.avcProfileLevel = avcProfileLevel;
    isBufferMode = true;
    MediaCodecInfo encoder = chooseEncoder(type);
    try {
      if (encoder != null) {
        Log.i(TAG, "Encoder selected " + encoder.getName());
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
      if ((rotation == 90 || rotation == 270)) {
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
      //Set CBR mode if supported by encoder.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && CodecUtil.isCBRModeSupported(encoder, type)) {
        Log.i(TAG, "set bitrate mode CBR");
        videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
      } else {
        Log.i(TAG, "bitrate mode CBR not supported using default mode");
      }
      // Rotation by encoder.
      // Removed because this is ignored by most encoders, producing different results on different devices
      //  videoFormat.setInteger(MediaFormat.KEY_ROTATION, rotation);

      if (this.avcProfile > 0) {
        // MediaFormat.KEY_PROFILE, API > 21
        videoFormat.setInteger("profile", this.avcProfile);
      }
      if (this.avcProfileLevel > 0) {
        // MediaFormat.KEY_LEVEL, API > 23
        videoFormat.setInteger("level", this.avcProfileLevel);
      }
      setCallback();
      codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      running = false;
      if (formatVideoEncoder == FormatVideoEncoder.SURFACE
          && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        isBufferMode = false;
        inputSurface = codec.createInputSurface();
      }
      Log.i(TAG, "prepared");
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Create VideoEncoder failed.", e);
      this.stop();
      return false;
    }
  }

  @Override
  public void start(boolean resetTs) {
    forceKey = false;
    shouldReset = resetTs;
    spsPpsSetted = false;
    if (resetTs) {
      fpsLimiter.setFPS(fps);
    }
    if (formatVideoEncoder != FormatVideoEncoder.SURFACE) {
      YUVUtil.preAllocateBuffers(width * height * 3 / 2);
    }
    Log.i(TAG, "started");
  }

  @Override
  protected void stopImp() {
    spsPpsSetted = false;
    if (inputSurface != null) inputSurface.release();
    inputSurface = null;
    oldSps = null;
    oldPps = null;
    oldVps = null;
    Log.i(TAG, "stopped");
  }

  @Override
  public void reset() {
    stop(false);
    prepareVideoEncoder(width, height, fps, bitRate, rotation, iFrameInterval, formatVideoEncoder,
        avcProfile, avcProfileLevel);
    restart();
  }

  private FormatVideoEncoder chooseColorDynamically(MediaCodecInfo mediaCodecInfo) {
    for (int color : mediaCodecInfo.getCapabilitiesForType(type).colorFormats) {
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
    return prepareVideoEncoder(width, height, fps, bitRate, rotation, iFrameInterval,
        formatVideoEncoder, avcProfile, avcProfileLevel);
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
  public void requestKeyframe() {
    if (isRunning()) {
      if (spsPpsSetted) {
        Bundle bundle = new Bundle();
        bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        try {
          codec.setParameters(bundle);
          getVideoData.onSpsPpsVps(oldSps, oldPps, oldVps);
        } catch (IllegalStateException e) {
          Log.e(TAG, "encoder need be running", e);
        }
      } else {
        //You need wait until encoder generate first frame.
        forceKey = true;
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

  public int getRotation() {
    return rotation;
  }

  public void setFps(int fps) {
    this.fps = fps;
  }

  public void setRotation(int rotation) {
    this.rotation = rotation;
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
      List<ByteBuffer> byteBufferList = extractVpsSpsPpsFromH265(mediaFormat.getByteBuffer("csd-0"));
      oldSps = byteBufferList.get(1);
      oldPps = byteBufferList.get(2);
      oldVps = byteBufferList.get(0);
      getVideoData.onSpsPpsVps(oldSps, oldPps, oldVps);
      //H264
    } else {
      oldSps = mediaFormat.getByteBuffer("csd-0");
      oldPps = mediaFormat.getByteBuffer("csd-1");
      oldVps = null;
      getVideoData.onSpsPpsVps(oldSps, oldPps, oldVps);
    }
  }

  /**
   * choose the video encoder by mime.
   */
  @Override
  protected MediaCodecInfo chooseEncoder(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList;
    if (force == CodecUtil.Force.HARDWARE) {
      mediaCodecInfoList = CodecUtil.getAllHardwareEncoders(mime, true);
    } else if (force == CodecUtil.Force.SOFTWARE) {
      mediaCodecInfoList = CodecUtil.getAllSoftwareEncoders(mime, true);
    } else {
      //Priority: hardware CBR > hardware > software CBR > software
      mediaCodecInfoList = CodecUtil.getAllEncoders(mime, true, true);
    }

    Log.i(TAG, mediaCodecInfoList.size() + " encoders found");
    for (MediaCodecInfo mci : mediaCodecInfoList) {
      Log.i(TAG, "Encoder " + mci.getName());
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
    byte[] csd = new byte[length];
    outputBuffer.get(csd, 0, length);
    outputBuffer.rewind();
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
      byte[] sps = new byte[ppsIndex];
      System.arraycopy(csd, spsIndex, sps, 0, ppsIndex);
      byte[] pps = new byte[length - ppsIndex];
      System.arraycopy(csd, ppsIndex, pps, 0, length - ppsIndex);
      return new Pair<>(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps));
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
    int length = csd0byteBuffer.remaining();
    byte[] csdArray = new byte[length];
    csd0byteBuffer.get(csdArray, 0, length);
    csd0byteBuffer.rewind();
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
    if (frame == null) return null;
    if (fpsLimiter.limitFPS()) return getInputFrame();
    byte[] buffer = frame.getBuffer();
    boolean isYV12 = frame.getFormat() == ImageFormat.YV12;

    int orientation = frame.isFlip() ? frame.getOrientation() + 180 : frame.getOrientation();
    if (orientation >= 360) orientation -= 360;
    buffer = isYV12 ? YUVUtil.rotateYV12(buffer, width, height, orientation)
        : YUVUtil.rotateNV21(buffer, width, height, orientation);

    buffer = isYV12 ? YUVUtil.YV12toYUV420byColor(buffer, width, height, formatVideoEncoder)
        : YUVUtil.NV21toYUV420byColor(buffer, width, height, formatVideoEncoder);
    frame.setBuffer(buffer);
    return frame;
  }

  @Override
  protected long calculatePts(Frame frame, long presentTimeUs) {
    return System.nanoTime() / 1000 - presentTimeUs;
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
    if (forceKey && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      forceKey = false;
      requestKeyframe();
    }
    fixTimeStamp(bufferInfo);
    if (!spsPpsSetted && type.equals(CodecUtil.H264_MIME)) {
      Log.i(TAG, "formatChanged not called, doing manual sps/pps extraction...");
      Pair<ByteBuffer, ByteBuffer> buffers = decodeSpsPpsFromBuffer(byteBuffer.duplicate(), bufferInfo.size);
      if (buffers != null) {
        Log.i(TAG, "manual sps/pps extraction success");
        oldSps = buffers.first;
        oldPps = buffers.second;
        oldVps = null;
        getVideoData.onSpsPpsVps(oldSps, oldPps, oldVps);
        spsPpsSetted = true;
      } else {
        Log.e(TAG, "manual sps/pps extraction failed");
      }
    } else if (!spsPpsSetted && type.equals(CodecUtil.H265_MIME)) {
      Log.i(TAG, "formatChanged not called, doing manual vps/sps/pps extraction...");
      List<ByteBuffer> byteBufferList = extractVpsSpsPpsFromH265(byteBuffer);
      if (byteBufferList.size() == 3) {
        Log.i(TAG, "manual vps/sps/pps extraction success");
        oldSps = byteBufferList.get(1);
        oldPps = byteBufferList.get(2);
        oldVps = byteBufferList.get(0);
        getVideoData.onSpsPpsVps(oldSps, oldPps, oldVps);
        spsPpsSetted = true;
      } else {
        Log.e(TAG, "manual vps/sps/pps extraction failed");
      }
    }
    if (formatVideoEncoder == FormatVideoEncoder.SURFACE) {
      bufferInfo.presentationTimeUs = System.nanoTime() / 1000 - presentTimeUs;
    }
  }

  @Override
  protected void sendBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    getVideoData.getVideoData(byteBuffer, bufferInfo);
  }
}
