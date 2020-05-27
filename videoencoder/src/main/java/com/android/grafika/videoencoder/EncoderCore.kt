package com.android.grafika.videoencoder

import android.view.Surface


interface EncoderCore {

    val inputSurface: Surface?
    val pauseResumeSupported: Boolean

    /**
     * MediaRecorder automatically drains the Encoder's persistent Surface
     */
    fun drainEncoder(endStream: Boolean) = Unit

    fun start()
    fun resume()
    fun pause()
    fun stop()
    fun release()

}