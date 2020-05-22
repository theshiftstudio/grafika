package com.android.grafika.videoencoder.mediarecorder

import android.media.MediaRecorder
import android.nfc.Tag
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import com.android.grafika.videoencoder.EncoderCore
import java.io.File
import java.io.IOException
import java.lang.RuntimeException


class MediaRecorderEncoderCore (
        width: Int,
        height: Int,
        videoBitRate: Int,
        audioBitRate: Int,
        frameRate: Int,
        outputFile: File,
        override val inputSurface: Surface
) : EncoderCore {

    override val pauseResumeSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private val mediaRecorder = MediaRecorder()
    private var isRecording: Boolean = false

    init {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setOutputFile(outputFile.absolutePath)
        mediaRecorder.setVideoEncodingBitRate(videoBitRate)
        mediaRecorder.setVideoFrameRate(frameRate)
        mediaRecorder.setAudioEncodingBitRate(audioBitRate)
        mediaRecorder.setVideoSize(width, height)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setInputSurface(inputSurface)
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
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't START MediaRecorderEncoderCore", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun resume() {
        if (pauseResumeSupported) {
            try {
                mediaRecorder.resume()
                isRecording = true
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't RESUME MediaRecorderEncoderCore", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun pause() {
        if (pauseResumeSupported) {
            try {
                mediaRecorder.pause()
                isRecording = false
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't PAUSE MediaRecorderEncoderCore", e)
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
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't STOP MediaRecorderEncoderCore", e)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Can't STOP MediaRecorderEncoderCore", e)
            }
        }
    }

    companion object {
        private val TAG = MediaRecorderEncoderCore::class.java.simpleName
    }
}