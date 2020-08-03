package com.android.grafika.videoencoder.mediarecorder

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import com.android.grafika.videoencoder.Recorder
import com.android.grafika.videoencoder.muxer.video.VideoEncoderConfig


class MediaVideoRecorder : Recorder {

    override var isRecording: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun updateTextureId(textureId: Int) {
        TODO("not implemented")
    }

    override fun frameAvailable(surfaceTexture: SurfaceTexture) {
        TODO("not implemented")
    }

    override fun startRecording(config: VideoEncoderConfig?) {
        TODO("not implemented")
    }

    override fun resumeRecording() {
        TODO("not implemented")
    }

    override fun updateSharedContext(eglContext: EGLContext) {
        TODO("not implemented")
    }

    override fun pauseRecording() {
        TODO("not implemented")
    }

    override fun stopRecording() {
        TODO("not implemented")
    }
}