package com.android.grafika.videoencoder.muxer

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import android.util.Log
import com.android.grafika.core.Utils.TAG
import com.android.grafika.videoencoder.EncoderStateHandler
import com.android.grafika.videoencoder.Recorder
import com.android.grafika.videoencoder.muxer.audio.AudioEncoder
import com.android.grafika.videoencoder.muxer.audio.AudioEncoderConfig
import com.android.grafika.videoencoder.muxer.video.VideoEncoder
import com.android.grafika.videoencoder.muxer.video.VideoEncoderConfig


class MuxerRecorder(
    private val encoderStateHandler: EncoderStateHandler
) : Recorder {

    private val muxer = AndroidMuxer("/data/user/0/com.google.grafika/files/recording.mp4")
    private val videoEncoder = VideoEncoder(muxer, encoderStateHandler)
    private val audioEncoder = AudioEncoder(muxer, AudioEncoderConfig(1, 44100, 96 * 1000))

    override var isRecording: Boolean = false

    override fun updateTextureId(textureId: Int) {
        videoEncoder.updateTextureId(textureId)
    }

    override fun frameAvailable(surfaceTexture: SurfaceTexture) {
        videoEncoder.frameAvailable(surfaceTexture)
    }

    override fun startRecording(config: VideoEncoderConfig?){
        config?.let {
            videoEncoder.startRecording(config)
            audioEncoder.startRecording()
        } ?: Log.e(TAG, "config == null")
    }

    override fun resumeRecording() {
        Log.i(TAG, "resume recording not implemented!")
    }

    override fun pauseRecording() {
        Log.i(TAG, "resume recording not implemented!")
    }

    override fun stopRecording() {
        videoEncoder.stopRecording()
        audioEncoder.stopRecording()
    }

    override fun updateSharedContext(eglContext: EGLContext) {
        videoEncoder.updateSharedContext(eglContext)
    }
}