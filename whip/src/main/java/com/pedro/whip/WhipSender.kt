package com.pedro.whip

import com.pedro.common.ConnectChecker
import com.pedro.common.base.BaseSender
import com.pedro.whip.webrtc.CommandsManager
import java.nio.ByteBuffer

class WhipSender(
    connectChecker: ConnectChecker,
    private val commandsManager: CommandsManager
): BaseSender(connectChecker, "WhipSender") {

    override fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {

    }

    override fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {

    }

    override suspend fun onRun() {

    }

    override suspend fun stopImp(clear: Boolean) {

    }
}