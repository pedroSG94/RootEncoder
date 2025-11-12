package com.pedro.streamer.rotation.custom

interface OnLiveSettingsListener {
    fun onScanCodeClicked(@LiveSettingsView.ScanType type: Int)

    fun onMobileNetworkChecked(isChecked: Boolean)
}