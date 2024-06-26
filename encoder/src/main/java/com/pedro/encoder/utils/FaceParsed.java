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

package com.pedro.encoder.utils;

import android.graphics.PointF;

/**
 * Created by pedro on 18/04/21.
 */
public class FaceParsed {
  private PointF position;
  private PointF scale;

  public FaceParsed(PointF position, PointF scale) {
    this.position = position;
    this.scale = scale;
  }

  public PointF getPosition() {
    return position;
  }

  public void setPosition(PointF position) {
    this.position = position;
  }

  public PointF getScale() {
    return scale;
  }

  public void setScale(PointF scale) {
    this.scale = scale;
  }

  @Override
  public String toString() {
    return "FaceParsed{" +
        "position=" + position +
        ", scale=" + scale +
        '}';
  }
}