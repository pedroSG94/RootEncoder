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

package com.pedro.streamer.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Build
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.Surface
import android.view.View
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.SpriteGestureController
import com.pedro.encoder.input.gl.render.filters.AnalogTVFilterRender
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender
import com.pedro.encoder.input.gl.render.filters.BasicDeformationFilterRender
import com.pedro.encoder.input.gl.render.filters.BeautyFilterRender
import com.pedro.encoder.input.gl.render.filters.BlackFilterRender
import com.pedro.encoder.input.gl.render.filters.BlurFilterRender
import com.pedro.encoder.input.gl.render.filters.BrightnessFilterRender
import com.pedro.encoder.input.gl.render.filters.CartoonFilterRender
import com.pedro.encoder.input.gl.render.filters.ChromaFilterRender
import com.pedro.encoder.input.gl.render.filters.CircleFilterRender
import com.pedro.encoder.input.gl.render.filters.ColorFilterRender
import com.pedro.encoder.input.gl.render.filters.ContrastFilterRender
import com.pedro.encoder.input.gl.render.filters.CropFilterRender
import com.pedro.encoder.input.gl.render.filters.DuotoneFilterRender
import com.pedro.encoder.input.gl.render.filters.EarlyBirdFilterRender
import com.pedro.encoder.input.gl.render.filters.EdgeDetectionFilterRender
import com.pedro.encoder.input.gl.render.filters.ExposureFilterRender
import com.pedro.encoder.input.gl.render.filters.FireFilterRender
import com.pedro.encoder.input.gl.render.filters.GammaFilterRender
import com.pedro.encoder.input.gl.render.filters.GlitchFilterRender
import com.pedro.encoder.input.gl.render.filters.GreyScaleFilterRender
import com.pedro.encoder.input.gl.render.filters.HalftoneLinesFilterRender
import com.pedro.encoder.input.gl.render.filters.Image70sFilterRender
import com.pedro.encoder.input.gl.render.filters.LamoishFilterRender
import com.pedro.encoder.input.gl.render.filters.MoneyFilterRender
import com.pedro.encoder.input.gl.render.filters.NegativeFilterRender
import com.pedro.encoder.input.gl.render.filters.NoiseFilterRender
import com.pedro.encoder.input.gl.render.filters.PixelatedFilterRender
import com.pedro.encoder.input.gl.render.filters.PolygonizationFilterRender
import com.pedro.encoder.input.gl.render.filters.RGBSaturationFilterRender
import com.pedro.encoder.input.gl.render.filters.RainbowFilterRender
import com.pedro.encoder.input.gl.render.filters.RippleFilterRender
import com.pedro.encoder.input.gl.render.filters.RotationFilterRender
import com.pedro.encoder.input.gl.render.filters.SaturationFilterRender
import com.pedro.encoder.input.gl.render.filters.SepiaFilterRender
import com.pedro.encoder.input.gl.render.filters.SharpnessFilterRender
import com.pedro.encoder.input.gl.render.filters.SnowFilterRender
import com.pedro.encoder.input.gl.render.filters.SwirlFilterRender
import com.pedro.encoder.input.gl.render.filters.TemperatureFilterRender
import com.pedro.encoder.input.gl.render.filters.ZebraFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.GifObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.library.view.GlInterface
import com.pedro.streamer.R
import java.io.IOException

