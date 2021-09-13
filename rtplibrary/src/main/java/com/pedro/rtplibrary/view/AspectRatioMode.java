/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.rtplibrary.view;

public enum AspectRatioMode {
    Adjust(0),
    Fill(1),
    AdjustRotate(2),
    FillRotate(3);

    int id;

    AspectRatioMode(int id) {
        this.id = id;
    }

    static AspectRatioMode fromId(int id) {
        for (AspectRatioMode mode : values()) {
            if (mode.id == id) return mode;
        }
        throw new IllegalArgumentException();
    }
}
