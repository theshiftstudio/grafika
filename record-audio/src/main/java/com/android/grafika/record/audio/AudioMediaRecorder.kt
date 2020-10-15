package com.android.grafika.record.audio

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.IOException
import java.lang.IllegalStateException
import java.lang.RuntimeException


@SuppressLint("RestrictedApi")
internal class AudioMediaRecorder (
        config: AudioMediaRecorderConfig
) : LifecycleEventObserver {

    private val pauseResumeSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private val mediaRecorder = MediaRecorder()
    internal var isRecording: Boolean = false

    var onRecordingStarted: () -> Unit = { }
    var onRecordingPaused: () -> Unit = { }
    var onRecordingResumed: () -> Unit = { }
    var onRecordingStopped: () -> Unit = { }
    var onRecordingFailed: (e: Throwable) -> Unit = { }

    init {
        mediaRecorder.setAudioSource(config.audioSource)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setOutputFile(config.outputFile!!.absolutePath)
        mediaRecorder.setAudioEncodingBitRate(config.audioBitRate)
        mediaRecorder.setAudioSamplingRate(config.audioSamplingRate)
        mediaRecorder.setAudioEncoder(config.audioEncoder)
        mediaRecorder.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> stop()
            }
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun prepare(): AudioMediaRecorder = apply {
        mediaRecorder.prepare()
    }

    fun release() {
        mediaRecorder.release()
        isRecording = false
    }

    fun start() = apply {
        if (isRecording.not()) {
            try {
                mediaRecorder.start()
                isRecording = true
                onRecordingStarted()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't START AudioMediaRecorder", e)
                onRecordingFailed(e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun resume() {
        if (pauseResumeSupported && isRecording.not()) {
            try {
                mediaRecorder.resume()
                isRecording = true
                onRecordingResumed()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't RESUME AudioMediaRecorder", e)
                onRecordingFailed(e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun pause() {
        if (pauseResumeSupported) {
            try {
                mediaRecorder.pause()
                isRecording = false
                onRecordingPaused()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't PAUSE AudioMediaRecorder", e)
                onRecordingFailed(e)
            }
        } else {
            stop()
        }
    }

    fun stop() {
        if (isRecording) {
            try {
                mediaRecorder.stop()
                isRecording = false
                onRecordingStopped()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't STOP AudioMediaRecorder", e)
                onRecordingFailed(e)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Can't STOP AudioMediaRecorder", e)
                onRecordingFailed(e)
            }
        }
    }

    fun bindToLifecycleOwner(owner: LifecycleOwner) = apply {
        owner.lifecycle.addObserver(this)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_DESTROY -> {
                stop()
                release()
            }
            else -> Unit
        }
    }

    companion object {
        private val TAG = AudioMediaRecorder::class.java.simpleName
    }
}