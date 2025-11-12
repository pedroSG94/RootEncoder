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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera1Source
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.extrasources.BitmapSource
import com.pedro.extrasources.CameraUvcSource
import com.pedro.extrasources.CameraXSource
import com.pedro.streamer.R
import com.pedro.streamer.rotation.eventbus.BroadcastBackPressedEvent
import com.pedro.streamer.rotation.topmethod.onBackPressedListener
import com.pedro.streamer.utils.FilterMenu
import com.pedro.streamer.utils.Logger
import com.pedro.streamer.utils.toast
import com.pedro.streamer.utils.updateMenuColor
import org.greenrobot.eventbus.EventBus

/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraActivity : AppCompatActivity(), OnTouchListener {
    companion object {
        private const val TAG = "CameraActivity"
        private const val EXIT_TIME_INTERVAL = 2000
    }

    private val liveFragment = LiveFragment.getInstance()
    private val filterMenu: FilterMenu by lazy { FilterMenu(this) }
    private var currentVideoSource: MenuItem? = null
    private var currentAudioSource: MenuItem? = null
    private var currentOrientation: MenuItem? = null
    private var currentFilter: MenuItem? = null
    private var currentPlatform: MenuItem? = null
    private var mClickTime: Long = 0
    private var requestPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var hasCheckPermission: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rotation_activity)
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ result: Map<String, Boolean> ->
            handlePermissionResults(result)
        }
        checkAndRequestPermissions()
        initBackEventListener()
    }

    private fun buildRequiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        perms.add(Manifest.permission.CAMERA)
        perms.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return perms.toTypedArray()
    }

    private fun checkAndRequestPermissions() {
        hasCheckPermission = true
        val required = buildRequiredPermissions()
        val notGranted = required.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }
        if(notGranted.isEmpty()){
            onAllPermissionGranted()
        }else{
          requestPermissionLauncher?.launch(notGranted.toTypedArray())
        }
    }

    private fun handlePermissionResults(results: Map<String, Boolean>) {
        Logger.d(TAG, "handlePermissionResults: results = $results")
        val denied: Set<String> = results.filter { !it.value }.keys
        if(denied.isEmpty()){
            onAllPermissionGranted()
            return
        }
        // 检查是否有“永久拒绝”（即用户勾选了 Don't ask again / 不再询问）
        val permanentlyDenied = denied.filter { perm ->
            isPermissionPermanentlyDenied(perm)
        }
        if(permanentlyDenied.isNotEmpty()){
            // 有永久拒绝 — 强制引导到设置页
            showPermissionDeniedPermanentlyDialog(permanentlyDenied)
        }else{
            // 只是普通拒绝 — 给用户一个明确说明和再次请求的机会或退出
            showPermissionRationaleDialog(denied)
        }
    }

    /**
     * 判断某个权限是否被“永久拒绝”（拒绝并不再询问）
     */
    private fun isPermissionPermanentlyDenied(permission: String): Boolean {
        val denied = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED
        // shouldShowRequestPermissionRationale -> false 表示：要么从未请求过，要么永久拒绝
        val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        return denied && !shouldShow
    }

    private fun onAllPermissionGranted(){
        Logger.d(TAG, "onAllPermissionGranted: ")
        supportFragmentManager.beginTransaction().add(R.id.container, liveFragment).commit()
    }

    private fun showPermissionRationaleDialog(deniedPermissions: Set<String>) {
        Logger.d(TAG, "showPermissionRationaleDialog: deniedPermissions = $deniedPermissions")
        val message = buildRationaleMessage(deniedPermissions)
        AlertDialog.Builder(this)
            .setTitle("需要授权以继续使用")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("重新授权") { _, _ ->
                requestPermissionLauncher?.launch(deniedPermissions.toTypedArray())
            }
            .setNegativeButton("退出应用") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun showPermissionDeniedPermanentlyDialog(permanentlyDenied: List<String>) {
        Logger.d(TAG, "showPermissionDeniedPermanentlyDialog: permanentlyDenied = $permanentlyDenied")
        val message = buildRationaleMessage(permanentlyDenied.toSet()) + "\n\n请在应用设置中手动开启权限，否则无法使用本应用。"
        AlertDialog.Builder(this)
            .setTitle("权限被禁用")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("打开设置") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("退出应用") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun openAppSettings() {
        hasCheckPermission = false
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun buildRationaleMessage(deniedPermissions: Set<String>): String {
        val human = deniedPermissions.map { perm ->
            when (perm) {
                Manifest.permission.CAMERA -> "相机（拍摄/扫码）"
                Manifest.permission.RECORD_AUDIO -> "麦克风（语音/录音）"
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> "相册/媒体（读取照片）"
                else -> perm
            }
        }
        return "应用需要以下权限：\n• ${human.joinToString("\n• ")}\n以正常运行。"
    }

    override fun onResume() {
        super.onResume()
        // 用户可能从设置页回来，这里再次检查权限
        if(!hasCheckPermission){
            checkAndRequestPermissionsIfNeeded()
        }
    }

    private fun checkAndRequestPermissionsIfNeeded() {
        val required = buildRequiredPermissions()
        val notGranted = required.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }
        if(notGranted.isEmpty()){
            onAllPermissionGranted()
        }else{
            AlertDialog.Builder(this)
                .setTitle("权限未就绪")
                .setMessage("必要权限仍然未授权，应用无法继续运行。")
                .setCancelable(false)
                .setPositiveButton("退出应用") { _, _ ->
                    finishAffinity()
                }
                .show()
        }
    }

    private fun initBackEventListener() {
        onBackPressedListener(true) {
            EventBus.getDefault().post(BroadcastBackPressedEvent())
        }
    }

    fun handleBackEvent() {
        Logger.d(TAG, "handleBackEvent: ")
        if ((System.currentTimeMillis() - mClickTime) > EXIT_TIME_INTERVAL) {
            toast("Press again to exit app")
            mClickTime = System.currentTimeMillis()
        } else {
            finish()
        }
    }

    /*override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rotation_menu, menu)
        val defaultVideoSource = menu.findItem(R.id.video_source_camera2)
        val defaultAudioSource = menu.findItem(R.id.audio_source_microphone)
        val defaultOrientation = menu.findItem(R.id.orientation_horizontal)
        val defaultFilter = menu.findItem(R.id.no_filter)
        val defaultPlatform = menu.findItem(R.id.platform_youtube)
        currentVideoSource = defaultVideoSource.updateMenuColor(this, currentVideoSource)
        currentAudioSource = defaultAudioSource.updateMenuColor(this, currentAudioSource)
        currentOrientation = defaultOrientation.updateMenuColor(this, currentOrientation)
        currentFilter = defaultFilter.updateMenuColor(this, currentFilter)
        currentPlatform = defaultPlatform.updateMenuColor(this, currentPlatform)
        return true
    }*/

//  override fun onBackPressed() {
//    super.onBackPressed()
//    Logger.d(TAG, "onBackPressed: ")
//  }

//  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//    if(keyCode == KeyEvent.KEYCODE_BACK){
//      Logger.d(TAG, "onKeyDown: ")
//
//    }
//    return super.onKeyDown(keyCode, event)
//  }

    /*override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            when (item.itemId) {
                R.id.video_source_camera1 -> {
                    currentVideoSource = item.updateMenuColor(this, currentVideoSource)
                    liveFragment.genericStream.changeVideoSource(Camera1Source(applicationContext))
                }

                R.id.video_source_camera2 -> {
                    currentVideoSource = item.updateMenuColor(this, currentVideoSource)
                    liveFragment.genericStream.changeVideoSource(Camera2Source(applicationContext))
                }

                R.id.video_source_camerax -> {
                    currentVideoSource = item.updateMenuColor(this, currentVideoSource)
                    liveFragment.genericStream.changeVideoSource(CameraXSource(applicationContext))
                }

                R.id.video_source_camera_uvc -> {
                    currentVideoSource = item.updateMenuColor(this, currentVideoSource)
                    liveFragment.genericStream.changeVideoSource(CameraUvcSource())
                }

                R.id.video_source_bitmap -> {
                    currentVideoSource = item.updateMenuColor(this, currentVideoSource)
                    val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
                    liveFragment.genericStream.changeVideoSource(BitmapSource(bitmap))
                }

                R.id.audio_source_microphone -> {
                    currentAudioSource = item.updateMenuColor(this, currentAudioSource)
                    liveFragment.genericStream.changeAudioSource(MicrophoneSource())
                }

                R.id.orientation_horizontal -> {
                    currentOrientation = item.updateMenuColor(this, currentOrientation)
                    liveFragment.setOrientationMode(false)
                }

                R.id.orientation_vertical -> {
                    currentOrientation = item.updateMenuColor(this, currentOrientation)
                    liveFragment.setOrientationMode(true)
                }

                R.id.platform_huya -> {
                    currentPlatform = item.updateMenuColor(this, currentPlatform)
                    liveFragment.streamUrl.setText(R.string.stream_url_huya)
                }

                R.id.platform_tiktok -> {
                    currentPlatform = item.updateMenuColor(this, currentPlatform)
                    liveFragment.streamUrl.setText(R.string.stream_url_tiktok)
                }

                R.id.platform_youtube -> {
                    currentPlatform = item.updateMenuColor(this, currentPlatform)
                    liveFragment.streamUrl.setText(R.string.stream_url_youtube)
                }

                else -> {
                    val result = filterMenu.onOptionsItemSelected(
                        item,
                        liveFragment.genericStream.getGlInterface()
                    )
                    if (result) currentFilter = item.updateMenuColor(this, currentFilter)
                    return result
                }
            }
        } catch (e: IllegalArgumentException) {
//      toast("Change source error: ${e.message}")
        }
        return super.onOptionsItemSelected(item)
    }*/

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        if (filterMenu.spriteGestureController.spriteTouched(view, motionEvent)) {
            filterMenu.spriteGestureController.moveSprite(view, motionEvent)
            filterMenu.spriteGestureController.scaleSprite(motionEvent)
            return true
        }
        return false
    }
}

