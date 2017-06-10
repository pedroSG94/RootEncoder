package com.pedro.encoder.input.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import com.pedro.encoder.audio.GetAccData;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 9/06/17.
 * Get ACC data from a mp4 file
 */

public class AudioDecoder {

  private final String TAG = "AudioDecoder";

  private MediaExtractor audioExtractor;
  private MediaCodec audioDecoder;
  private final GetAccData getAccData;
  private MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
  private Thread thread;
  private String filePath;
  private boolean decoding = false;
  private int sampleRate;
  private boolean isStereo;

  public AudioDecoder(GetAccData getAccData) {
    this.getAccData = getAccData;
  }

  public boolean prepareAudio() {
    try {
      audioExtractor = new MediaExtractor();
      audioExtractor.setDataSource(filePath);
      MediaFormat audioFormat = audioExtractor.getTrackFormat(selectTrack(audioExtractor));
      sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      isStereo = (audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2);
      audioDecoder = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
      audioDecoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      decoding = false;
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Error preparing audio: ", e);
      return false;
    }
  }

  public void start() {
    decoding = true;
    audioDecoder.start();
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        if (Build.VERSION.SDK_INT >= 21) {
          decodeAudioAPI21();
        } else {
          decodeAudio();
        }
      }
    });
    thread.start();
  }

  public void stop() {
    decoding = false;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join();
      } catch (InterruptedException e) {
        thread.interrupt();
      }
      thread = null;
    }
    if (audioExtractor != null) {
      audioExtractor.release();
      audioExtractor = null;
    }
    if (audioDecoder != null) {
      audioDecoder.stop();
      audioDecoder.release();
      audioDecoder = null;
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void decodeAudioAPI21() {
    while (decoding) {
      int inBufferIndex = audioDecoder.dequeueInputBuffer(-1);
      if (inBufferIndex >= 0) {
        ByteBuffer bb = audioDecoder.getInputBuffer(inBufferIndex);
        audioExtractor.readSampleData(bb, inBufferIndex);
        long pts = audioExtractor.getSampleTime();
        audioDecoder.queueInputBuffer(inBufferIndex, 0, bb.remaining(), pts, 0);
        audioExtractor.advance();
      }
      for (; ; ) {
        int outBufferIndex = audioDecoder.dequeueOutputBuffer(audioInfo, 0);
        if (outBufferIndex >= 0) {
          if ((audioInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            decoding = false;
          }
          if ((audioInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //This ByteBuffer is ACC
            ByteBuffer bb = audioDecoder.getOutputBuffer(outBufferIndex);
            getAccData.getAccData(bb, audioInfo);
            audioDecoder.releaseOutputBuffer(outBufferIndex, false);
          }
        } else {
          break;
        }
      }
    }
  }

  private void decodeAudio() {
    ByteBuffer[] inputBuffers = audioDecoder.getInputBuffers();
    ByteBuffer[] outputBuffers = audioDecoder.getOutputBuffers();
    while (decoding) {
      int inBufferIndex = audioDecoder.dequeueInputBuffer(-1);
      if (inBufferIndex >= 0) {
        ByteBuffer bb = inputBuffers[inBufferIndex];
        bb.clear();
        audioExtractor.readSampleData(bb, inBufferIndex);
        long pts = audioExtractor.getSampleTime();
        audioDecoder.queueInputBuffer(inBufferIndex, 0, bb.remaining(), pts, 0);
        audioExtractor.advance();
      }

      for (; ; ) {
        int outBufferIndex = audioDecoder.dequeueOutputBuffer(audioInfo, 0);
        if (outBufferIndex >= 0) {
          if ((audioInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            decoding = false;
          }
          if ((audioInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //This ByteBuffer is ACC
            ByteBuffer bb = outputBuffers[outBufferIndex];
            getAccData.getAccData(bb, audioInfo);
            audioDecoder.releaseOutputBuffer(outBufferIndex, false);
          }
        } else {
          break;
        }
      }
    }
  }

  /**
   * @return the track index, or -1 if no audio track is found.
   */
  private int selectTrack(MediaExtractor extractor) {
    // Select the first video track we find, ignore the rest.
    int numTracks = extractor.getTrackCount();
    for (int i = 0; i < numTracks; i++) {
      MediaFormat format = extractor.getTrackFormat(i);
      String mime = format.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("audio/")) {
        Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
        return i;
      }
    }
    return -1;
  }

  public void setFilePath(String filePath) throws FileNotFoundException {
    File file = new File(filePath);
    if (file.canRead()) {
      this.filePath = file.getAbsolutePath();
    } else {
      throw new FileNotFoundException("The file can't be read or not exists");
    }
  }

  public void setFilePath(File file) throws FileNotFoundException {
    if (file.canRead()) {
      this.filePath = file.getAbsolutePath();
    } else {
      throw new FileNotFoundException("The file can't be read or not exists");
    }
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public boolean isStereo() {
    return isStereo;
  }
}
