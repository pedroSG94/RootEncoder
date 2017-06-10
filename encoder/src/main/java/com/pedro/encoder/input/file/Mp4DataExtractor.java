package com.pedro.encoder.input.file;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Extract frames from an MP4 using MediaExtractor, MediaCodec, and GLES.  Put a .mp4 file
 * in "/sdcard/source.mp4" and look for output files named "/sdcard/frame-XX.png".
 * <p>
 * This uses various features first available in Android "Jellybean" 4.1 (API 16).
 * <p>
 * (This was derived from bits and pieces of CTS tests, and is packaged as such, but is not
 * currently part of CTS.)
 */

/**
 * Created by pedro on 9/06/17.
 */

public class Mp4DataExtractor {

  private final String TAG = "Mp4DataExtractor";

  private File file;

  /**
   * run test
   */
  public void runTest() {
    try {
      setFile(Environment.getExternalStorageDirectory() + "/video.mp4");
      extractMpegFrames();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void extractMpegFrames() throws IOException {
    MediaCodec decoder;
    OutputSurface outputSurface;
    MediaExtractor extractor;

    // The MediaExtractor error messages aren't very useful.  Check to see if the input
    // file exists so we can throw a better one if it's not there.
    if (!file.canRead()) {
      throw new FileNotFoundException("Unable to read " + file);
    }

    extractor = new MediaExtractor();
    extractor.setDataSource(file.toString());
    int trackIndex = selectTrack(extractor);
    if (trackIndex < 0) {
      throw new RuntimeException("No video track found in " + file);
    }
    extractor.selectTrack(trackIndex);

    MediaFormat format = extractor.getTrackFormat(trackIndex);
    Log.d(TAG,
        "Video size is " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" + format.getInteger(
            MediaFormat.KEY_HEIGHT));

    // Could use width/height from the MediaFormat to get full-size frames.
    outputSurface = new OutputSurface(format.getInteger(MediaFormat.KEY_WIDTH),
        format.getInteger(MediaFormat.KEY_HEIGHT));

    // Create a MediaCodec decoder, and configure it with the MediaFormat from the
    // extractor.  It's very important to use the format from the extractor because
    // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
    String mime = format.getString(MediaFormat.KEY_MIME);
    decoder = MediaCodec.createDecoderByType(mime);
    decoder.configure(format, outputSurface.getSurface(), null, 0);
    decoder.start();

    doExtract(extractor, decoder, outputSurface);
    // release everything we grabbed
    if (outputSurface != null) {
      outputSurface.release();
      outputSurface = null;
    }
    if (decoder != null) {
      decoder.stop();
      decoder.release();
      decoder = null;
    }
    if (extractor != null) {
      extractor.release();
      extractor = null;
    }
  }

  /**
   * Selects the video track, if any.
   *
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

  /**
   * Work loop.
   */
  private void doExtract(MediaExtractor extractor, MediaCodec decoder,
      OutputSurface outputSurface) throws IOException {
    final int TIMEOUT_USEC = 10000;
    ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    int inputChunk = 0;

    boolean outputDone = false;
    boolean inputDone = false;
    while (!outputDone) {

      // Feed more data to the decoder.
      if (!inputDone) {
        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufIndex >= 0) {
          ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
          // Read the sample data into the ByteBuffer.  This neither respects nor
          // updates inputBuf's position, limit, etc.
          int chunkSize = extractor.readSampleData(inputBuf, 0);
          if (chunkSize < 0) {
            // End of stream -- send empty frame with EOS flag set.
            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            inputDone = true;
          } else {
            long presentationTimeUs = extractor.getSampleTime();
            decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, presentationTimeUs, 0 /*flags*/);
            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" + chunkSize);
            inputChunk++;
            extractor.advance();
          }
        }
      }

      if (!outputDone) {
        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
        if (decoderStatus >= 0) {
          if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            outputDone = true;
          }

          boolean doRender = (info.size != 0);
          // As soon as we call releaseOutputBuffer, the buffer will be forwarded
          // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
          // that the texture will be available before the call returns, so we
          // need to wait for the onFrameAvailable callback to fire.
          decoder.releaseOutputBuffer(decoderStatus, doRender);
          if (doRender) {
            outputSurface.awaitNewImage();
            outputSurface.drawImage(true);
          }
        }
      }
    }
  }

  public void setFile(String absolutePath) throws FileNotFoundException {
    File file = new File(absolutePath);
    if (file.canRead()) {
      this.file = file;
    } else {
      throw new FileNotFoundException("Unable to read " + file);
    }
  }

  public void setFile(File file) throws FileNotFoundException {
    if (file.canRead()) {
      this.file = file;
    } else {
      throw new FileNotFoundException("Unable to read " + file);
    }
  }
}
