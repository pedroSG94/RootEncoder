/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.common;

import org.jetbrains.annotations.NotNull;

/**
 * Created by pedro on 8/3/24.
 */
public interface ConnectCheckerEvent extends ConnectChecker {

    void onStreamEvent(StreamEvent event, String message);

    @Override
    default void onConnectionStarted(@NotNull String url) {
        onStreamEvent(StreamEvent.STARTED, url);
    }

    @Override
    default void onConnectionSuccess() {
        onStreamEvent(StreamEvent.CONNECTED, "");
    }

    @Override
    default void onConnectionFailed(@NotNull String reason) {
        onStreamEvent(StreamEvent.FAILED, reason);
    }

    @Override
    default void onNewBitrate(long bitrate) {
        onStreamEvent(StreamEvent.NEW_BITRATE, Long.toString(bitrate));
    }

    @Override
    default void onDisconnect() {
        onStreamEvent(StreamEvent.DISCONNECTED, "");
    }

    @Override
    default void onAuthError() {
        onStreamEvent(StreamEvent.AUTH_ERROR, "");
    }

    @Override
    default void onAuthSuccess() {
        onStreamEvent(StreamEvent.AUTH_SUCCESS, "");
    }
}