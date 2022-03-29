package com.pedro.rtplibrary.util.sources

/**
 * Created by pedro on 29/3/22.
 */
class NoSource {

  private var running = false

  fun start() {
    running = true
  }

  fun stop() {
    running = false
  }

  fun isRunning(): Boolean = running
}