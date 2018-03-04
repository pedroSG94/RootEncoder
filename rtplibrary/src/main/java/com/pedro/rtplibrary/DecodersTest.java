package com.pedro.rtplibrary;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.view.Surface;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.decoder.AudioDecoder;
import com.pedro.encoder.input.decoder.AudioDecoderInterface;
import com.pedro.encoder.input.decoder.LoopFileInterface;
import com.pedro.encoder.input.decoder.VideoDecoder;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import java.io.IOException;

/**
 * Created by pedro on 20/06/17.
 * Debug purpose ignore this class. This use decoder for reproduce audio or render a surface
 */
public class DecodersTest
    implements GetMicrophoneData, AudioDecoderInterface, VideoDecoderInterface, LoopFileInterface {

  private final String TAG = "DecodersTest";

  private AudioTrack audioTrack;

  public void audioDecoderTest(String filePath) throws IOException {
    AudioDecoder audioDecoderThread = new AudioDecoder(this, this, this);
    audioDecoderThread.initExtractor(filePath);
    audioDecoderThread.prepareAudio();

    int buffsize = AudioTrack.getMinBufferSize(audioDecoderThread.getSampleRate(),
        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, audioDecoderThread.getSampleRate(),
        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, buffsize,
        AudioTrack.MODE_STREAM);
    audioTrack.play();
    audioDecoderThread.start();
  }

  public void videoDecoderTest(Surface surface, String filePath) throws IOException {
    VideoDecoder videoDecoder = new VideoDecoder(this, this);
    videoDecoder.initExtractor(filePath);
    videoDecoder.prepareVideo(surface);
    videoDecoder.start();
  }

  @Override
  public void inputPCMData(byte[] buffer, int size) {
    audioTrack.write(buffer, 0, size);
  }

  @Override
  public void onAudioDecoderFinished() {

  }

  @Override
  public void onVideoDecoderFinished() {

  }

  @Override
  public void onReset(boolean isVideo) {

  }
}
