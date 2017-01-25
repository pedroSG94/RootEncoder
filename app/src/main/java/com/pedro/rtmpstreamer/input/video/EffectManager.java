package com.pedro.rtmpstreamer.input.video;

import android.hardware.Camera;

/**
 * Created by pedro on 22/01/17.
 */

public enum EffectManager {

    NEGATIVE, AQUA, SEPIA, BLACKBOARD, GREYSCALE, WHITEBOARD, POSTERIZE, SOLARIZE, CLEAR;

    public String getEffect() {
        switch (this) {
            case NEGATIVE:
                return Camera.Parameters.EFFECT_NEGATIVE;
            case AQUA:
                return Camera.Parameters.EFFECT_AQUA;
            case SEPIA:
                return Camera.Parameters.EFFECT_SEPIA;
            case BLACKBOARD:
                return Camera.Parameters.EFFECT_BLACKBOARD;
            case GREYSCALE:
                return Camera.Parameters.EFFECT_MONO;
            case WHITEBOARD:
                return Camera.Parameters.EFFECT_WHITEBOARD;
            case POSTERIZE:
                return Camera.Parameters.EFFECT_POSTERIZE;
            case SOLARIZE:
                return Camera.Parameters.EFFECT_SOLARIZE;
            case CLEAR:
                return Camera.Parameters.EFFECT_NONE;
            default:
                return null;
        }
    }
}
