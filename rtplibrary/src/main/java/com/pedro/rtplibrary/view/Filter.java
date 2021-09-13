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

package com.pedro.rtplibrary.view;

import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;

/**
 * Created by pedro on 30/07/18.
 */

public class Filter {

  private int position;
  private BaseFilterRender baseFilterRender;

  public Filter() {
  }

  public Filter(int position, BaseFilterRender baseFilterRender) {
    this.position = position;
    this.baseFilterRender = baseFilterRender;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public BaseFilterRender getBaseFilterRender() {
    return baseFilterRender;
  }

  public void setBaseFilterRender(BaseFilterRender baseFilterRender) {
    this.baseFilterRender = baseFilterRender;
  }
}
