/*
 * Copyright (C) 2026 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.encoder.utils.yuv;

import java.nio.ByteBuffer;

/**
 * Tiled NV12 layout of the Samsung MFC, used by the OMX.SEC encoders of the Exynos devices before
 * API 21. They ask for a color format like YUV420Planar but their preprocessor copies the buffer to
 * the MFC without converting it, so the frame has to be written already tiled.
 *
 * A plane is stored as tiles of 64x32 bytes (2048 bytes each) instead of packed rows and the tiles
 * are not in raster order: they are grouped in blocks of 4 tiles wide by 2 tiles tall and inside
 * each group pairs of tiles zig zag between both tile rows to spread them over the memory banks.
 * When the number of tile rows is odd the last one has no pair and is stored in raster order.
 *
 * A frame is the luma plane, a grid of width x height bytes, followed by the chroma plane aligned
 * to 8KB, a grid of width x (height / 2) bytes with U and V interleaved. Both are tiled.
 *
 * Layout of a 640x480 frame:
 *
 * luma:   0      .. 307200   150 tiles, 10 per row and 15 rows
 * (hole)  307200 .. 311296   luma size aligned to 8KB
 * chroma: 311296 .. 475136   80 tiles, 10 per row and 8 rows (the last 16 rows are padding)
 *
 * Verified against a Samsung EK-KC120L (Exynos 4412, Android 4.1.2) reconstructing the frames of a
 * stream produced by OMX.SEC.avc.enc. See issue #2158.
 */
public class SamsungTiledUtil {

  private static final int TILE_WIDTH = 64;
  private static final int TILE_HEIGHT = 32;
  private static final int TILE_SIZE = TILE_WIDTH * TILE_HEIGHT;
  private static final int PLANE_ALIGNMENT = 8 * 1024;

  /**
   * The tiles of a row are paired to zig zag between two tile rows so an even number of tiles per
   * row is needed. Only the width is restricted, a height that doesn't fill the last tile row is
   * valid because the rest of the tile is padding.
   */
  public static boolean isSupported(int width, int height) {
    return width % (TILE_WIDTH * 2) == 0 && height % 2 == 0;
  }

  /**
   * @return the size of a tiled frame, the size the encoder reads for that resolution.
   */
  public static int getSize(int width, int height) {
    return align(getPlaneSize(width, height), PLANE_ALIGNMENT) + getPlaneSize(width, height / 2);
  }

  /**
   * Copy a packed YUV420 frame into the buffer using the tiled layout. The buffer position is not
   * modified if the copy can't be done.
   *
   * @param semiPlanar true if the frame chroma is a single NV12 plane, false if it is I420.
   * @return bytes filled in the buffer or -1 if the frame or the buffer are too small.
   */
  public static int copy(ByteBuffer byteBuffer, byte[] buffer, int offset, int size, int width,
      int height, boolean semiPlanar) {
    int lumaSize = width * height;
    int filled = getSize(width, height);
    if (size < lumaSize + lumaSize / 2 || byteBuffer.remaining() < filled) return -1;
    int start = byteBuffer.position();
    copyTiled(byteBuffer, start, buffer, offset, width, height);
    int chromaStart = start + align(getPlaneSize(width, height), PLANE_ALIGNMENT);
    if (semiPlanar) { //the chroma is already interleaved, it is tiled like the luma
      copyTiled(byteBuffer, chromaStart, buffer, offset + lumaSize, width, height / 2);
    } else {
      copyTiledInterleaving(byteBuffer, chromaStart, buffer, offset + lumaSize, width, height / 2);
    }
    byteBuffer.position(start + filled);
    return filled;
  }

  /**
   * Copy a packed plane of width x height bytes writing it tiled. The rows of the last tile row
   * that the plane doesn't reach are left untouched, the encoder ignores them.
   */
  private static void copyTiled(ByteBuffer byteBuffer, int start, byte[] buffer, int offset,
      int width, int height) {
    int tilesPerRow = width / TILE_WIDTH;
    int tileRows = tilesOf(height, TILE_HEIGHT);
    for (int tileY = 0; tileY < tileRows; tileY++) {
      int rows = Math.min(TILE_HEIGHT, height - tileY * TILE_HEIGHT);
      for (int tileX = 0; tileX < tilesPerRow; tileX++) {
        int tile = start + tileOffset(tileX, tileY, tilesPerRow, tileRows);
        for (int row = 0; row < rows; row++) {
          byteBuffer.position(tile + row * TILE_WIDTH);
          int line = (tileY * TILE_HEIGHT + row) * width + tileX * TILE_WIDTH;
          byteBuffer.put(buffer, offset + line, TILE_WIDTH);
        }
      }
    }
  }

  /**
   * Copy the U and V planes of an I420 frame writing them tiled as a single interleaved plane.
   */
  private static void copyTiledInterleaving(ByteBuffer byteBuffer, int start, byte[] buffer,
      int offset, int width, int height) {
    int tilesPerRow = width / TILE_WIDTH;
    int tileRows = tilesOf(height, TILE_HEIGHT);
    int chromaWidth = width / 2;
    //the V plane goes right after the U plane
    int planeSize = chromaWidth * height;
    byte[] line = new byte[TILE_WIDTH];
    for (int tileY = 0; tileY < tileRows; tileY++) {
      int rows = Math.min(TILE_HEIGHT, height - tileY * TILE_HEIGHT);
      for (int tileX = 0; tileX < tilesPerRow; tileX++) {
        int tile = start + tileOffset(tileX, tileY, tilesPerRow, tileRows);
        for (int row = 0; row < rows; row++) {
          int u = offset + (tileY * TILE_HEIGHT + row) * chromaWidth + tileX * (TILE_WIDTH / 2);
          for (int i = 0; i < TILE_WIDTH; i += 2) {
            line[i] = buffer[u + i / 2];
            line[i + 1] = buffer[u + i / 2 + planeSize];
          }
          byteBuffer.position(tile + row * TILE_WIDTH);
          byteBuffer.put(line, 0, TILE_WIDTH);
        }
      }
    }
  }

  /**
   * @return the offset of a tile inside a tiled plane.
   */
  private static int tileOffset(int tileX, int tileY, int tilesPerRow, int tileRows) {
    int group = (tileY / 2) * 2 * tilesPerRow;
    //an odd number of tile rows leaves the last one without pair, it is stored in raster order
    if (tileY == tileRows - 1 && tileRows % 2 != 0) return (group + tileX) * TILE_SIZE;
    //pairs of tiles alternate between both tile rows of the group every two pairs
    int pairs = tileX / 2;
    int pair = pairs * 2 + (pairs % 2 == tileY % 2 ? 0 : 1);
    return (group + pair * 2 + tileX % 2) * TILE_SIZE;
  }

  private static int getPlaneSize(int width, int height) {
    return tilesOf(width, TILE_WIDTH) * tilesOf(height, TILE_HEIGHT) * TILE_SIZE;
  }

  private static int tilesOf(int value, int tile) {
    return (value + tile - 1) / tile;
  }

  private static int align(int value, int alignment) {
    return (value + alignment - 1) / alignment * alignment;
  }
}
