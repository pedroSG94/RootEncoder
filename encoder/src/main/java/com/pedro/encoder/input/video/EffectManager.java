package com.pedro.encoder.input.video;

import android.hardware.Camera;

/**
 * Created by pedro on 22/01/17.
 */

public enum EffectManager {

    CLEAR, GREYSCALE, SEPIA, NEGATIVE, AQUA, POSTERIZE;

    public String getEffect() {
        switch (this) {
            case CLEAR:
                return Camera.Parameters.EFFECT_NONE;
            case GREYSCALE:
                return Camera.Parameters.EFFECT_MONO;
            case SEPIA:
                return Camera.Parameters.EFFECT_SEPIA;
            case NEGATIVE:
                return Camera.Parameters.EFFECT_NEGATIVE;
            case AQUA:
                return Camera.Parameters.EFFECT_AQUA;
            case POSTERIZE:
                return Camera.Parameters.EFFECT_POSTERIZE;
            default:
                return null;
        }
    }
}
