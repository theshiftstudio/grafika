package com.android.grafika.videoencoder

interface EncoderStateCallback {
    fun onRecordingStarted()
    fun onRecordingResumed()
    fun onRecordingPaused()
    fun onRecordingStopped()
    fun onRecordingFailed(t: Throwable)

    companion object {
        val EMPTY = object : EncoderStateCallback {
            override fun onRecordingStarted()  = Unit
            override fun onRecordingResumed()  = Unit
            override fun onRecordingPaused()  = Unit
            override fun onRecordingStopped()  = Unit
            override fun onRecordingFailed(t: Throwable)  = Unit
        }
    }
}