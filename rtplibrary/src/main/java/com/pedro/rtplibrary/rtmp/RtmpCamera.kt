package com.pedro.rtplibrary.rtmp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.rtplibrary.base.CameraBase
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/3/22.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RtmpCamera(context: Context): CameraBase(context = context) {

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {

  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {

  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {

  }
}