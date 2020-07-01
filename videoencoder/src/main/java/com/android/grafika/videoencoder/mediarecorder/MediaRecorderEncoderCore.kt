package com.android.grafika.videoencoder.mediarecorder

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.util.Preconditions
import com.android.grafika.videoencoder.EncoderCore
import com.android.grafika.videoencoder.EncoderStateCallback
import com.android.grafika.videoencoder.VideoEncoderConfig
import java.io.IOException
import java.lang.IllegalStateException
import java.lang.RuntimeException


@SuppressLint("RestrictedApi")
class MediaRecorderEncoderCore (
        config: VideoEncoderConfig,
        private val encoderStateCallback: EncoderStateCallback = EncoderStateCallback.EMPTY
) : EncoderCore {

    override val inputSurface: Surface? = config.inputSurface
    override val pauseResumeSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private val mediaRecorder = MediaRecorder()
    private var isRecording: Boolean = false

    init {
        Preconditions.checkArgument(inputSurface != null, "inputSurface == null")
        mediaRecorder.setAudioSource(config.audioSource)
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setOutputFile(config.outputFile!!.absolutePath)
        mediaRecorder.setVideoEncodingBitRate(config.videoBitRate)
        mediaRecorder.setVideoFrameRate(config.frameRate)
        if (config.audioBitRate != VideoEncoderConfig.NULL_VALUE) {
            mediaRecorder.setAudioEncodingBitRate(config.audioBitRate)
        }
        mediaRecorder.setVideoSize(config.width, config.height)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setAudioEncoder(config.audioEncoder)
        mediaRecorder.setInputSurface(inputSurface!!)
        mediaRecorder.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> stop()
            }
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun prepare(): MediaRecorderEncoderCore = apply {
        mediaRecorder.prepare()
    }

    override fun release() {
        mediaRecorder.reset()
        mediaRecorder.release()
        isRecording = false
    }

    override fun start() {
        if (isRecording.not()) {
            try {
                mediaRecorder.start()
                isRecording = true
                encoderStateCallback.onRecordingStarted()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't START MediaRecorderEncoderCore", e)
                encoderStateCallback.onRecordingFailed(e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun resume() {
        if (pauseResumeSupported) {
            try {
                mediaRecorder.resume()
                isRecording = true
                encoderStateCallback.onRecordingResumed()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't RESUME MediaRecorderEncoderCore", e)
                encoderStateCallback.onRecordingFailed(e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun pause() {
        if (pauseResumeSupported) {
            try {
                mediaRecorder.pause()
                isRecording = false
                encoderStateCallback.onRecordingPaused()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't PAUSE MediaRecorderEncoderCore", e)
                encoderStateCallback.onRecordingFailed(e)
            }
        } else {
            stop()
        }
    }

    override fun stop() {
        if (isRecording) {
            try {
                mediaRecorder.stop()
                isRecording = false
                encoderStateCallback.onRecordingStopped()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't STOP MediaRecorderEncoderCore", e)
                encoderStateCallback.onRecordingFailed(e)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Can't STOP MediaRecorderEncoderCore", e)
                encoderStateCallback.onRecordingFailed(e)
            }
        }
    }

    companion object {
        private val TAG = MediaRecorderEncoderCore::class.java.simpleName
    }
}