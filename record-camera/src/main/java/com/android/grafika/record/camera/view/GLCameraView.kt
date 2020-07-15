package com.android.grafika.record.camera.view

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RadioGroup
import androidx.camera.core.CameraSelector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.android.grafika.record.camera.GLCameraXModule
import com.android.grafika.record.camera.R
import com.android.grafika.videoencoder.VideoEncoderConfig
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_AUDIO_ENCODER
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_AUDIO_SOURCE
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_FRAME_RATE
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_VIDEO_BIT_RATE
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_VIDEO_HEIGHT
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_VIDEO_WIDTH
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.NULL_VALUE
import java.io.File


class GLCameraView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), LifecycleEventObserver {

    private var audioDebugger: View? = null
    private val audioDebuggerEncoder by lazy {
        audioDebugger?.findViewById<RadioGroup?>(R.id.audio_debugger)
    }
    private val audioDebuggerBitRate by lazy {
        audioDebugger?.findViewById<RadioGroup?>(R.id.audio_debugger_bitrate)
    }

    @CameraSelector.LensFacing
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    val isRecording
        get() = previewView.isRecording

    val previewWidth
        get() = previewView.width

    val previewHeight
        get() = previewView.height

    var videoBitRate
        get() = encoderConfig.videoBitRate
        set(value) { encoderConfig.videoBitRate { value } }

    var audioBitRate
        get() = encoderConfig.audioBitRate
        set(value) { encoderConfig.audioBitRate { value } }

    var audioEncoder
        get() = encoderConfig.audioEncoder
        set(value) { encoderConfig.audioEncoder { value } }

    private val cameraXModule = GLCameraXModule(this)
    private lateinit var encoderType : EncoderType
    private val previewView by lazy {
        GLCameraSurfacePreviewView(context, encoderType).apply {
            layoutParams = LayoutParams(videoPreferredWidth, videoPreferredHeight)
        }
    }
    private var videoPreferredWidth: Int = DEFAULT_VIDEO_WIDTH
    private var videoPreferredHeight: Int = DEFAULT_VIDEO_HEIGHT
    private val encoderConfig = VideoEncoderConfig(VideoEncoderConfig.DEFAULT)


    private val audioSourcesMap = mapOf(
            Pair(0, MediaRecorder.AudioSource.DEFAULT),
            Pair(1, MediaRecorder.AudioSource.MIC),
            Pair(2, MediaRecorder.AudioSource.VOICE_UPLINK),
            Pair(3, MediaRecorder.AudioSource.VOICE_DOWNLINK),
            Pair(4, MediaRecorder.AudioSource.VOICE_CALL),
            Pair(5, MediaRecorder.AudioSource.CAMCORDER),
            Pair(6, MediaRecorder.AudioSource.VOICE_RECOGNITION),
            Pair(7, MediaRecorder.AudioSource.VOICE_COMMUNICATION),
            Pair(9, safeAudioSourceUnprocessed()),
            Pair(10, safeAudioSourceVoicePerformance())
    )

    private val audioEncodersMap = mapOf(
            Pair(0, MediaRecorder.AudioEncoder.DEFAULT),
            Pair(1, MediaRecorder.AudioEncoder.AMR_NB),
            Pair(2, MediaRecorder.AudioEncoder.AMR_WB),
            Pair(3, MediaRecorder.AudioEncoder.AAC),
            Pair(4, MediaRecorder.AudioEncoder.HE_AAC),
            Pair(5, MediaRecorder.AudioEncoder.AAC_ELD),
            Pair(6, MediaRecorder.AudioEncoder.VORBIS),
            Pair(7, safeAudioEncoderOpus())
    )

