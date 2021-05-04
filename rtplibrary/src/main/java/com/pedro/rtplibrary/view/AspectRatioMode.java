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
