package com.pedro.encoder.utils;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by pedro on 14/02/18.
 */

public class CodecUtil {

  private static final String TAG = "CodecUtil";

  public static final String H264_MIME = "video/avc";
  public static final String H265_MIME = "video/hevc";
  public static final String AAC_MIME = "audio/mp4a-latm";
  public static final String VORBIS_MIME = "audio/ogg";
  public static final String OPUS_MIME = "audio/opus";

  public enum Force {
    FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
  }

  public static List<String> showAllCodecsInfo() {
    List<MediaCodecInfo> mediaCodecInfoList = getAllCodecs(false);
    List<String> infos = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      String info = "----------------\n";
      info += "Name: " + mediaCodecInfo.getName() + "\n";
      for (String type : mediaCodecInfo.getSupportedTypes()) {
        info += "Type: " + type + "\n";
        MediaCodecInfo.CodecCapabilities codecCapabilities =
            mediaCodecInfo.getCapabilitiesForType(type);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          info += "Max instances: " + codecCapabilities.getMaxSupportedInstances() + "\n";
        }
        if (mediaCodecInfo.isEncoder()) {
          info += "----- Encoder info -----\n";
          MediaCodecInfo.EncoderCapabilities encoderCapabilities = null;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            encoderCapabilities = codecCapabilities.getEncoderCapabilities();
            info += "Complexity range: "
                + encoderCapabilities.getComplexityRange().getLower()
                + " - "
                + encoderCapabilities.getComplexityRange().getUpper()
                + "\n";
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info += "Quality range: "
                + encoderCapabilities.getQualityRange().getLower()
                + " - "
                + encoderCapabilities.getQualityRange().getUpper()
                + "\n";
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info += "CBR supported: " + encoderCapabilities.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR) + "\n";
            info += "VBR supported: " + encoderCapabilities.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) + "\n";
            info += "CQ supported: " + encoderCapabilities.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ) + "\n";
          }
          info += "----- -----\n";
        } else {
          info += "----- Decoder info -----\n";
          info += "----- -----\n";
        }

        if (codecCapabilities.colorFormats != null && codecCapabilities.colorFormats.length > 0) {
          info += "----- Video info -----\n";
          info += "Supported colors: \n";
          for (int color : codecCapabilities.colorFormats) info += color + "\n";
          for (MediaCodecInfo.CodecProfileLevel profile : codecCapabilities.profileLevels)
            info += "Profile: " + profile.profile + ", level: " + profile.level + "\n";
          MediaCodecInfo.VideoCapabilities videoCapabilities = null;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            videoCapabilities = codecCapabilities.getVideoCapabilities();

            info += "Bitrate range: "
                + videoCapabilities.getBitrateRange().getLower()
                + " - "
                + videoCapabilities.getBitrateRange().getUpper()
                + "\n";
            info += "Frame rate range: "
                + videoCapabilities.getSupportedFrameRates().getLower()
                + " - "
                + videoCapabilities.getSupportedFrameRates().getUpper()
                + "\n";
            info += "Width range: "
                + videoCapabilities.getSupportedWidths().getLower()
                + " - "
                + videoCapabilities.getSupportedWidths().getUpper()
                + "\n";
            info += "Height range: "
                + videoCapabilities.getSupportedHeights().getLower()
                + " - "
                + videoCapabilities.getSupportedHeights().getUpper()
                + "\n";
          }
          info += "----- -----\n";
        } else {
          info += "----- Audio info -----\n";
          for (MediaCodecInfo.CodecProfileLevel profile : codecCapabilities.profileLevels)
            info += "Profile: " + profile.profile + ", level: " + profile.level + "\n";
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecInfo.AudioCapabilities audioCapabilities =
                codecCapabilities.getAudioCapabilities();

            info += "Bitrate range: "
                + audioCapabilities.getBitrateRange().getLower()
                + " - "
                + audioCapabilities.getBitrateRange().getUpper()
                + "\n";
            info += "Channels supported: " + audioCapabilities.getMaxInputChannelCount() + "\n";
            try {
              if (audioCapabilities.getSupportedSampleRates() != null
                  && audioCapabilities.getSupportedSampleRates().length > 0) {
                info += "Supported sample rate: \n";
                for (int sr : audioCapabilities.getSupportedSampleRates()) info += sr + "\n";
              }
            } catch (Exception e) {
            }
          }
          info += "----- -----\n";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          info += "Max instances: " + codecCapabilities.getMaxSupportedInstances() + "\n";
        }
      }
      info += "----------------\n";
      infos.add(info);
    }
    return infos;
  }

  public static List<MediaCodecInfo> getAllCodecs(boolean filterBroken) {
    List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= 21) {
      MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
      MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
      mediaCodecInfoList.addAll(Arrays.asList(mediaCodecInfos));
    } else {
      int count = MediaCodecList.getCodecCount();
      for (int i = 0; i < count; i++) {
        MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
        mediaCodecInfoList.add(mci);
      }
    }
    return filterBroken ? filterBrokenCodecs(mediaCodecInfoList) : mediaCodecInfoList;
  }

  public static List<MediaCodecInfo> getAllHardwareEncoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllEncoders(mime);
    List<MediaCodecInfo> mediaCodecInfoHardware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      if (isHardwareAccelerated(mediaCodecInfo)) {
        mediaCodecInfoHardware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoHardware;
  }

  public static List<MediaCodecInfo> getAllHardwareDecoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllDecoders(mime);
    List<MediaCodecInfo> mediaCodecInfoHardware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      if (isHardwareAccelerated(mediaCodecInfo)) {
        mediaCodecInfoHardware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoHardware;
  }

  public static List<MediaCodecInfo> getAllSoftwareEncoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllEncoders(mime);
    List<MediaCodecInfo> mediaCodecInfoSoftware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      if (isSoftwareOnly(mediaCodecInfo)) {
        mediaCodecInfoSoftware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoSoftware;
  }

  public static List<MediaCodecInfo> getAllSoftwareDecoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllDecoders(mime);
    List<MediaCodecInfo> mediaCodecInfoSoftware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      if (isSoftwareOnly(mediaCodecInfo)) {
        mediaCodecInfoSoftware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoSoftware;
  }

  /**
   * choose the video encoder by mime.
   */
  public static List<MediaCodecInfo> getAllEncoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
    List<MediaCodecInfo> mediaCodecInfos = getAllCodecs(true);
    for (MediaCodecInfo mci : mediaCodecInfos) {
      if (!mci.isEncoder()) {
        continue;
      }
      String[] types = mci.getSupportedTypes();
      for (String type : types) {
        if (type.equalsIgnoreCase(mime)) {
          mediaCodecInfoList.add(mci);
        }
      }
    }
    return mediaCodecInfoList;
  }

  /**
   * choose the video encoder by mime.
   */
  public static List<MediaCodecInfo> getAllDecoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
    List<MediaCodecInfo> mediaCodecInfos = getAllCodecs(true);
    for (MediaCodecInfo mci : mediaCodecInfos) {
      if (mci.isEncoder()) {
        continue;
      }
      String[] types = mci.getSupportedTypes();
      for (String type : types) {
        if (type.equalsIgnoreCase(mime)) {
          mediaCodecInfoList.add(mci);
        }
      }
    }
    return mediaCodecInfoList;
  }

  /* Adapted from google/ExoPlayer
   * https://github.com/google/ExoPlayer/commit/48555550d7fcf6953f2382466818c74092b26355
   */
  private static boolean isHardwareAccelerated(MediaCodecInfo codecInfo) {
    if (Build.VERSION.SDK_INT >= 29) {
      return codecInfo.isHardwareAccelerated();
    }
    // codecInfo.isHardwareAccelerated() != codecInfo.isSoftwareOnly() is not necessarily true.
    // However, we assume this to be true as an approximation.
    return !isSoftwareOnly(codecInfo);
  }

  /* Adapted from google/ExoPlayer
   * https://github.com/google/ExoPlayer/commit/48555550d7fcf6953f2382466818c74092b26355
   */
  private static boolean isSoftwareOnly(MediaCodecInfo mediaCodecInfo) {
    if (Build.VERSION.SDK_INT >= 29) {
      return mediaCodecInfo.isSoftwareOnly();
    }
    String name = mediaCodecInfo.getName().toLowerCase();
    if (name.startsWith("arc.")) { // App Runtime for Chrome (ARC) codecs
      return false;
    }
    return name.startsWith("omx.google.")
        || name.startsWith("omx.ffmpeg.")
        || (name.startsWith("omx.sec.") && name.contains(".sw."))
        || name.equals("omx.qcom.video.decoder.hevcswvdec")
        || name.startsWith("c2.android.")
        || name.startsWith("c2.google.")
        || (!name.startsWith("omx.") && !name.startsWith("c2."));
  }

  /**
   * Filter broken codecs by name and device model.
   *
   * Note:
   * There is no way to know broken encoders so we will check by name and device.
   * Please add your encoder to this method if you detect one.
   *
   * @param codecs All device codecs
   * @return a list without broken codecs
   */
  private static List<MediaCodecInfo> filterBrokenCodecs(List<MediaCodecInfo> codecs) {
    List<MediaCodecInfo> listFilter = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : codecs) {
      if (isValid(mediaCodecInfo.getName())) {
        listFilter.add(mediaCodecInfo);
      }
    }
    return listFilter;
  }

  /**
   * For now, none broken codec reported.
   */
  private static boolean isValid(String name) {
    //This encoder is invalid and produce errors (Only found in AVD API 16)
    if (name.equalsIgnoreCase("aacencoder")) return false;
    return true;
  }
}
