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

package com.pedro.encoder.input.gl.render;

/**
 * Created by pedro on 25/7/18.
 */

public class RenderHandler {

  private int[] fboId = new int[] { 0 };
  private int[] rboId = new int[] { 0 };
  private int[] texId = new int[] { 0 };

  public int[] getTexId() {
    return texId;
  }

  public int[] getFboId() {
    return fboId;
  }

  public int[] getRboId() {
    return rboId;
  }

  public void setFboId(int[] fboId) {
    this.fboId = fboId;
  }

  public void setRboId(int[] rboId) {
    this.rboId = rboId;
  }

  public void setTexId(int[] texId) {
    this.texId = texId;
  }
}
