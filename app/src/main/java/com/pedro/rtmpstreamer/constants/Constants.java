package com.pedro.rtmpstreamer.constants;

import android.support.annotation.IntDef;
import android.support.annotation.StringDef;

public class Constants {
  public static final int RTMP = 1;
  public static final int RTSP = 2;

  @IntDef({ RTMP, RTSP })
  public @interface Protocols {
  }

  public static final String LABEL = "label";

  @StringDef({ LABEL })
  public @interface IntentExtras {
  }
}
