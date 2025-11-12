package com.pedro.streamer.rotation.custom

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.IntDef
import androidx.constraintlayout.widget.ConstraintLayout
import com.pedro.streamer.databinding.ViewLiveSettingsDialogBinding

class LiveSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnClickListener {
    companion object {
        private const val TAG = "LiveSettingsView"
    }

    private val binding: ViewLiveSettingsDialogBinding =
        ViewLiveSettingsDialogBinding.inflate(LayoutInflater.from(context), this, true)

    private var listener: OnLiveSettingsListener? = null
    @ScanType
    private var scanType: Int = ScanType.PATH

    @IntDef(ScanType.PATH, ScanType.CODE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ScanType {
        companion object {
            const val PATH = 0
            const val CODE = 1
        }
    }

    init {
        // 初始化 Switch 状态并设置点击监听
//        binding.itemLiveSwitch.isChecked = PreferencesUtils.getBoolean(context, SettingsConstant.KEY_USE_MOBILE_NETWORK)
        binding.ivLiveSettingsPathScan.setOnClickListener(this)
        binding.ivLiveSettingsCodeScan.setOnClickListener(this)
        binding.itemLiveSwitch.setOnClickListener(this)
    }

    /**
     * 适配竖屏/横屏的位移（保持和你原实现一致）
     */
    fun matchPortraitScreen() {
        translationY = 80f
        translationX = 0f
    }

    fun matchLandScreen() {
        translationX = 0f
        translationY = 0f
    }

    override fun onClick(v: View) {
        when (v.id) {
            binding.ivLiveSettingsPathScan.id -> {
                scanType = ScanType.PATH
                listener?.onScanCodeClicked(scanType)
            }
            binding.ivLiveSettingsCodeScan.id -> {
                scanType = ScanType.CODE
                listener?.onScanCodeClicked(scanType)
            }
            binding.itemLiveSwitch.id -> {
                val isChecked = binding.itemLiveSwitch.isChecked
                //PreferencesUtils.putBoolean(context, SettingsConstant.KEY_USE_MOBILE_NETWORK, isChecked)
                listener?.onMobileNetworkChecked(isChecked)
            }
        }
    }

    fun setLiveSettingsListener(l: OnLiveSettingsListener?) {
        listener = l
    }

    /**
     * 接收二维码扫描结果并填写到对应 EditText（如果值相同则忽略）
     */
    fun receiveQrScanResult(result: String) {
        when (scanType) {
            ScanType.PATH -> {
                val current = binding.etLiveSettingsPath.text?.toString()
                if (current != result) {
                    binding.etLiveSettingsPath.setText(result)
                }
            }
            ScanType.CODE -> {
                val current = binding.etLiveSettingsCode.text?.toString()
                if (current != result) {
                    binding.etLiveSettingsCode.setText(result)
                }
            }
        }
    }

    /**
     * 拼接 live url。会做空值保护与多余斜杠清理
     *
     * 例如：
     * etPath: "https://example.com/stream"
     * etCode: "room1"
     * -> "https://example.com/stream/room1"
     */
    fun appendLiveUrl(): String {
        val path = binding.etLiveSettingsPath.text?.toString()?.trim().orEmpty()
            .trimEnd('/')
        val code = binding.etLiveSettingsCode.text?.toString()?.trim().orEmpty()
            .trimStart('/')

        return when {
            path.isBlank() && code.isBlank() -> ""
            path.isBlank() -> code
            code.isBlank() -> path
            else -> "$path/$code"
        }
    }

    /**
     * 清除 EditText 焦点并隐藏键盘（安全地从 context 转换为 Activity）
     */
    fun clearEditTextFocus() {
        val activity = context as? Activity ?: return
        val current = activity.currentFocus
        if (current == binding.etLiveSettingsPath || current == binding.etLiveSettingsCode) {
            current.clearFocus()
            //PubUtils.getInstance().hideKeyboard(context, current)
        }
    }
}