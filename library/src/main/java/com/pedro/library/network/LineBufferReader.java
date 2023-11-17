/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.library.network;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Class for reading {@code /proc/net/xt_qtaguid/stats} line by line with a small,
 * reusable byte buffer.
 */

@Deprecated
class LineBufferReader {

  private byte[] mFileBuffer;
  private FileInputStream mInputStream;
  private int mFileBufIndex;
  private int mBytesInBuffer;

  public LineBufferReader() {
    mFileBuffer = new byte[512];
  }

  /**
   * Sets the FileInputStream for reading.
   *
   * @param is The FileInputStream to set.
   */
  public void setFileStream(FileInputStream is) {
    mInputStream = is;
    mBytesInBuffer = 0;
    mFileBufIndex = 0;
  }

  /**
   * @param lineBuffer The buffer to fill with the current line.
   * @return The index in the buffer at which the line terminates.
   */
  public int readLine(byte[] lineBuffer) throws IOException {
    if (mFileBufIndex >= mBytesInBuffer) {
      mBytesInBuffer = mInputStream.read(mFileBuffer);
      mFileBufIndex = 0;
    }
    int i;
    for (i = 0; mBytesInBuffer != -1 && i < lineBuffer.length && mFileBuffer[mFileBufIndex] != '\n';
        i++) {
      lineBuffer[i] = mFileBuffer[mFileBufIndex];
      mFileBufIndex++;
      if (mFileBufIndex >= mBytesInBuffer) {
        mBytesInBuffer = mInputStream.read(mFileBuffer);
        mFileBufIndex = 0;
      }
    }
    // Move past the newline character.
    mFileBufIndex++;
    // If there are no more bytes to be read into the buffer,
    // we have reached the end of this file. Exit.
    if (mBytesInBuffer == -1) {
      return -1;
    }
    return i;
  }

  /**
   * Skips a line in the current file stream.
   */
  public void skipLine() throws IOException {
    if (mFileBufIndex >= mBytesInBuffer) {
      mBytesInBuffer = mInputStream.read(mFileBuffer);
      mFileBufIndex = 0;
    }
    for (int i = 0; mBytesInBuffer != -1 && mFileBuffer[mFileBufIndex] != '\n'; i++) {
      mFileBufIndex++;
      if (mFileBufIndex >= mBytesInBuffer) {
        mBytesInBuffer = mInputStream.read(mFileBuffer);
        mFileBufIndex = 0;
      }
    }
    // Move past the newline character.
    mFileBufIndex++;
  }
}
