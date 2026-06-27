/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.whip.webrtc.stun

import android.util.Log
import com.pedro.common.bytesToHex
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.toUInt32
import com.pedro.whip.webrtc.CommandsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Created by pedro on 7/8/25.
 */
class CandidatePairConnection(
  val socket: UdpStreamSocket,
  val candidatePair: CandidatePair,
  private val commandsManager: CommandsManager
) {

  private val scope = CoroutineScope(Dispatchers.IO)
  @Volatile
  private var state = CandidatePairConnectionState.STOPPED
  private val timeouts = arrayOf(100L, 200L, 400L, 800L, 1500L, 2000)
  private val bindingRequestWaiting = mutableListOf<ByteArray>()
  private val nominatingRequestWaiting = mutableListOf<ByteArray>()
  private val confirmRequestWaiting = mutableListOf<ByteArray>()
  private val sync = Any()

  fun connect(onDone: (CandidatePairConnection) -> Unit) {
    val localFrag = commandsManager.localSdpInfo?.uFrag ?: return
    val remoteFrag = commandsManager.remoteSdpInfo?.uFrag ?: return
    val host = candidatePair.remote.publicAddress ?: candidatePair.remote.localAddress
    val port = candidatePair.remote.publicPort ?: candidatePair.remote.localPort
    scope.launch {
      state = CandidatePairConnectionState.BINDING
      sendBindingRequestToCandidate(localFrag, remoteFrag, host, port, socket)
      if (state == CandidatePairConnectionState.FAILED) {
        Log.i("Pedro", "binding failed")
        return@launch
      }
      sendNominateBindingRequestToCandidate(localFrag, remoteFrag, host, port, socket)
      if (state == CandidatePairConnectionState.FAILED) {
        Log.i("Pedro", "binding failed")
        return@launch
      }
      sendConfirmRequestToCandidate(localFrag, remoteFrag, host, port, socket)
      if (state == CandidatePairConnectionState.FAILED) {
        Log.i("Pedro", "confirm failed")
        return@launch
      }
      onDone(this@CandidatePairConnection)
    }
  }

  fun stop() {
    scope.cancel()
    state = CandidatePairConnectionState.STOPPED
    bindingRequestWaiting.clear()
    nominatingRequestWaiting.clear()
    confirmRequestWaiting.clear()
  }

  fun handleResponse(data: ByteArray, host: String , port: Int) {
    val remoteHost = candidatePair.remote.getRealHost()
    val remotePort = candidatePair.remote.getRealPort()
    if (host != remoteHost || port != remotePort) return
    val command = StunCommandReader.readPacket(data)
    val id = command.header.id
    Log.i("Pedro", "command received, handling: ${id.bytesToHex()}")
    synchronized(sync) {
      Log.i("Pedro", "binding waiting: ${bindingRequestWaiting.map { it.bytesToHex() }}")
      var found = false
      bindingRequestWaiting.forEach {
        if (it.contentEquals(id)) {
          state = CandidatePairConnectionState.NOMINATING
          found = true
        }
      }
      nominatingRequestWaiting.forEach {
        if (it.contentEquals(id)) {
          state = CandidatePairConnectionState.CONFIRMING
          found = true
        }
      }
      confirmRequestWaiting.forEach {
        if (it.contentEquals(id)) {
          state = CandidatePairConnectionState.DONE
          found = true
        }
      }
      if (!found) {
        scope.launch {
          val localFrag = commandsManager.localSdpInfo?.uFrag ?: return@launch
          val remoteFrag = commandsManager.remoteSdpInfo?.uFrag ?: return@launch
          val host = candidatePair.remote.publicAddress ?: candidatePair.remote.localAddress
          val port = candidatePair.remote.publicPort ?: candidatePair.remote.localPort
          sendSuccess(id, localFrag, remoteFrag, host, port, socket)
        }
      }
    }
  }

  private suspend fun sendBindingRequestToCandidate(
    localFrag: String, remoteFrag: String, host: String, port: Int,
    socket: UdpStreamSocket
  ) {
    for (i in 0..timeouts.size) {
      if (state != CandidatePairConnectionState.BINDING) return

      val id = commandsManager.generateTransactionId()
      val userName = StunAttributeValueParser.createUserName(localFrag, remoteFrag)
      val attributes = listOf(
        StunAttribute(AttributeType.PRIORITY, candidatePair.local.priority.toUInt32()),
        StunAttribute(AttributeType.USERNAME, userName),
        StunAttribute(AttributeType.ICE_CONTROLLING, commandsManager.tieBreak),
      )
      synchronized(sync) {
        bindingRequestWaiting.add(id)
      }
      //commandsManager.writeStun(HeaderType.REQUEST, id, attributes, socket, host, port)
      Log.i("Pedro", "send binding $i")
      delay(timeouts[i])
    }
    state == CandidatePairConnectionState.FAILED
  }

  private suspend fun sendNominateBindingRequestToCandidate(
    localFrag: String, remoteFrag: String, host: String, port: Int,
    socket: UdpStreamSocket
  ) {
    for (i in 0..timeouts.size) {
      if (state != CandidatePairConnectionState.NOMINATING) return

      val id = commandsManager.generateTransactionId()
      val userName = StunAttributeValueParser.createUserName(localFrag, remoteFrag)
      val attributes = listOf(
        StunAttribute(AttributeType.PRIORITY, candidatePair.local.priority.toUInt32()),
        StunAttribute(AttributeType.USERNAME, userName),
        StunAttribute(AttributeType.ICE_CONTROLLING, commandsManager.tieBreak),
        StunAttribute(AttributeType.USE_CANDIDATE, byteArrayOf()),
      )
      synchronized(sync) {
        nominatingRequestWaiting.add(id)
      }
      //commandsManager.writeStun(HeaderType.REQUEST, id, attributes, socket, host, port)
      sendConfirmRequestToCandidate(localFrag, remoteFrag, host, port, socket)
      Log.i("Pedro", "send nominate $i")
      delay(timeouts[i])
    }
    state == CandidatePairConnectionState.FAILED
  }

  private suspend fun sendConfirmRequestToCandidate(
    localFrag: String, remoteFrag: String, host: String, port: Int,
    socket: UdpStreamSocket
  ) {
    val id = commandsManager.generateTransactionId()
    val userName = StunAttributeValueParser.createUserName(localFrag, remoteFrag)
    val attributes = listOf(
      StunAttribute(AttributeType.PRIORITY, candidatePair.local.priority.toUInt32()),
      StunAttribute(AttributeType.USERNAME, userName),
      StunAttribute(AttributeType.ICE_CONTROLLING, commandsManager.tieBreak),
    )
    synchronized(sync) {
      confirmRequestWaiting.add(id)
    }
    //commandsManager.writeStun(HeaderType.REQUEST, id, attributes, socket, host, port)
  }

  private suspend fun sendSuccess(
    id: ByteArray, localFrag: String, remoteFrag: String, host: String, port: Int,
    socket: UdpStreamSocket
  ) {
    val userNameValue = StunAttributeValueParser.createUserName(remoteFrag, localFrag)
    val xorAddress = StunAttributeValueParser.createXorMappedAddress(id, host, port, true)
    val attributes = listOf(
      StunAttribute(AttributeType.USERNAME, userNameValue),
      StunAttribute(AttributeType.XOR_MAPPED_ADDRESS, xorAddress)
    )
    //commandsManager.writeStun(HeaderType.SUCCESS, id, attributes, socket, host, port)
    Log.i("Pedro", "send success")
  }
}