    init {
        attrs?.let {
            val array = context.obtainStyledAttributes(it, R.styleable.GLCameraView)
            videoPreferredWidth = array.getInteger(R.styleable.GLCameraView_videoPreferredWidth, DEFAULT_VIDEO_WIDTH)
            videoPreferredHeight = array.getInteger(R.styleable.GLCameraView_videoPreferredHeight, DEFAULT_VIDEO_HEIGHT)
            encoderConfig
                    .videoBitRate {
                        array.getInteger(R.styleable.GLCameraView_videoBitRate, DEFAULT_VIDEO_BIT_RATE)
                    }
                    .frameRate {
                        array.getInteger(R.styleable.GLCameraView_videoFrameRate, DEFAULT_FRAME_RATE)
                    }
                    .audioBitRate {
                        array.getInteger(R.styleable.GLCameraView_audioBitRate, NULL_VALUE)
                    }
                    .audioSource {
                        array.getInt(R.styleable.GLCameraView_audioSource, DEFAULT_AUDIO_SOURCE).let {
                            audioSourcesMap[it] ?: DEFAULT_AUDIO_SOURCE
                        }
                    }
                    .audioEncoder {
                        array.getInt(R.styleable.GLCameraView_audioEncoder, DEFAULT_AUDIO_ENCODER).let {
                            audioEncodersMap[it] ?: DEFAULT_AUDIO_ENCODER
                        }
                    }
            audioDebugger = array.getBoolean(R.styleable.GLCameraView_debugAudio, false)
                    .takeIf { it }
                    ?.let {
                        LayoutInflater.from(context).inflate(R.layout.view_audio_debugger, this, false)
                    }.apply {
                        audioDebuggerEncoder?.setOnCheckedChangeListener { _, checkedId ->
                            when (checkedId) {
                                R.id.audio_debuger_encoder_aac -> audioEncoder = MediaRecorder.AudioEncoder.AAC
                                R.id.audio_debuger_encoder_aac_he -> audioEncoder = MediaRecorder.AudioEncoder.HE_AAC
                                R.id.audio_debuger_encoder_aac_eld -> audioEncoder = MediaRecorder.AudioEncoder.AAC_ELD
                            }
                        }
                        audioDebuggerBitRate?.setOnCheckedChangeListener { _, checkedId ->
                            when (checkedId) {
                                R.id.audio_debugger_bitrate_8khz -> audioBitRate = 8 * 1000
                                R.id.audio_debugger_bitrate_24khz -> audioBitRate = 24 * 1000
                                R.id.audio_debugger_bitrate_48khz -> audioBitRate = 48 * 1000
                            }
                        }
                    }
            encoderType = array.getInt(R.styleable.GLCameraView_encoderType, EncoderType.MEDIA_RECORDER.attrId).let {
                when (it) {
                    EncoderType.MUXER.attrId -> EncoderType.MUXER
                    EncoderType.MEDIA_RECORDER.attrId -> EncoderType.MEDIA_RECORDER
                    else -> EncoderType.MEDIA_RECORDER
                }
            }
            array.recycle()
        }
        this.addView(previewView, 0)
        audioDebugger?.let { addView(it) }
    }

    fun unbindUseCases() {
        if (isRecording) {
            stopRecording()
        }
        cameraXModule.unbindUseCases {
            previewView.cameraAvailable = false
        }
    }

    fun bindLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
        previewView.onSurfaceTextureAvailable = {
            cameraXModule.bindToLifecycle(lifecycleOwner, previewView)
            previewView.cameraAvailable = true
        }
        previewView.surfaceTexture?.let {
            previewView.onSurfaceTextureAvailable(it)
        }
    }

    fun startRecording(outputFile: File) =
            previewView.startRecording(encoderConfig.outputFile { outputFile })
    fun stopRecording() = previewView.stopRecording()

    fun toggleRecording(outputFile: File) {
        when (previewView.isRecording) {
            false -> startRecording(outputFile)
            true -> stopRecording()
        }
    }

    fun flipCameras(lifecycleOwner: LifecycleOwner) {
        when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> lensFacing = CameraSelector.LENS_FACING_BACK
            CameraSelector.LENS_FACING_BACK -> lensFacing = CameraSelector.LENS_FACING_FRONT
        }
        cameraXModule.bindToLifecycle(lifecycleOwner, previewView, lensFacing)
    }

    fun onRecordingStarted(block: () -> Unit) = apply {
        previewView.onRecordingStarted = block
    }

    fun onRecordingResumed(block: () -> Unit) = apply {
        previewView.onRecordingResumed = block
    }

    fun onRecordingPaused(block: () -> Unit) = apply {
        previewView.onRecordingPaused = block
    }

    fun onRecordingStopped(block: () -> Unit) = apply {
        previewView.onRecordingStopped = block
    }

    fun onRecordingFailed(block: (Throwable) -> Unit) = apply {
        previewView.onRecordingFailed = block
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        previewView.onDestroy()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> previewView.onResume()
            Lifecycle.Event.ON_PAUSE -> previewView.onPause()
            Lifecycle.Event.ON_DESTROY -> previewView.onDestroy()
            else -> Unit
        }
    }

    private fun safeAudioSourceUnprocessed(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                DEFAULT_AUDIO_SOURCE
            }

    private fun safeAudioSourceVoicePerformance(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaRecorder.AudioSource.VOICE_PERFORMANCE
            } else {
                DEFAULT_AUDIO_SOURCE
            }

    private fun safeAudioEncoderOpus(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaRecorder.AudioEncoder.OPUS
            } else {
                DEFAULT_AUDIO_ENCODER
            }
}