/**
 * Created by pedro on 27/2/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class FilterMenu(private val context: Context) {

  val spriteGestureController = SpriteGestureController()

  fun onOptionsItemSelected(item: MenuItem, glInterface: GlInterface): Boolean {
    //Stop listener for image, text and gif stream objects.
    spriteGestureController.stopListener()
    when (item.itemId) {
      R.id.no_filter -> {
        glInterface.clearFilters()
        return true
      }
      R.id.analog_tv -> {
        glInterface.setFilter(AnalogTVFilterRender())
        return true
      }
      R.id.android_view -> {
        val view: View = LayoutInflater.from(context).inflate(R.layout.layout_android_filter, null)
        //Get root view to know max width and max height in the XML layout
        val previewSize = glInterface.encoderSize
        val sizeSpecWidth = View.MeasureSpec.makeMeasureSpec(previewSize.x, View.MeasureSpec.EXACTLY)
        val sizeSpecHeight = View.MeasureSpec.makeMeasureSpec(previewSize.y, View.MeasureSpec.EXACTLY)
        //Set view size to allow rendering
        view.measure(sizeSpecWidth, sizeSpecHeight)
        view.layout(0, 0, previewSize.x, previewSize.y)
        val androidViewFilterRender = AndroidViewFilterRender()
        androidViewFilterRender.view = view
        glInterface.setFilter(androidViewFilterRender)
        return true
      }
      R.id.basic_deformation -> {
        glInterface.addFilter(BasicDeformationFilterRender())
        return true
      }
      R.id.beauty -> {
        glInterface.setFilter(BeautyFilterRender())
        return true
      }
      R.id.black -> {
        glInterface.setFilter(BlackFilterRender())
        return true
      }
      R.id.blur -> {
        glInterface.setFilter(BlurFilterRender())
        return true
      }
      R.id.brightness -> {
        glInterface.setFilter(BrightnessFilterRender())
        return true
      }
      R.id.cartoon -> {
        glInterface.setFilter(CartoonFilterRender())
        return true
      }
      R.id.chroma -> {
        val chromaFilterRender = ChromaFilterRender()
        glInterface.setFilter(chromaFilterRender)
        chromaFilterRender.setImage(
          BitmapFactory.decodeResource(context.resources, R.drawable.bg_chroma)
        )
        return true
      }
      R.id.circle -> {
        glInterface.setFilter(CircleFilterRender())
        return true
      }
      R.id.color -> {
        glInterface.setFilter(ColorFilterRender())
        return true
      }
      R.id.contrast -> {
        glInterface.setFilter(ContrastFilterRender())
        return true
      }
      R.id.crop -> {
        glInterface.setFilter(CropFilterRender().apply {
          //crop center of the image with 40% of width and 40% of height
          setCropArea(30f, 30f, 40f, 40f)
        })
        return true
      }
      R.id.duotone -> {
        glInterface.setFilter(DuotoneFilterRender())
        return true
      }
      R.id.early_bird -> {
        glInterface.setFilter(EarlyBirdFilterRender())
        return true
      }
      R.id.edge_detection -> {
        glInterface.setFilter(EdgeDetectionFilterRender())
        return true
      }
      R.id.exposure -> {
        glInterface.setFilter(ExposureFilterRender())
        return true
      }
      R.id.fire -> {
        glInterface.setFilter(FireFilterRender())
        return true
      }
      R.id.gamma -> {
        glInterface.setFilter(GammaFilterRender())
        return true
      }
      R.id.glitch -> {
        glInterface.setFilter(GlitchFilterRender())
        return true
      }
      R.id.gif -> {
        try {
          val gifObjectFilterRender = GifObjectFilterRender()
          gifObjectFilterRender.setGif(context.resources.openRawResource(R.raw.banana))
          glInterface.addFilter(gifObjectFilterRender)
          gifObjectFilterRender.setScale(50f, 50f)
          gifObjectFilterRender.setPosition(TranslateTo.BOTTOM)
          spriteGestureController.setBaseObjectFilterRender(gifObjectFilterRender) //Optional
        } catch (ignored: IOException) { }
        return true
      }
      R.id.grey_scale -> {
        glInterface.setFilter(GreyScaleFilterRender())
        return true
      }
      R.id.halftone_lines -> {
        glInterface.setFilter(HalftoneLinesFilterRender())
        return true
      }
      R.id.image -> {
        val imageObjectFilterRender = ImageObjectFilterRender()
        glInterface.addFilter(imageObjectFilterRender)
        imageObjectFilterRender.setImage(
          BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        )
        imageObjectFilterRender.setScale(50f, 50f)
        imageObjectFilterRender.setPosition(TranslateTo.RIGHT)
        spriteGestureController.setBaseObjectFilterRender(imageObjectFilterRender) //Optional
        spriteGestureController.setPreventMoveOutside(false) //Optional
        return true
      }
      R.id.image_70s -> {
        glInterface.setFilter(Image70sFilterRender())
        return true
      }
      R.id.lamoish -> {
        glInterface.setFilter(LamoishFilterRender())
        return true
      }
      R.id.money -> {
        glInterface.setFilter(MoneyFilterRender())
        return true
      }
      R.id.negative -> {
        glInterface.setFilter(NegativeFilterRender())
        return true
      }
      R.id.noise -> {
        glInterface.setFilter(NoiseFilterRender())
        return true
      }
      R.id.pixelated -> {
        glInterface.setFilter(PixelatedFilterRender())
        return true
      }
      R.id.polygonization -> {
        glInterface.setFilter(PolygonizationFilterRender())
        return true
      }
      R.id.rainbow -> {
        glInterface.setFilter(RainbowFilterRender())
        return true
      }
      R.id.rgb_saturate -> {
        val rgbSaturationFilterRender = RGBSaturationFilterRender()
        glInterface.setFilter(rgbSaturationFilterRender)
        //Reduce green and blue colors 20%. Red will predominate.
        rgbSaturationFilterRender.setRGBSaturation(1f, 0.8f, 0.8f)
        return true
      }
      R.id.ripple -> {
        glInterface.setFilter(RippleFilterRender())
        return true
      }
      R.id.rotation -> {
        val rotationFilterRender = RotationFilterRender()
        glInterface.setFilter(rotationFilterRender)
        rotationFilterRender.rotation = 90
        return true
      }
      R.id.saturation -> {
        glInterface.setFilter(SaturationFilterRender())
        return true
      }
      R.id.sepia -> {
        glInterface.setFilter(SepiaFilterRender())
        return true
      }
      R.id.sharpness -> {
        glInterface.setFilter(SharpnessFilterRender())
        return true
      }
      R.id.snow -> {
        glInterface.setFilter(SnowFilterRender())
        return true
      }
      R.id.swirl -> {
        glInterface.setFilter(SwirlFilterRender())
        return true
      }
      R.id.surface_filter -> {
        val surfaceFilterRender = SurfaceFilterRender { surfaceTexture -> //You can render this filter with other api that draw in a surface. for example you can use VLC
          val mediaPlayer: MediaPlayer = MediaPlayer.create(context, R.raw.big_bunny_240p)
          mediaPlayer.setSurface(Surface(surfaceTexture))
          mediaPlayer.start()
        }
        glInterface.setFilter(surfaceFilterRender)
        //Video is 360x240 so select a percent to keep aspect ratio (50% x 33.3% screen)
        surfaceFilterRender.setScale(50f, 33.3f)
        spriteGestureController.setBaseObjectFilterRender(surfaceFilterRender) //Optional
        return true
      }
      R.id.temperature -> {
        glInterface.setFilter(TemperatureFilterRender())
        return true
      }
      R.id.text -> {
        val textObjectFilterRender = TextObjectFilterRender()
        glInterface.setFilter(textObjectFilterRender)
        textObjectFilterRender.setText("Hello world", 22f, Color.RED)
        textObjectFilterRender.setScale(50f, 50f)
        textObjectFilterRender.setPosition(TranslateTo.CENTER)
        spriteGestureController.setBaseObjectFilterRender(textObjectFilterRender) //Optional
        return true
      }
      R.id.zebra -> {
        glInterface.setFilter(ZebraFilterRender())
        return true
      }
      else -> return false
    }
  }
}