package com.pedro.encoder.input.audio;

public abstract class CustomAudioEffect {

  /**
   * @param pcmBuffer buffer obtained directly from the microphone.
   * @return it must be of same size that pcmBuffer parameter.
   */
  public abstract byte[] process(byte[] pcmBuffer);
}
