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
package com.pedro.encoder.input.gl.render.filters.`object`

import android.graphics.Color
import android.graphics.Typeface
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.utils.gl.TextStreamObject

/**
 * Created by pedro on 27/07/18.
 */

@Deprecated("Use TextFilterRender instead", ReplaceWith("TextFilterRender"))
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
open class TextObjectFilterRender : TextFilterRender()

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
open class TextFilterRender : BaseObjectFilterRender() {
  private var text: String = ""
  private var textSize = 0f
  private var textColor = 0
  private var backgroundColor = 0
  private var typeface: Typeface? = null

  init {
    streamObject = TextStreamObject()
  }

  override fun drawFilter() {
    super.drawFilter()
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, streamObjectTextureId[0])
    //Set alpha. 0f if no image loaded.
    GLES20.glUniform1f(uAlphaHandle, if (streamObjectTextureId[0] == -1) 0f else alpha)
  }

  @JvmOverloads
  fun setText(
    text: String, textSize: Float, textColor: Int,
    backgroundColor: Int = Color.TRANSPARENT, typeface: Typeface? = null
  ) {
    this.text = text
    this.textSize = textSize
    this.textColor = textColor
    this.backgroundColor = backgroundColor
    this.typeface = typeface
    (streamObject as TextStreamObject).load(text, textSize, textColor, backgroundColor, typeface)
    shouldLoad = true
  }

  fun setText(text: String, textSize: Float, textColor: Int, typeface: Typeface?) {
    setText(text, textSize, textColor, Color.TRANSPARENT, typeface)
  }

  fun addText(text: String) {
    setText(this.text + text, textSize, textColor, backgroundColor, typeface)
  }

  fun updateColor(textColor: Int) {
    setText(text, textSize, textColor, backgroundColor, typeface)
  }

  fun updateTypeface(typeface: Typeface?) {
    setText(text, textSize, textColor, backgroundColor, typeface)
  }

  fun updateTextSize(textSize: Float) {
    setText(text, textSize, textColor, backgroundColor, typeface)
  }
}
