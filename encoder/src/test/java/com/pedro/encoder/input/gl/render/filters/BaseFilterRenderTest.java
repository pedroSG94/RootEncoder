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

package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public class BaseFilterRenderTest {

  @Test
  public void initGlForwardsContextToFilter() {
    Context expectedContext = new ContextWrapper(null);
    TestFilterRender filterRender = new TestFilterRender();

    filterRender.initGl(1, 1, expectedContext, 1, 1);

    assertSame(expectedContext, filterRender.receivedContext);
  }

  private static final class TestFilterRender extends BaseFilterRender {

    private Context receivedContext;

    @Override
    protected void initGlFilter(@NonNull Context context) {
      receivedContext = context;
    }

    @Override
    protected void drawFilter() {
    }

    @Override
    protected void disableResources() {
    }

    @Override
    public void release() {
    }
  }
}