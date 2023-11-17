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

import android.os.StrictMode;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Class for parsing total number of downloaded bytes
 * from {@code /proc/net/xt_qtaguid/stats}.
 */

@Deprecated
class QTagTxParser {
  private static final String TAG = "QTagParser";
  private static final String QTAGUID_UID_STATS = "/proc/net/xt_qtaguid/stats";

  private static final ThreadLocal<byte[]> sLineBuffer = new ThreadLocal<byte[]>() {
    @Override
    public byte[] initialValue() {
      return new byte[512];
    }
  };

  private static long sPreviousBytes = -1;
  private static LineBufferReader sStatsReader = new LineBufferReader();
  private static ByteArrayScanner sScanner = new ByteArrayScanner();
  public static QTagTxParser sInstance;

  public static synchronized QTagTxParser getInstance() {
    if (sInstance == null) {
      sInstance = new QTagTxParser(QTAGUID_UID_STATS);
    }
    return sInstance;
  }

  private String mPath;

  // @VisibleForTesting
  public QTagTxParser(String path) {
    mPath = path;
  }

  /**
   * Reads the qtaguid file and returns a difference from the previous read.
   *
   * @param uid The target uid to read bytes downloaded for.
   * @return The difference between the current number of bytes downloaded and
   */
  public long parseDataUsageForUidAndTag(int uid) {
    // The format of each line is
    // idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes
    // (There are many more fields but we are not interested in them)
    // For us parts: 1, 2, 3 are to see if the line is relevant
    // and part 5 is the received bytes
    // (part numbers start from 0)

    // Permit disk reads here, as /proc/net/xt_qtaguid/stats isn't really "on
    // disk" and should be fast.
    StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();
    try {
      long tagTxBytes = 0;

      FileInputStream fis = new FileInputStream(mPath);
      sStatsReader.setFileStream(fis);
      byte[] buffer = sLineBuffer.get();

      try {
        int length;
        sStatsReader.skipLine(); // skip first line (headers)

        int line = 2;
        while ((length = sStatsReader.readLine(buffer)) != -1) {
          try {

            // Content is arranged in terms of:
            // idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets rx_tcp_bytes
            // rx_tcp_packets rx_udp_bytes rx_udp_packets rx_other_bytes rx_other_packets tx_tcp_bytes tx_tcp_packets
            // tx_udp_bytes tx_udp_packets tx_other_bytes tx_other_packets

            // The ones we're interested in are:
            // idx - ignore
            // interface, filter out local interface ("lo")
            // tag - ignore
            // uid_tag_int, match it with the UID of interest
            // cnt_set - ignore
            // rx_bytes

            sScanner.reset(buffer, length);
            sScanner.useDelimiter(' ');

            sScanner.skip();
            if (sScanner.nextStringEquals("lo")) {
              continue;
            }
            sScanner.skip();
            if (sScanner.nextInt() != uid) {
              continue;
            }
            sScanner.skip();
            sScanner.skip();
            sScanner.skip();
            int txBytes = sScanner.nextInt();
            tagTxBytes += txBytes;
            line++;
            // If the line is incorrectly formatted, ignore the line.
          } catch (NumberFormatException e) {
            Log.e(TAG, "Cannot parse byte count at line" + line + ".");
            continue;
          } catch (NoSuchElementException e) {
            Log.e(TAG, "Invalid number of tokens on line " + line + ".");
            continue;
          }
        }
      } finally {
        fis.close();
      }

      if (sPreviousBytes == -1) {
        sPreviousBytes = tagTxBytes;
        return -1;
      }
      long diff = tagTxBytes - sPreviousBytes;
      sPreviousBytes = tagTxBytes;
      return diff;
    } catch (IOException e) {
      Log.e(TAG,
          "Error reading from /proc/net/xt_qtaguid/stats. Please check if this file exists.");
    } finally {
      StrictMode.setThreadPolicy(savedPolicy);
    }

    // Return -1 upon error.
    return -1;
  }
}