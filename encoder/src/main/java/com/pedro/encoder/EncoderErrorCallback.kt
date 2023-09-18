package com.pedro.encoder

import android.media.MediaCodec
import java.lang.IllegalStateException

/**
 * Created by pedro on 18/9/23.
 */
interface EncoderErrorCallback {
  fun onCodecError(type: String, e: MediaCodec.CodecException)

  /**
   * @return indicate if should try reset encoder
   */
  fun onEncodeError(type: String, e: IllegalStateException): Boolean = true
}