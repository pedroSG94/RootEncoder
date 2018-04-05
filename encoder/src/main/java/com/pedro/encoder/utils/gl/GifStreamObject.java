package com.pedro.encoder.utils.gl;

import android.graphics.Bitmap;
import com.pedro.encoder.utils.gl.gif.GifDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by pedro on 22/09/17.
 */

public class GifStreamObject extends StreamObjectBase {

  private final static Logger logger = LoggerFactory.getLogger(GifStreamObject.class);

  private int numFrames;
  private Bitmap[] gifBitmaps;
  private int[] gifDelayFrames;
  private long startDelayFrame;
  private int currentGifFrame;

  public GifStreamObject() {
  }

  @Override
  public int getWidth() {
    return gifBitmaps[0].getWidth();
  }

  @Override
  public int getHeight() {
    return gifBitmaps[0].getHeight();
  }

  public void load(InputStream inputStreamGif) throws IOException {
    GifDecoder gifDecoder = new GifDecoder();
    if (gifDecoder.read(inputStreamGif, inputStreamGif.available()) == 0) {
      logger.info("read gif ok");
      numFrames = gifDecoder.getFrameCount();
      gifDelayFrames = new int[numFrames];
      gifBitmaps = new Bitmap[numFrames];
      for (int i = 0; i < numFrames; i++) {
        gifDecoder.advance();
        gifBitmaps[i] = gifDecoder.getNextFrame();
        gifDelayFrames[i] = gifDecoder.getNextDelay();
      }
      logger.info("finish load gif frames");
    } else {
      throw new RuntimeException("read gif error");
    }
  }

  @Override
  public void resize(int width, int height) {
    for (int i = 0; i < numFrames; i++) {
      gifBitmaps[i] = Bitmap.createScaledBitmap(gifBitmaps[i], width, height, false);
    }
  }

  @Override
  public void recycle() {
    for (int i = 0; i < numFrames; i++) {
      gifBitmaps[i].recycle();
    }
  }

  @Override
  public int getNumFrames() {
    return numFrames;
  }

  public int[] getGifDelayFrames() {
    return gifDelayFrames;
  }

  public Bitmap[] getGifBitmaps() {
    return gifBitmaps;
  }

  @Override
  public int updateFrame() {
    if (startDelayFrame == 0) {
      startDelayFrame = System.currentTimeMillis();
    }
    if (System.currentTimeMillis() - startDelayFrame >= gifDelayFrames[currentGifFrame]) {
      if (currentGifFrame >= numFrames - 1) {
        currentGifFrame = 0;
      } else {
        currentGifFrame++;
      }
      startDelayFrame = 0;
    }
    return currentGifFrame;
  }
}
