package com.pedro.builder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.decoder.AudioDecoder;

/**
 * Created by pedro on 20/06/17.
 */
public class DecodersTest implements GetMicrophoneData {

  private final String TAG = "DecodersTest";

  private AudioTrack audioTrack;

  public void audioDecoderTest(String filePath) {
    AudioDecoder audioDecoderThread = new AudioDecoder(this);
    audioDecoderThread.prepareAudio(filePath);

    int buffsize = AudioTrack.getMinBufferSize(audioDecoderThread.getSampleRate(),
        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, audioDecoderThread.getSampleRate(),
        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, buffsize,
        AudioTrack.MODE_STREAM);

    audioTrack.play();
    audioDecoderThread.start();
  }

  @Override
  public void inputPcmData(byte[] buffer, int size) {
    Log.i(TAG, "PCM buffer");
    audioTrack.write(buffer, 0, size);
  }
}
