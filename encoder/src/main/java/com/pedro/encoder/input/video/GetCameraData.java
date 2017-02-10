package com.pedro.encoder.input.video;

/**
 * Created by pedro on 20/01/17.
 */

public interface GetCameraData {

    void inputYv12Data(byte[] buffer, int width, int height);

    void inputNv21Data(byte[] buffer, int width, int height);
}
