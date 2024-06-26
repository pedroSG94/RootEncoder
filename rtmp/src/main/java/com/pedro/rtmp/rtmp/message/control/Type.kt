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

package com.pedro.rtmp.rtmp.message.control

/**
 * Created by pedro on 21/04/21.
 */
enum class Type(val mark: Byte) {
  /**
   * Type: 0
   * The server sends this event to notify the client that a stream has become
   * functional and can be used for communication. By default, this event
   * is sent on ID 0 after the application connect command is successfully
   * received from the client.
   *
   * Event Data:
   * eventData[0] (int) the stream ID of the stream that became functional
   */
  STREAM_BEGIN(0x00),

  /**
   * Type: 1
   * The server sends this event to notify the client that the playback of
   * data is over as requested on this stream. No more data is sent without
   * issuing additional commands. The client discards the messages received
   * for the stream.
   *
   * Event Data:
   * eventData[0]: the ID of thestream on which playback has ended.
   */
  STREAM_EOF(0x01),

  /**
   * Type: 2
   * The server sends this event to notify the client that there is no
   * more data on the stream. If the server does not detect any message for
   * a time period, it can notify the subscribed clients that the stream is
   * dry.
   *
   * Event Data:
   * eventData[0]: the stream ID of the dry stream.
   */
  STREAM_DRY(0x02),

  /**
   * Type: 3
   * The client sends this event to inform the server of the buffer size
   * (in milliseconds) that is used to buffer any data coming over a stream.
   * This event is sent before the server starts  processing the stream.
   *
   * Event Data:
   * eventData[0]: the stream ID and
   * eventData[1]: the buffer length, in milliseconds.
   */
  SET_BUFFER_LENGTH(0x03),

  /**
   * Type: 4
   * The server sends this event to notify the client that the stream is a
   * recorded stream.
   *
   * Event Data:
   * eventData[0]: the stream ID of the recorded stream.
   */
  STREAM_IS_RECORDED(0x04),

  /**
   * Type: 6
   * The server sends this event to test whether the client is reachable.
   *
   * Event Data:
   * eventData[0]: a timestamp representing the local server time when the server dispatched the command.
   *
   * The client responds with PING_RESPONSE on receiving PING_REQUEST.
   */
  PING_REQUEST(0x06),

  /**
   * Type: 7
   * The client sends this event to the server in response to the ping request.
   *
   * Event Data:
   * eventData[0]: the 4-byte timestamp which was received with the PING_REQUEST.
   */
  PONG_REPLY(0x07),

  /**
   * Type: 31 (0x1F)
   *
   * This user control type is not specified in any official documentation, but
   * is sent by Flash Media Server 3.5. Thanks to the rtmpdump devs for their
   * explanation:
   *
   * Buffer Empty (unofficial name): After the server has sent a complete buffer, and
   * sends this Buffer Empty message, it will wait until the play
   * duration of that buffer has passed before sending a new buffer.
   * The Buffer Ready message will be sent when the new buffer starts.
   *
   * (see also: http://repo.or.cz/w/rtmpdump.git/blob/8880d1456b282ee79979adbe7b6a6eb8ad371081:/librtmp/rtmp.c#l2787)
   */
  BUFFER_EMPTY(0x1F),

  /**
   * Type: 32 (0x20)
   *
   * This user control type is not specified in any official documentation, but
   * is sent by Flash Media Server 3.5. Thanks to the rtmpdump devs for their
   * explanation:
   *
   * Buffer Ready (unofficial name): After the server has sent a complete buffer, and
   * sends a Buffer Empty message, it will wait until the play
   * duration of that buffer has passed before sending a new buffer.
   * The Buffer Ready message will be sent when the new buffer starts.
   * (There is no BufferReady message for the very first buffer;
   * presumably the Stream Begin message is sufficient for that
   * purpose.)
   *
   * (see also: http://repo.or.cz/w/rtmpdump.git/blob/8880d1456b282ee79979adbe7b6a6eb8ad371081:/librtmp/rtmp.c#l2787)
   */
  BUFFER_READY(0x20)
}