package com.pedro.encoder.input.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import com.pedro.encoder.Frame;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.utils.PCMUtil;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/06/17.
 */
public class AudioDecoder extends BaseDecoder {

  private AudioDecoderInterface audioDecoderInterface;
  private GetMicrophoneData getMicrophoneData;
  private int sampleRate;
  private boolean isStereo;
  private int channels = 1;
  private int size = 2048;
  private byte[] pcmBuffer = new byte[size];
  private byte[] pcmBufferMuted = new byte[11];
  private boolean muted = false;

  public AudioDecoder(GetMicrophoneData getMicrophoneData,
      AudioDecoderInterface audioDecoderInterface, LoopFileInterface loopFileInterface) {
    super(loopFileInterface);
    this.getMicrophoneData = getMicrophoneData;
    this.audioDecoderInterface = audioDecoderInterface;
  }

  @Override
  protected boolean extract(MediaExtractor audioExtractor) {
    running = false;
    for (int i = 0; i < audioExtractor.getTrackCount() && !mime.startsWith("audio/"); i++) {
      mediaFormat = audioExtractor.getTrackFormat(i);
      mime = mediaFormat.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("audio/")) {
        audioExtractor.selectTrack(i);
      } else {
        mediaFormat = null;
      }
    }
    if (mediaFormat != null) {
      channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      isStereo = channels >= 2;
      sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
      if (channels >= 2) {
        pcmBuffer = new byte[2048 * channels];
      }
      return true;
      //audio decoder not supported
    } else {
      mime = "";
      mediaFormat = null;
      return false;
    }
  }

  public boolean prepareAudio() {
    return prepare(null);
  }

  public void reset() {
    resetCodec(null);
  }

  @Override
  protected void decode() {
    ByteBuffer[] inputBuffers = codec.getInputBuffers();
    ByteBuffer[] outputBuffers = codec.getOutputBuffers();
    startMs = System.currentTimeMillis();
    while (running) {
      int inIndex = codec.dequeueInputBuffer(10000);
      if (inIndex >= 0) {
        ByteBuffer buffer = inputBuffers[inIndex];
        int sampleSize = extractor.readSampleData(buffer, 0);
        if (sampleSize < 0) {
          codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
          codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
          extractor.advance();
        }

        int outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
        switch (outIndex) {
          case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
            outputBuffers = codec.getOutputBuffers();
            break;
          case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
          case MediaCodec.INFO_TRY_AGAIN_LATER:
            break;
          default:
            //needed for fix decode speed
            while (extractor.getSampleTime() / 1000
                > System.currentTimeMillis() - startMs + seekTime) {
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
            }
            ByteBuffer outBuffer = outputBuffers[outIndex];
            //This buffer is PCM data
            if (muted) {
              outBuffer.get(pcmBufferMuted, 0,
                  Math.min(outBuffer.remaining(), pcmBufferMuted.length));
              getMicrophoneData.inputPCMData(new Frame(pcmBufferMuted, 0, pcmBufferMuted.length));
            } else {
              outBuffer.get(pcmBuffer, 0, Math.min(outBuffer.remaining(), pcmBuffer.length));
              if (channels > 2) { //downgrade to stereo
                byte[] bufferStereo = PCMUtil.pcmToStereo(pcmBuffer, channels);
                getMicrophoneData.inputPCMData(new Frame(bufferStereo, 0, bufferStereo.length));
              } else {
                getMicrophoneData.inputPCMData(new Frame(pcmBuffer, 0, pcmBuffer.length));
              }
            }
            codec.releaseOutputBuffer(outIndex, false);
            break;
        }

        // All decoded frames have been rendered, we can stop playing now
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
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
}
