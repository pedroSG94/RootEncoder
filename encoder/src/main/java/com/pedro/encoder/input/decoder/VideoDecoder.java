package com.pedro.encoder.input.decoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import com.pedro.encoder.video.FormatVideoEncoder;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by pedro on 9/06/17.
 * Get H264 data from a mp4 file
 */

public class VideoDecoder {

  private final String TAG = "VideoDecoder";

  private MediaExtractor videoExtractor;
  private MediaCodec videoDecoder;

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

  public void prepareVideo() {
    try {
      videoExtractor = new MediaExtractor();
      videoExtractor.setDataSource(filePath);
      MediaFormat videoFormat = videoExtractor.getTrackFormat(selectTrack(videoExtractor));
      videoDecoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
      videoDecoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    } catch (IOException e) {
      Log.e(TAG, "Error preparing video: ", e);
    }
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
}
