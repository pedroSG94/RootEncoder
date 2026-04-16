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

package com.pedro.common

/**
 * Created by pedro on 21/08/23.
 */
interface ConnectChecker: BitrateChecker {
  fun onConnectionStarted(url: String)
  fun onConnectionSuccess()
  fun onConnectionFailed(reason: String)
  fun onDisconnect()
  fun onAuthError()
  fun onAuthSuccess()

  /**
   * Typed transport event from the sender layer.
   *
   * Provides machine-readable signal types so callers can differentiate between
   * queue-pressure events ([TransportEvent.QueueOverflow]) and network errors
   * ([TransportEvent.NetworkSendError]).
   *
   * Default implementation is a no-op so existing [ConnectChecker] implementors
   * are not required to override this method. A [TransportEvent.NetworkSendError] is
   * always accompanied by a corresponding [onConnectionFailed] call for backward
   * compatibility.
   *
   * @param event  Typed transport event; use exhaustive `when` for dispatch.
   */
  fun onTransportEvent(event: TransportEvent) {}
}