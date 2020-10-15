package com.android.grafika.record.audio

import androidx.lifecycle.LifecycleOwner


class AudioRecorder {

    private lateinit var audioMediaRecorderConfig: AudioMediaRecorderConfig
    private var audioMediaRecorder: AudioMediaRecorder? = null

    var isRecording = false
        get() { return audioMediaRecorder?.isRecording ?: false }
        private set

    private var onRecordingStarted: () -> Unit = { }
    private var onRecordingPaused: () -> Unit = { }
    private var onRecordingResumed: () -> Unit = { }
    private var onRecordingStopped: () -> Unit = { }
    private var onRecordingFailed: (e: Throwable) -> Unit = { }

    fun config(block: () -> AudioMediaRecorderConfig) = apply {
        this.audioMediaRecorderConfig = block()
    }

    fun startRecording(lifecycleOwner: LifecycleOwner) {
        audioMediaRecorder?.stop()
        audioMediaRecorder?.release()
        audioMediaRecorder = AudioMediaRecorder(audioMediaRecorderConfig)
                .bindToLifecycleOwner(lifecycleOwner)
                .apply {
                    this.onRecordingStarted = this@AudioRecorder.onRecordingStarted
                    this.onRecordingPaused = this@AudioRecorder.onRecordingPaused
                    this.onRecordingResumed = this@AudioRecorder.onRecordingResumed
                    this.onRecordingStopped = this@AudioRecorder.onRecordingStopped
                    this.onRecordingFailed = this@AudioRecorder.onRecordingFailed
                }
                .prepare()
        audioMediaRecorder?.start()
    }

    fun stopRecording() {
        audioMediaRecorder?.stop()
        audioMediaRecorder?.release()
        audioMediaRecorder = null
    }

    fun onRecordingStarted(block: () -> Unit) = apply {
        this.onRecordingStarted = block
    }

    fun onRecordingPaused(block: () -> Unit) = apply {
        this.onRecordingPaused = block
    }

    fun onRecordingResumed(block: () -> Unit) = apply {
        this.onRecordingResumed = block
    }

    fun onRecordingStopped(block: () -> Unit) = apply {
        this.onRecordingStopped = block
    }

    fun onRecordingFailed(block: (e: Throwable) -> Unit) = apply {
        this.onRecordingFailed = block
    }
}