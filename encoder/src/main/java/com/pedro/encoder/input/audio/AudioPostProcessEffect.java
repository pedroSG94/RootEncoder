package com.pedro.encoder.input.audio;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by pedro on 11/05/17.
 */

public class AudioPostProcessEffect {

  private final static Logger logger = LoggerFactory.getLogger(AudioPostProcessEffect.class);

  private int microphoneId;
  private AcousticEchoCanceler acousticEchoCanceler = null;
  private AutomaticGainControl automaticGainControl = null;
  private NoiseSuppressor noiseSuppressor = null;

  public AudioPostProcessEffect(int microphoneId) {
    this.microphoneId = microphoneId;
  }

  public void enableAutoGainControl() {
    if (AutomaticGainControl.isAvailable() && automaticGainControl == null) {
      automaticGainControl = AutomaticGainControl.create(microphoneId);
      automaticGainControl.setEnabled(true);
      logger.info("AutoGainControl enabled");
    } else {
      logger.error("This device don't support AutoGainControl");
    }
  }

  public void releaseAutoGainControl() {
    if (automaticGainControl != null) {
      automaticGainControl.setEnabled(false);
      automaticGainControl.release();
      automaticGainControl = null;
    }
  }

  public void enableEchoCanceler() {
    if (AcousticEchoCanceler.isAvailable() && acousticEchoCanceler == null) {
      acousticEchoCanceler = AcousticEchoCanceler.create(microphoneId);
      acousticEchoCanceler.setEnabled(true);
      logger.info("EchoCanceler enabled");
    } else {
      logger.error("This device don't support EchoCanceler");
    }
  }

  public void releaseEchoCanceler() {
    if (acousticEchoCanceler != null) {
      acousticEchoCanceler.setEnabled(false);
      acousticEchoCanceler.release();
      acousticEchoCanceler = null;
    }
  }

  public void enableNoiseSuppressor() {
    if (NoiseSuppressor.isAvailable() && noiseSuppressor == null) {
      noiseSuppressor = NoiseSuppressor.create(microphoneId);
      noiseSuppressor.setEnabled(true);
      logger.info("NoiseSuppressor enabled");
    } else {
      logger.error("This device don't support NoiseSuppressor");
    }
  }

  public void releaseNoiseSuppressor() {
    if (noiseSuppressor != null) {
      noiseSuppressor.setEnabled(false);
      noiseSuppressor.release();
      noiseSuppressor = null;
    }
  }
}
