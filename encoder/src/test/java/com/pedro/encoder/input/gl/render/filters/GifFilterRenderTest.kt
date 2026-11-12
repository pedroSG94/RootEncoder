package com.pedro.encoder.input.gl.render.filters

import com.pedro.encoder.input.gl.render.filters.`object`.GifFilterRender
import org.junit.Test
import java.io.IOException

class GifFilterRenderTest {

  @Test(expected = IOException::class)
  fun nullInputIsReportedAsIoException() {
    GifFilterRender().setGif(null)
  }
}
