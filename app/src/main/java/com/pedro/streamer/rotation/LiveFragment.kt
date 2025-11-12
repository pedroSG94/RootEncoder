/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.streamer.rotation

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.video.Camera1Source
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.extrasources.CameraXSource
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.BitrateAdapter
import com.pedro.streamer.R
import com.pedro.streamer.rotation.annotation.CameraMode
import com.pedro.streamer.rotation.annotation.IconState
import com.pedro.streamer.rotation.annotation.SettingType
import com.pedro.streamer.rotation.bean.IconInfo
import com.pedro.streamer.rotation.custom.LiveSettingsAdapter
import com.pedro.streamer.rotation.custom.LiveSettingsView
import com.pedro.streamer.rotation.eventbus.BroadcastBackPressedEvent
import com.pedro.streamer.utils.Logger
import com.pedro.streamer.utils.toast
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Locale
import com.pedro.streamer.rotation.custom.OnLiveSettingsListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Example code to stream using StreamBase. This is the recommend way to use the library.
 * Necessary API 21+
 * This mode allow you stream using custom Video/Audio sources, attach a preview or not dynamically, support device rotation, etc.
 *
 * Check Menu to use filters, video and audio sources, and orientation
 *
 * Orientation horizontal (by default) means that you want stream with vertical resolution
 * (with = 640, height = 480 and rotation = 0) The stream/record result will be 640x480 resolution
 *
 * Orientation vertical means that you want stream with vertical resolution
 * (with = 640, height = 480 and rotation = 90) The stream/record result will be 480x640 resolution
 *
 * More documentation see:
 * [com.pedro.library.base.StreamBase]
 * Support RTMP, RTSP and SRT with commons features
 * [com.pedro.library.generic.GenericStream]
 * Support RTSP with all RTSP features
 * [com.pedro.library.rtsp.RtspStream]
 * Support RTMP with all RTMP features
 * [com.pedro.library.rtmp.RtmpStream]
 * Support SRT with all SRT features
 * [com.pedro.library.srt.SrtStream]
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class LiveFragment: Fragment(), ConnectChecker {
    companion object {
        fun getInstance(): LiveFragment = LiveFragment()
        private const val TAG = "CameraFragment"
    }

    enum class Resolution {
        _1080P, _720P
    }

    val genericStream: GenericStream by lazy {
        GenericStream(requireContext(), this).apply {
            getGlInterface().autoHandleOrientation = true
            getStreamClient().setBitrateExponentialFactor(0.5f)
        }
    }
    private lateinit var surfaceView: SurfaceView
    private lateinit var liveStartStop: ImageView
    private lateinit var bitrateText: TextView
    private lateinit var recyclerView: RecyclerView
    private var liveSettingView: LiveSettingsView? = null
    //  private val width = 640
//  private val height = 480
    private var width = 1440
    private var height = 1080

    //  private val vBitrate = 1200 * 1000
    private var vBitrate = 2500 * 1000
    private var rotation = 0
    private val sampleRate = 32000
    private val isStereo = true
    private val aBitrate = 128 * 1000
    private var recordPath = ""
    private var mCurResolution = Resolution._1080P
    private var liveSettings: List<IconInfo>? = null
    private val liveSettingsAdapter: LiveSettingsAdapter by lazy { LiveSettingsAdapter(
        onIconClick = { handleSettingIconClick(it) }
    ) }
    private var topLayout: FrameLayout? = null
    //Bitrate adapter used to change the bitrate on fly depend of the bandwidth.
    private val bitrateAdapter = BitrateAdapter {
        genericStream.setVideoBitrateOnFly(it)
    }.apply {
        setMaxBitrate(vBitrate + aBitrate)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d(TAG, "onCreate: ")
        if (EventBus.getDefault().isRegistered(this).not()) {
            EventBus.getDefault().register(this)
        }
        prepare()
        genericStream.getStreamClient().setReTries(10)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_live, container, false)
        liveStartStop = view.findViewById(R.id.live_start_stop)
        val liveRecord = view.findViewById<ImageView>(R.id.live_record)
        val liveSwitchLens = view.findViewById<ImageView>(R.id.live_switch_lens)
        recyclerView = view.findViewById(R.id.live_recycler_view)
        recyclerView.adapter = liveSettingsAdapter.also { it.submitList(liveSettings) }
        bitrateText = view.findViewById(R.id.live_bitrate)
        surfaceView = view.findViewById(R.id.live_surface_view)
        (requireActivity() as? RotationActivity)?.let {
            surfaceView.setOnTouchListener(it)
        }
        topLayout = view.findViewById(R.id.top_layout)
        showLiveSettingsDialog()

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback2 {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Logger.d(TAG, "surfaceCreated: ")
                holder.setKeepScreenOn(true)
                if (!genericStream.isOnPreview) genericStream.startPreview(surfaceView)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Logger.d(TAG, "surfaceChanged: width = $width; height = $height")
                genericStream.getGlInterface().setPreviewResolution(width, height)
//        genericStream.getGlInterface().setPreviewResolution(height, width)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Logger.d(TAG, "surfaceDestroyed: ")
                if (genericStream.isOnPreview) genericStream.stopPreview()
            }

            override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
                Logger.d(TAG, "surfaceRedrawNeeded: ")
            }
        })

        liveStartStop.setOnClickListener {
            Logger.d(TAG, "onCreateView: liveStartStop clicked")
            if (!genericStream.isStreaming) {
                genericStream.startStream("streamUrl.text.toString()")
                liveStartStop.setImageResource(R.drawable.ic_live_stop)
            } else {
                genericStream.stopStream()
                liveStartStop.setImageResource(R.drawable.ic_live_start)
            }
        }

        liveRecord.setOnClickListener {
            Logger.d(TAG, "onCreateView: liveRecord clicked")
            if (mCurResolution == Resolution._1080P) {
                mCurResolution = Resolution._720P
                width = 960
                height = 720
                vBitrate = 2000 * 1000
                liveRecord.setImageResource(R.drawable.ic_resolution_720)
                genericStream.setVideoResolution(width, height)
                genericStream.setVideoBitRate(vBitrate)
                if (genericStream.isStreaming) {
                    genericStream.stopStream()
                    liveStartStop.setImageResource(R.drawable.ic_live_start)
                }
            } else {
                mCurResolution = Resolution._1080P
                width = 1440
                height = 1080
                vBitrate = 2500 * 1000
                liveRecord.setImageResource(R.drawable.ic_resolution_1080)
                genericStream.setVideoResolution(width, height)
                genericStream.setVideoBitRate(vBitrate)
                if (genericStream.isStreaming) {
                    genericStream.stopStream()
                    liveStartStop.setImageResource(R.drawable.ic_live_start)
                }
            }
            when (val source = genericStream.videoSource) {
                is Camera2Source -> source.changeResolution()
            }

//      if (!genericStream.isRecording) {
//        val folder = PathUtils.getRecordPath()
//        if (!folder.exists()) folder.mkdir()
//        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
//        recordPath = "${folder.absolutePath}/${sdf.format(Date())}.mp4"
//        genericStream.startRecord(recordPath) { status ->
//          if (status == RecordController.Status.RECORDING) {
//            bRecord.setImageResource(R.drawable.stop_icon)
//          }
//        }
//        bRecord.setImageResource(R.drawable.pause_icon)
//      } else {
//        genericStream.stopRecord()
//        bRecord.setImageResource(R.drawable.record_icon)
//        PathUtils.updateGallery(requireContext(), recordPath)
//      }
        }

        liveSwitchLens.setOnClickListener {
            Logger.d(TAG, "onCreateView: liveSwitchLens clicked")
            when (val source = genericStream.videoSource) {
                is Camera1Source -> source.switchCamera()
                is Camera2Source -> source.switchCamera()
                is CameraXSource -> source.switchCamera()
            }
        }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        liveSettingView?.let {
            it.setLiveSettingsListener(null)
            topLayout?.removeView(it)
            liveSettingView = null
        }
        genericStream.release()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    private fun showLiveSettingsDialog() {
        if(liveSettingView != null && liveSettingView?.parent != null){
            liveSettingView?.let {
                it.isVisible = true
                it.bringToFront()
                it.requestLayout()
            }
            return
        }
        val v = LiveSettingsView(requireContext())
        v.setLiveSettingsListener(object : OnLiveSettingsListener {
            override fun onScanCodeClicked(type: Int) {
                showScanCodeView()
            }

            override fun onMobileNetworkChecked(isChecked: Boolean) {

            }
        })
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            topLayout?.addView(v, lp)
            v.bringToFront()
            v.requestLayout()
        }
        liveSettingView = v
    }

    private fun hideLiveSettingsDialog() {
        liveSettingView?.let {
            it.isVisible = false
            it.clearEditTextFocus()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun handleMessageEvent(event: BroadcastBackPressedEvent) {
        if (genericStream.isStreaming) {
            genericStream.stopStream()
            liveStartStop.setImageResource(R.drawable.ic_live_start)
        } else {
            (requireActivity() as CameraActivity).handleBackEvent()
        }
    }

    fun setOrientationMode(isVertical: Boolean) {
        val wasOnPreview = genericStream.isOnPreview
        Logger.d(TAG, "setOrientationMode: isVertical = $isVertical, wasOnPreview = $wasOnPreview")
        genericStream.release()
        rotation = if (isVertical) 90 else 0
        prepare()
        if (wasOnPreview) genericStream.startPreview(surfaceView)
    }

    private fun prepare() {
        Logger.d(TAG, "prepare: ")
        prepareLiveSettings()
        val prepared = try {
            genericStream.prepareVideo(width, height, vBitrate, rotation = rotation)
                    && genericStream.prepareAudio(sampleRate, isStereo, aBitrate)
        } catch (e: IllegalArgumentException) {
            false
        }
        if (!prepared) {
            Logger.d(TAG, "prepare: Audio or Video configuration failed")
            activity?.finish()
        }
    }

    private fun prepareLiveSettings(){
        @SettingType
        val settingTypes = listOf(
            SettingType.LIVE,
            SettingType.RESOLUTION,
            SettingType.MICROPHONE
        )
        liveSettings = settingTypes.map { type ->
            val icons = getIconsBySettingType(type)
            IconInfo(CameraMode.LIVE, type, icons.keyAt(0), icons)
        }
    }

    private fun getIconsBySettingType(@SettingType settingType: Int): SparseIntArray {
        return when (settingType) {
            SettingType.LIVE -> SparseIntArray().apply {
                put(IconState.OFF, R.drawable.ic_live_setting_off)
                put(IconState.ON, R.drawable.ic_live_setting_on)
            }
            SettingType.RESOLUTION -> SparseIntArray().apply {
                put(IconState.ON, R.drawable.ic_resolution_720)
                put(IconState.ON, R.drawable.ic_resolution_1080)
            }
            SettingType.MICROPHONE -> SparseIntArray().apply {
                put(IconState.ON, R.drawable.ic_microphone_on)
                put(IconState.OFF, R.drawable.ic_microphone_off)
            }
            else -> SparseIntArray()
        }
    }

    private fun handleSettingIconClick(iconInfo: IconInfo){
        when (iconInfo.mode) {
            CameraMode.LIVE -> {
                when (iconInfo.type) {
                    SettingType.LIVE -> {
                        when (iconInfo.state) {
                            IconState.ON -> {
                                showLiveSettingsDialog()
                            }
                            IconState.OFF -> {
                                hideLiveSettingsDialog()
                            }
                        }
                    }
                    SettingType.RESOLUTION -> {

                    }
                    SettingType.MICROPHONE -> {
                        when (iconInfo.state) {
                            IconState.ON -> {

                            }
                            IconState.OFF -> {

                            }
                        }
                    }
                }
            }
        }
    }


    override fun onConnectionStarted(url: String) {
    }

    override fun onConnectionSuccess() {
        toast("Connected")
    }

    override fun onConnectionFailed(reason: String) {
        if (genericStream.getStreamClient().reTry(5000, reason, null)) {
//      toast("Retry")
        } else {
            genericStream.stopStream()
            liveStartStop.setImageResource(R.drawable.ic_live_start)
//      toast("Failed: $reason")
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        bitrateAdapter.adaptBitrate(bitrate, genericStream.getStreamClient().hasCongestion())
        bitrateText.text = String.format(Locale.getDefault(), "%.1f mb/s", bitrate / 1000_000f)
    }

    override fun onDisconnect() {
        bitrateText.text = String()
        toast("Disconnected")
    }

    override fun onAuthError() {
        genericStream.stopStream()
        liveStartStop.setImageResource(R.drawable.ic_live_start)
//    toast("Auth error")
    }

    override fun onAuthSuccess() {
//    toast("Auth success")
    }

    private fun showScanCodeView() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {

        }
    }
}