package com.pedro.encoder.utils;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import androidx.annotation.RequiresApi;
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

  public enum Force {
    FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  public static List<String> showAllCodecsInfo() {
    List<MediaCodecInfo> mediaCodecInfoList = getAllCodecs();
    List<String> infos = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      String info = "----------------\n";
      info += "Name: " + mediaCodecInfo.getName() + "\n";
      for (String type : mediaCodecInfo.getSupportedTypes()) {
        info += "Type: " + type + "\n";
        MediaCodecInfo.CodecCapabilities codecCapabilities =
            mediaCodecInfo.getCapabilitiesForType(type);
        info += "Max instances: " + codecCapabilities.getMaxSupportedInstances() + "\n";
        if (mediaCodecInfo.isEncoder()) {
          info += "----- Encoder info -----\n";
          MediaCodecInfo.EncoderCapabilities encoderCapabilities =
              codecCapabilities.getEncoderCapabilities();
          info += "Complexity range: "
              + encoderCapabilities.getComplexityRange().getLower()
              + " - "
              + encoderCapabilities.getComplexityRange().getUpper()
              + "\n";
          info += "Quality range: "
              + encoderCapabilities.getQualityRange().getLower()
              + " - "
              + encoderCapabilities.getQualityRange().getUpper()
              + "\n";
          info += "CBR supported: " + encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR) + "\n";
          info += "VBR supported: " + encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) + "\n";
          info += "CQ supported: " + encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ) + "\n";
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
          MediaCodecInfo.VideoCapabilities videoCapabilities =
              codecCapabilities.getVideoCapabilities();

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
          info += "----- -----\n";
        } else {
          info += "----- Audio info -----\n";
          for (MediaCodecInfo.CodecProfileLevel profile : codecCapabilities.profileLevels)
            info += "Profile: " + profile.profile + ", level: " + profile.level + "\n";
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
          info += "----- -----\n";
        }
        info += "Max instances: " + codecCapabilities.getMaxSupportedInstances() + "\n";
      }
      info += "----------------\n";
      infos.add(info);
    }
    return infos;
  }

  public static List<MediaCodecInfo> getAllCodecs() {
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
    return mediaCodecInfoList;
  }

  public static List<MediaCodecInfo> getAllHardwareEncoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllEncoders(mime);
    List<MediaCodecInfo> mediaCodecInfoHardware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      String name = mediaCodecInfo.getName().toLowerCase();
      if (!name.contains("omx.google") && !name.contains("sw")) {
        mediaCodecInfoHardware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoHardware;
  }

  public static List<MediaCodecInfo> getAllHardwareDecoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllDecoders(mime);
    List<MediaCodecInfo> mediaCodecInfoHardware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      String name = mediaCodecInfo.getName().toLowerCase();
      if (!name.contains("omx.google") && !name.contains("sw")) {
        mediaCodecInfoHardware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoHardware;
  }

  public static List<MediaCodecInfo> getAllSoftwareEncoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllEncoders(mime);
    List<MediaCodecInfo> mediaCodecInfoSoftware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      String name = mediaCodecInfo.getName().toLowerCase();
      if (name.contains("omx.google") || name.contains("sw")) {
        mediaCodecInfoSoftware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoSoftware;
  }

  public static List<MediaCodecInfo> getAllSoftwareDecoders(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = getAllDecoders(mime);
    List<MediaCodecInfo> mediaCodecInfoSoftware = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      String name = mediaCodecInfo.getName().toLowerCase();
      if (name.contains("omx.google") || name.contains("sw")) {
        mediaCodecInfoSoftware.add(mediaCodecInfo);
      }
    }
    return mediaCodecInfoSoftware;
  }

  public static List<MediaCodecInfo> getAllEncoders(String mime) {
    if (Build.VERSION.SDK_INT >= 21) {
      return getAllEncodersAPI21(mime);
    } else {
      return getAllEncodersAPI16(mime);
    }
  }

  public static List<MediaCodecInfo> getAllDecoders(String mime) {
    if (Build.VERSION.SDK_INT >= 21) {
      return getAllDecodersAPI21(mime);
    } else {
      return getAllDecodersAPI16(mime);
    }
  }

  /**
   * choose the video encoder by mime. API 21+
   */
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private static List<MediaCodecInfo> getAllEncodersAPI21(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
    MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
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
   * choose the video encoder by mime. API > 16
   */
  private static List<MediaCodecInfo> getAllEncodersAPI16(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
    int count = MediaCodecList.getCodecCount();
    for (int i = 0; i < count; i++) {
      MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
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
   * choose the video encoder by mime. API 21+
   */
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private static List<MediaCodecInfo> getAllDecodersAPI21(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
    MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
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

  /**
   * choose the video encoder by mime. API > 16
   */
  private static List<MediaCodecInfo> getAllDecodersAPI16(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList = new ArrayList<>();
    int count = MediaCodecList.getCodecCount();
    for (int i = 0; i < count; i++) {
      MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
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
}
