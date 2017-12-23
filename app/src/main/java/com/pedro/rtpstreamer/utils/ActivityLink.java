package com.pedro.rtpstreamer.utils;

import android.content.Intent;

public class ActivityLink {
  private final int minSdk;
  private final String label;
  private final Intent intent;

  public ActivityLink(Intent intent, String label, int minSdk) {
    this.intent = intent;
    this.label = label;
    this.minSdk = minSdk;
  }

  public String getLabel() {
    return label;
  }

  public Intent getIntent() {
    return intent;
  }

  public int getMinSdk() {
    return minSdk;
  }
}
