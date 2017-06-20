package com.pedro.builder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import android.view.Surface;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.decoder.AudioDecoder;
import com.pedro.encoder.input.decoder.VideoDecoder;
import com.pedro.encoder.input.video.GetCameraData;

/**
 * Created by pedro on 20/06/17.
 */
public class DecodersTest implements GetMicrophoneData, GetCameraData {

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

  public void videoDecoderTest(Surface surface, String filePath) {
    VideoDecoder videoDecoder = new VideoDecoder(this);
    videoDecoder.prepareVideo(surface, filePath);
    videoDecoder.start();
  }

  @Override
  public void inputPcmData(byte[] buffer, int size) {
    Log.i(TAG, "PCM buffer");
    audioTrack.write(buffer, 0, size);
  }

  @Override
  public void inputYv12Data(byte[] buffer) {
    Log.i(TAG, "YUV buffer");
  }

  @Override
  public void inputNv21Data(byte[] buffer) {
    Log.i(TAG, "YUV buffer");
  }
}
