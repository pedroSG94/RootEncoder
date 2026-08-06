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

  private long interval = 1_000_000_000L / 30;
  private long time = 0;
  private boolean configured = false;

  public void setFPS(int fps) {
    if (fps <= 0) {
      configured = false;
      return;
    } else {
      configured = true;
    }
    interval = 1_000_000_000L / fps;
  }

  public boolean limitFPS(long timestamp) {
    if (!configured) return false;
    if (time == 0) {
      time = timestamp;
      return false;
    }
    if (timestamp - time < interval - (interval / 4)) return true;
    time += interval;
    if (timestamp - time > interval) time = timestamp;
    return false;
  }
}
