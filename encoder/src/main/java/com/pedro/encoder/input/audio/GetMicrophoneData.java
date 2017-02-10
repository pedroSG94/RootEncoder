package com.pedro.encoder.input.audio;

/**
 * Created by pedro on 19/01/17.
 */

public interface GetMicrophoneData {

    void inputPcmData(byte[] buffer, int size);
}
