package com.pedro.encoder.input.audio;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

/**
 * Created by pedro on 11/05/17.
 */

public class AudioPostProcessEffect {

  private final String TAG = "AudioPostProcessEffect";

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
      if (automaticGainControl != null) {
        automaticGainControl.setEnabled(true);
        Log.i(TAG, "AutoGainControl enabled");
        return;
      }
    }
    Log.e(TAG, "This device does't implement AutoGainControl");
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
      if (acousticEchoCanceler != null) {
        acousticEchoCanceler.setEnabled(true);
        Log.i(TAG, "EchoCanceler enabled");
        return;
      }
    }
    Log.e(TAG, "This device does't implement EchoCanceler");
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
      if (noiseSuppressor != null) {
        noiseSuppressor.setEnabled(true);
        Log.i(TAG, "NoiseSuppressor enabled");
        return;
      }
    }
    Log.e(TAG, "This device does't implement NoiseSuppressor");
  }

  public void releaseNoiseSuppressor() {
    if (noiseSuppressor != null) {
      noiseSuppressor.setEnabled(false);
      noiseSuppressor.release();
      noiseSuppressor = null;
    }
  }
}
