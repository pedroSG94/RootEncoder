package com.pedro.encoder.input.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import com.pedro.encoder.Frame;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.utils.PCMUtil;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/06/17.
 */
public class AudioDecoder {

  private final String TAG = "AudioDecoder";

  private AudioDecoderInterface audioDecoderInterface;
  private LoopFileInterface loopFileInterface;
  private MediaExtractor audioExtractor;
  private MediaCodec audioDecoder;
  private MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
  private boolean decoding;
  private Thread thread;
  private GetMicrophoneData getMicrophoneData;
  private MediaFormat audioFormat;
  private String mime = "";
  private int sampleRate;
  private boolean isStereo;
  private int channels = 2;
  private int size = 4096;
  private byte[] pcmBuffer = new byte[size];
  private byte[] pcmBufferMuted = new byte[11];
  private static boolean loopMode = false;
  private boolean muted = false;
  private long duration;
  private volatile long seekTime = 0;
  private volatile long startMs = 0;

  public AudioDecoder(GetMicrophoneData getMicrophoneData,
      AudioDecoderInterface audioDecoderInterface, LoopFileInterface loopFileInterface) {
    this.getMicrophoneData = getMicrophoneData;
    this.audioDecoderInterface = audioDecoderInterface;
    this.loopFileInterface = loopFileInterface;
  }

  public boolean initExtractor(String filePath) throws IOException {
    decoding = false;
    audioExtractor = new MediaExtractor();
    audioExtractor.setDataSource(filePath);
    for (int i = 0; i < audioExtractor.getTrackCount() && !mime.startsWith("audio/"); i++) {
      audioFormat = audioExtractor.getTrackFormat(i);
      mime = audioFormat.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("audio/")) {
        audioExtractor.selectTrack(i);
      } else {
        audioFormat = null;
      }
    }
    if (audioFormat != null) {
      channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      isStereo = channels >= 2;
      sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      duration = audioFormat.getLong(MediaFormat.KEY_DURATION);
      if (channels > 2) {
        pcmBuffer = new byte[2048 * channels];
      }
      return true;
      //audio decoder not supported
    } else {
      mime = "";
      audioFormat = null;
      return false;
    }
  }

  public boolean prepareAudio() {
    try {
      audioDecoder = MediaCodec.createDecoderByType(mime);
      audioDecoder.configure(audioFormat, null, null, 0);
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Prepare decoder error:", e);
      return false;
    }
  }

  public void start() {
    decoding = true;
    audioDecoder.start();
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        decodeAudio();
      }
    });
    thread.start();
  }

  public void stop() {
    decoding = false;
    seekTime = 0;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(100);
      } catch (InterruptedException e) {
        thread.interrupt();
      }
      thread = null;
    }
    if (audioDecoder != null) {
      audioDecoder.stop();
      audioDecoder.release();
      audioDecoder = null;
    }
    if (audioExtractor != null) {
      audioExtractor.release();
      audioExtractor = null;
    }
  }

  private void decodeAudio() {
    ByteBuffer[] inputBuffers = audioDecoder.getInputBuffers();
    ByteBuffer[] outputBuffers = audioDecoder.getOutputBuffers();
    startMs = System.currentTimeMillis();
    while (decoding) {
      int inIndex = audioDecoder.dequeueInputBuffer(10000);
      if (inIndex >= 0) {
        ByteBuffer buffer = inputBuffers[inIndex];
        int sampleSize = audioExtractor.readSampleData(buffer, 0);
        if (sampleSize < 0) {
          audioDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
          audioDecoder.queueInputBuffer(inIndex, 0, sampleSize, audioExtractor.getSampleTime(), 0);
          audioExtractor.advance();
        }

        int outIndex = audioDecoder.dequeueOutputBuffer(audioInfo, 10000);
        switch (outIndex) {
          case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
            outputBuffers = audioDecoder.getOutputBuffers();
            break;
          case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
          case MediaCodec.INFO_TRY_AGAIN_LATER:
            break;
          default:
            //needed for fix decode speed
            while (audioExtractor.getSampleTime() / 1000
                > System.currentTimeMillis() - startMs + seekTime) {
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                thread.interrupt();
                return;
              }
            }
            ByteBuffer outBuffer = outputBuffers[outIndex];
            //This buffer is PCM data
            if (muted) {
              outBuffer.get(pcmBufferMuted, 0, pcmBufferMuted.length);
              getMicrophoneData.inputPCMData(new Frame(pcmBufferMuted, 0, pcmBufferMuted.length));
            } else {
              outBuffer.get(pcmBuffer, 0, pcmBuffer.length);
              if (channels > 2) { //downgrade to stereo
                byte[] bufferStereo = PCMUtil.pcmToStereo(pcmBuffer, channels);
                getMicrophoneData.inputPCMData(new Frame(bufferStereo, 0, bufferStereo.length));
              } else {
                getMicrophoneData.inputPCMData(new Frame(pcmBuffer, 0, pcmBuffer.length));
              }
            }
            audioDecoder.releaseOutputBuffer(outIndex, false);
            break;
        }

        // All decoded frames have been rendered, we can stop playing now
        if ((audioInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
          seekTime = 0;
          Log.i(TAG, "end of file out");
          if (loopMode) {
            loopFileInterface.onReset(false);
          } else {
            audioDecoderInterface.onAudioDecoderFinished();
          }
        }
      }
    }
  }

  public double getTime() {
    if (decoding) {
      return audioExtractor.getSampleTime() / 10E5;
    } else {
      return 0;
    }
  }

  public void moveTo(double time) {
    audioExtractor.seekTo((long) (time * 10E5), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    seekTime = audioExtractor.getSampleTime() / 1000;
    startMs = System.currentTimeMillis();
  }

  public void setLoopMode(boolean loopMode) {
    this.loopMode = loopMode;
  }

  public void mute() {
    muted = true;
  }

  public void unMute() {
    muted = false;
  }

  public boolean isMuted() {
    return muted;
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public boolean isStereo() {
    return isStereo;
  }

  public double getDuration() {
    return duration / 10E5;
  }
}
