/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.encoder.input.video;

import com.pedro.common.TimeUtils;

/**
 * Created by pedro on 11/10/18.
 */

public class FpsLimiter {

  private long startTS = TimeUtils.getCurrentTimeMillis();
  private long ratioF = 1000 / 30;
  private long ratio = 1000 / 30;
  private long frameStartTS = 0;
  private boolean configured = false;

  public void setFPS(int fps) {
    if (fps <= 0) {
      configured = false;
      return;
    } else {
      configured = true;
    }
    startTS = TimeUtils.getCurrentTimeMillis();
    ratioF = 1000 / fps;
    ratio = 1000 / fps;
  }

  public boolean limitFPS() {
    if (!configured) return false;
    long lastFrameTimestamp = TimeUtils.getCurrentTimeMillis() - startTS;
    if (ratio < lastFrameTimestamp) {
      ratio += ratioF;
      return false;
    }
    return true;
  }

  public void setFrameStartTs() {
    frameStartTS = TimeUtils.getCurrentTimeMillis();
  }

  public long getSleepTime() {
    return Math.max(0, ratioF - (TimeUtils.getCurrentTimeMillis() - frameStartTS));
  }
}
