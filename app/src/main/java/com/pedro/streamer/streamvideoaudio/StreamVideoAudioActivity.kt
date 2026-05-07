package com.pedro.streamer.streamvideoaudio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioFileSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.library.generic.GenericStream
import com.pedro.streamer.R
import com.pedro.streamer.utils.AudioMixer
import com.pedro.streamer.utils.fitAppPadding
import com.pedro.streamer.utils.toast

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class StreamVideoAudioActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {

    private val TAG = "StreamVideoAudio"
    private val genericStream: GenericStream by lazy {
        GenericStream(this, this).apply {
            getGlInterface().autoHandleOrientation = true
        }
    }
    private var audioFileSource: AudioFileSource? = null
    private lateinit var btnStartStop: ImageView
    private lateinit var btnPlayPauseAudio: ImageView
    private lateinit var btnSelectAudio: ImageView
    private lateinit var btnMicrophone: ImageView
    private lateinit var txtTitleAudio: TextView
    private lateinit var edtUrl: EditText
    private lateinit var sbAudioVolume: SeekBar
    private lateinit var sbMicVolume: SeekBar
    private lateinit var surfaceView: SurfaceView
    private var uriAudio: Uri? = null
    private val audioMixer = AudioMixer()
    private val AUDIO_SAMPLE_RATE = 44100
    private var currentAudioTime: Double = 0.0
    private var isMicMutedInternal = false
    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { newUri ->
                val fileRate = getSampleRate(newUri)
                if (fileRate == -1) {
                    toast("Error: The selected file does not contain a valid audio track.")
                    return@let
                }
                if (fileRate != AUDIO_SAMPLE_RATE) {
                    toast("Error: This file is $fileRate Hz. Only 44100Hz is supported.")
                    return@let
                }

                audioFileSource?.apply {
                    stop()
                    stopAudioDevice()
                    release()
                }

                txtTitleAudio.text = getFileName(newUri) ?: "Audio selected"
                currentAudioTime = 0.0
                this.uriAudio = newUri
                try {
                    audioFileSource = createAudioFileSource(newUri)
                } catch (ex: Exception) {
                    toast(ex.message ?: "Error loading file audio")
                }
                if (genericStream.isStreaming) {
                    toggleAudio()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream_video_audio)
        fitAppPadding()
        initViews()
        prepareStream()
    }

    private fun initViews() {
        btnStartStop = findViewById(R.id.btn_start_stop)
        btnPlayPauseAudio = findViewById(R.id.btn_play_pause)
        btnSelectAudio = findViewById(R.id.btn_select_audio)
        btnMicrophone = findViewById(R.id.btn_microphone)
        txtTitleAudio = findViewById(R.id.txt_audio_title)
        edtUrl = findViewById(R.id.edt_url)
        surfaceView = findViewById(R.id.surfaceView)
        sbAudioVolume = findViewById(R.id.sb_volume_audio)
        sbMicVolume = findViewById(R.id.sb_volume_mic)
        surfaceView.holder.addCallback(this)

        val isMicMuted = (genericStream.audioSource as? MicrophoneSource)?.isMuted() ?: false
        stateMicButton(!isMicMuted)
        stateAudioButton(false)
        btnPlayPauseAudio.setImageResource(R.drawable.play_video_48)

        btnSelectAudio.setOnClickListener {
            audioPicker.launch(arrayOf("audio/*", "video/*"))
        }

        btnStartStop.setOnClickListener {
            toggleStream()
        }

        btnPlayPauseAudio.setOnClickListener {
            toggleAudio()
        }

        btnMicrophone.setOnClickListener {
            toggleMicrophone()
        }

        sbAudioVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val audioFileSource = audioFileSource ?: return
                val audioTrack = audioFileSource.getAudioTrack() ?: return
                val volume = progress / 100f

                audioMixer.audioVolume = volume
                audioTrack.setVolume(volume)
                Log.d(TAG, "Volume changed to: $volume")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbMicVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!isMicMutedInternal) {
                    val volume = progress / 100f
                    audioMixer.micVolume = volume
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (it.moveToFirst()) {
                name = it.getString(index)
            }
        }
        return name
    }

    private fun toggleMicrophone() {
        if (!genericStream.isStreaming) {
            return
        }

        isMicMutedInternal = !isMicMutedInternal

        if (isMicMutedInternal) {
            // TURN OFF MIC: Set the volume to 0 on the mixer.
            audioMixer.micVolume = 0f
            stateMicButton(false)
            toast("Microphone OFF")
        } else {
            // TURN ON THE MIC: Reclaiming Value from SeekBar
            audioMixer.micVolume = sbMicVolume.progress / 100f
            stateMicButton(true)
            toast("Microphone ON")
        }

        val micSource = genericStream.audioSource as? MicrophoneSource
        micSource?.unMute()

    }

    private fun prepareStream() {
        try {
            val micSource = genericStream.audioSource as? MicrophoneSource
            micSource?.setAudioEffect(audioMixer)

            val isPrepareAudio = genericStream.prepareAudio(
                sampleRate = AUDIO_SAMPLE_RATE,
                isStereo = true,
                bitrate = 128 * 1000
            )
            val isPrepareVideo =
                genericStream.prepareVideo(width = 640, height = 480, bitrate = 1200 * 1000)
            genericStream.getStreamClient().setReTries(10)
            val isCheck = isPrepareAudio && isPrepareVideo
            if (!isCheck) {
                toast("Audio or Video configuration failed")
                return
            }

        } catch (ex: IllegalStateException) {
            toast(ex.message ?: "Prepare stream failed")
            return
        } catch (ex: Exception) {
            toast(ex.message ?: "Prepare stream failed")
            return
        }
    }

    private fun stateMicButton(isEnable: Boolean = false) {
        runOnUiThread {
            val drawable =
                if (isEnable) R.drawable.microphone_icon else R.drawable.microphone_off_icon
            btnMicrophone.setImageResource(drawable)
        }
    }

    private fun stateStream(isStream: Boolean = false) {
        runOnUiThread {
            if (isStream) {
                btnStartStop.setImageResource(R.drawable.stream_stop_icon)
            } else {
                btnStartStop.setImageResource(R.drawable.stream_icon)
            }
        }
    }

    private fun toggleStream() {
        if (genericStream.isStreaming) {
            runOnUiThread {
                audioFileSource?.apply {
                    stop()
                    stopAudioDevice()
                    release()
                }
                stateStream(false)
                stateAudioButton(false)
                btnPlayPauseAudio.setImageResource(R.drawable.play_video_48)
                currentAudioTime = 0.0
                genericStream.stopStream()
            }
            return
        }

        val url = edtUrl.text.toString().trim()
//       val url = "srt://192.168.1.5:9991?mode=listener"
        if (url.isEmpty()) {
            toast("Please enter a valid URL")
            return
        }

        genericStream.startStream(url)
        stateStream(true)
        stateAudioButton(true)
    }

    private fun stateAudioButton(isEnable: Boolean = false) {
        btnPlayPauseAudio.apply {
            isEnabled = isEnable
            alpha = if (isEnable) 1f else 0.5f
        }
    }

    fun getSampleRate(uri: Uri): Int {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(this, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    return rate
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting sample rate from $uri", e)
            return -1
        } finally {
            extractor.release()
        }
        return -1
    }

    private fun createAudioFileSource(uri: Uri): AudioFileSource? {
        try {
            val fileRate = getSampleRate(uri)

            if (fileRate == -1) {
                throw IllegalArgumentException("The selected file does not contain a valid audio track.")
            }
            if (fileRate != AUDIO_SAMPLE_RATE) {
                throw IllegalArgumentException("Only 44100Hz files are supported.")
            }

            return AudioFileSource(this, uri, false) { isLoop ->
                if (isLoop) {
                    runOnUiThread {
                        toast("Audio looped")
                    }
                } else {
                    // The Audio ends completely (if loopMode = false)
                    audioFileSource?.stop()
                    audioFileSource?.stopAudioDevice()
                    runOnUiThread {
                        currentAudioTime = 0.0
                        btnPlayPauseAudio.setImageResource(R.drawable.play_video_48)
                        audioMixer.clear()
                        toast("Audio finished")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading file audio", e)
            toast("Error loading file audio: ${e.message}")
            return null
        }
    }

    private fun toggleAudio() {
        val uri = uriAudio ?: return toast("Please select a audio file")

        if (audioFileSource == null) {
            try {
                audioFileSource = createAudioFileSource(uri)
            } catch (ex: Exception) {
                toast(ex.message ?: "Error loading file audio")
            }
        }

        val source = audioFileSource ?: return toast("Error loading audio file")

        try {
            // audio is running
            if (source.isRunning()) {
                currentAudioTime = source.getTime()
                source.stop()
                source.stopAudioDevice()
                audioMixer.clear()
                btnPlayPauseAudio.setImageResource(R.drawable.play_video_48) // Play icon
                toast("Audio pause")
                return
            }
            // audio is not running
            val isInitialized = source.init(
                AUDIO_SAMPLE_RATE,
                isStereo = true,
                echoCanceler = false,
                noiseSuppressor = false
            )
            if (!isInitialized) return toast("Error loading audio file")
            source.start(object : GetMicrophoneData {
                override fun inputPCMData(frame: Frame) {
//                  val pcm = frame.buffer.copyOfRange(frame.offset, frame.offset + frame.size)
                    val pcm = ByteArray(frame.size)
                    System.arraycopy(frame.buffer, frame.offset, pcm, 0, frame.size)
                    audioMixer.pushAudioData(pcm)
                }
            })
            source.playAudioDevice()
            val duration = source.getDuration()
            if (currentAudioTime >= duration) {
                currentAudioTime = 0.0
            }
            source.moveTo(currentAudioTime)

            //check if the audio is actually playing.
            if (source.isAudioDeviceEnabled()) {
                toast("audio playing")
                btnPlayPauseAudio.setImageResource(R.drawable.pause_icon) // Pause icon
            } else {
                btnPlayPauseAudio.setImageResource(R.drawable.play_video_48)
                toast("Error playing audio")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            toast("Error playing audio: ${e.message}")
            return
        }
    }

    // --- Surface Callback ---
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!genericStream.isOnPreview) genericStream.startPreview(surfaceView)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        genericStream.getGlInterface().setPreviewResolution(w, h)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (genericStream.isOnPreview) genericStream.stopPreview()
    }

    // --- ConnectChecker Callbacks ---
    override fun onConnectionSuccess() {
        toast("Connected")
        val micSource = genericStream.audioSource as? MicrophoneSource
        micSource?.setAudioEffect(audioMixer)
    }

    override fun onConnectionFailed(reason: String) {
        if (genericStream.getStreamClient().reTry(5000, reason, null)) {
            Log.d(TAG, "onConnection: Retry")
        } else {
            genericStream.stopStream()
            stateStream(false)
            Log.d(TAG, "onConnection: Failed : $reason")
            toast("Connection : Failed")
        }
    }

    override fun onConnectionStarted(url: String) {}
    override fun onDisconnect() {
        toast("Disconnected")
    }

    override fun onAuthError() {}
    override fun onAuthSuccess() {}

    override fun onDestroy() {
        super.onDestroy()
        genericStream.release()
        uriAudio = null
        audioFileSource?.apply {
            stop()
            stopAudioDevice()
            release()
        }
    }
}
