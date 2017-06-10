package com.pedro.encoder.input.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by pedro on 9/06/17.
 * Get ACC data from a mp4 file
 */

public class AudioDecoder {

  private final String TAG = "AudioDecoder";

  private MediaExtractor audioExtractor;
  private MediaCodec audioDecoder;
  private String filePath;

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

  public void prepareAudio() {
    try {
      audioExtractor = new MediaExtractor();
      audioExtractor.setDataSource(filePath);
      MediaFormat audioFormat = audioExtractor.getTrackFormat(selectTrack(audioExtractor));
      audioDecoder = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
      audioDecoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    } catch (IOException e) {
      Log.e(TAG, "Error preparing audio: ", e);
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
}
