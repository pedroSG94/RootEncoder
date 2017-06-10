package com.pedro.encoder.input.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Pair;
import com.pedro.encoder.video.GetH264Data;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 9/06/17.
 * Get H264 data from a mp4 file
 */

public class VideoDecoder {

  private final String TAG = "VideoDecoder";

  private MediaExtractor videoExtractor;
  private MediaCodec videoDecoder;
  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private Thread thread;
  private GetH264Data getH264Data;
  private String filePath;
  private boolean decoding = false;
  private boolean spsPpsSetted = false;
  private int bitRate = 1200 * 1024;

  public VideoDecoder(GetH264Data getH264Data) {
    this.getH264Data = getH264Data;
  }

  public boolean prepareVideo() {
    try {
      videoExtractor = new MediaExtractor();
      videoExtractor.setDataSource(filePath);
      MediaFormat videoFormat = videoExtractor.getTrackFormat(selectTrack(videoExtractor));
      videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
      videoDecoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
      videoDecoder.configure(videoFormat, null, null, 0);
      decoding = false;
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Error preparing video: ", e);
      return false;
    }
  }

  public void start() {
    spsPpsSetted = false;
    decoding = true;
    videoDecoder.start();
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        if (Build.VERSION.SDK_INT >= 21) {
          decodeVideoAPI21();
        } else {
          decodeVideo();
        }
      }
    });
    thread.start();
  }

  public void stop() {
    decoding = false;
    spsPpsSetted = false;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join();
      } catch (InterruptedException e) {
        thread.interrupt();
      }
      thread = null;
    }
    if (videoExtractor != null) {
      videoExtractor.release();
      videoExtractor = null;
    }
    if (videoDecoder != null) {
      videoDecoder.stop();
      videoDecoder.release();
      videoDecoder = null;
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void decodeVideoAPI21() {
    int cont = 0;
    while (decoding) {
      Log.e(TAG, "times " + cont++);
      int inBufferIndex = videoDecoder.dequeueInputBuffer(-1);
      if (inBufferIndex >= 0) {
        ByteBuffer bb = videoDecoder.getInputBuffer(inBufferIndex);
        int chunkSize = videoExtractor.readSampleData(bb, inBufferIndex);
        if (chunkSize < 0) {
          // End of stream -- send empty frame with EOS flag set.
          videoDecoder.queueInputBuffer(inBufferIndex, 0, 0, 0L,
              MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
          long pts = videoExtractor.getSampleTime();
          videoDecoder.queueInputBuffer(inBufferIndex, 0, bb.remaining(), pts, 0);
          videoExtractor.advance();
        }
      }
      for (; ; ) {
        int outBufferIndex = videoDecoder.dequeueOutputBuffer(videoInfo, 0);
        if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat mediaFormat = videoDecoder.getOutputFormat();
          getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
              mediaFormat.getByteBuffer("csd-1"));
          spsPpsSetted = true;
        } else if (outBufferIndex >= 0) {
          if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            decoding = false;
          }
          if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            if (!spsPpsSetted) {
              Pair<ByteBuffer, ByteBuffer> buffers =
                  decodeSpsPpsFromBuffer(videoDecoder.getOutputBuffer(outBufferIndex),
                      videoInfo.size);
              if (buffers != null) {
                getH264Data.onSPSandPPS(buffers.first, buffers.second);
                spsPpsSetted = true;
              }
            }
          } else {
            //This ByteBuffer is H264
            ByteBuffer bb = videoDecoder.getOutputBuffer(outBufferIndex);
            getH264Data.getH264Data(bb, videoInfo);
            videoDecoder.releaseOutputBuffer(outBufferIndex, false);
          }
        } else {
          break;
        }
      }
    }
  }

  private void decodeVideo() {
    ByteBuffer[] inputBuffers = videoDecoder.getInputBuffers();
    ByteBuffer[] outputBuffers = videoDecoder.getOutputBuffers();
    while (decoding) {
      int inBufferIndex = videoDecoder.dequeueInputBuffer(-1);
      if (inBufferIndex >= 0) {
        ByteBuffer bb = inputBuffers[inBufferIndex];
        bb.clear();
        int chunkSize = videoExtractor.readSampleData(bb, inBufferIndex);
        if (chunkSize < 0) {
          // End of stream -- send empty frame with EOS flag set.
          videoDecoder.queueInputBuffer(inBufferIndex, 0, 0, 0L,
              MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
          long pts = videoExtractor.getSampleTime();
          videoDecoder.queueInputBuffer(inBufferIndex, 0, bb.remaining(), pts, 0);
          videoExtractor.advance();
        }
      }

      for (; ; ) {
        int outBufferIndex = videoDecoder.dequeueOutputBuffer(videoInfo, 0);
        if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat mediaFormat = videoDecoder.getOutputFormat();
          getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
              mediaFormat.getByteBuffer("csd-1"));
          spsPpsSetted = true;
        } else if (outBufferIndex >= 0) {
          if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            decoding = false;
          }
          if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            if (!spsPpsSetted) {
              Pair<ByteBuffer, ByteBuffer> buffers =
                  decodeSpsPpsFromBuffer(outputBuffers[outBufferIndex], videoInfo.size);
              if (buffers != null) {
                getH264Data.onSPSandPPS(buffers.first, buffers.second);
                spsPpsSetted = true;
              }
            }
          } else {
            //This ByteBuffer is H264
            ByteBuffer bb = outputBuffers[outBufferIndex];
            getH264Data.getH264Data(bb, videoInfo);
            videoDecoder.releaseOutputBuffer(outBufferIndex, false);
          }
        } else {
          break;
        }
      }
    }
  }

  /**
   * decode sps and pps if the encoder never call to MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
   */
  private Pair<ByteBuffer, ByteBuffer> decodeSpsPpsFromBuffer(ByteBuffer outputBuffer, int length) {
    byte[] mSPS = null, mPPS = null;
    byte[] csd = new byte[length];
    outputBuffer.get(csd, 0, length);
    int i = 0;
    int spsIndex = -1;
    int ppsIndex = -1;
    while (i < length - 4) {
      if (csd[i] == 0 && csd[i + 1] == 0 && csd[i + 2] == 0 && csd[i + 3] == 1) {
        if (spsIndex == -1) {
          spsIndex = i;
        } else {
          ppsIndex = i;
          break;
        }
      }
      i++;
    }
    if (spsIndex != -1 && ppsIndex != -1) {
      mSPS = new byte[ppsIndex];
      System.arraycopy(csd, spsIndex, mSPS, 0, ppsIndex);
      mPPS = new byte[length - ppsIndex];
      System.arraycopy(csd, ppsIndex, mPPS, 0, length - ppsIndex);
    }
    if (mSPS != null && mPPS != null) {
      return new Pair<>(ByteBuffer.wrap(mSPS), ByteBuffer.wrap(mPPS));
    }
    return null;
  }

  /**
   * @return the track index, or -1 if no video track is found.
   */
  private int selectTrack(MediaExtractor extractor) {
    // Select the first video track we find, ignore the rest.
    int numTracks = extractor.getTrackCount();
    for (int i = 0; i < numTracks; i++) {
      MediaFormat format = extractor.getTrackFormat(i);
      String mime = format.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {
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
}
