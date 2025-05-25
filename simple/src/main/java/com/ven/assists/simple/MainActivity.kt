package com.ven.assists.simple

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.PermissionUtils.SimpleCallback
import com.lxj.xpopup.XPopup
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.logNode
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.simple.databinding.ActivityMainBinding
import com.ven.assists.simple.overlays.OverlayBasic
import com.ven.assists.simple.overlays.OverlayLog
import com.ven.assists.simple.overlays.OverlayPro
import com.ven.assists.simple.overlays.OverlayWeb
import com.ven.assists.simple.step.Forward
import com.ven.assists.simple.step.StepTag
import com.ven.assists.stepper.StepManager
import com.ven.assists.utils.CoroutineWrapper
import androidx.core.net.toUri
import com.ven.assists.simple.step.ContactList


class MainActivity : AppCompatActivity(), AssistsServiceListener {
    private var isActivityResumed = false
    val viewBind: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater).apply {
            btnEnable.setOnClickListener {
                AssistsCore.openAccessibilitySetting()
                startActivity(Intent(this@MainActivity, SettingGuideActivity::class.java))
            }
            btnBasic.setOnClickListener {
                OverlayBasic.onClose = {
                    OverlayBasic.hide()
                }
                if (OverlayBasic.showed) {
                    OverlayBasic.hide()
                } else {
                    OverlayBasic.show()
                }
            }
            btnPro.setOnClickListener {
                OverlayPro.onClose = {
                    OverlayPro.hide()
                }
                if (OverlayPro.showed) {
                    OverlayPro.hide()
                } else {
                    OverlayPro.show()
                }
            }
            btnAdvanced.setOnClickListener {
                // 第一个对话框：询问群名称
                XPopup.Builder(this@MainActivity).asInputConfirm("设置监听群", "请输入要监听的群名称：", ContactList.sourceGroupName) { groupName ->
                    val finalGroupName = groupName.ifEmpty { ContactList.sourceGroupName }
                    // 第二个对话框：询问用户名称
                    XPopup.Builder(this@MainActivity).asInputConfirm("设置监听用户", "请输入要监听的微信用户名称：", ContactList.sourceRobotName) { userName ->
                        val finalUserName = userName.ifEmpty { ContactList.sourceRobotName }
                        // 设置新的监听群和用户
                        ContactList.sourceGroupName = finalGroupName
                        ContactList.sourceRobotName = finalUserName
                        // 显示悬浮窗并开始执行
                        OverlayLog.show()
                        StepManager.execute(Forward::class.java, StepTag.STEP_1, begin = true)
                    }.show()
                }.show()
            }
            btnWeb.setOnClickListener {
                OverlayWeb.onClose = {
                    OverlayWeb.hide()
                }
                if (OverlayWeb.showed) {
                    OverlayWeb.hide()
                } else {

                    OverlayWeb.show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        checkServiceEnable()
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
    }

    private fun checkServiceEnable() {
        if (!isActivityResumed) return
        if (AssistsCore.isAccessibilityServiceEnabled()) {
            viewBind.btnEnable.isVisible = false
            viewBind.llOption.isVisible = true
        } else {
            viewBind.btnEnable.isVisible = true
            viewBind.llOption.isVisible = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)
//        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
//
//        }

    }

    override fun onServiceConnected(service: AssistsService) {
//        onBackApp()
        checkServiceEnable()
        AssistsCore.getAllNodes().forEach { it.logNode() }
        if (AssistsCore.getPackageName() != AppUtils.getAppPackageName()) {
            CoroutineWrapper.launch { AssistsCore.launchApp(AppUtils.getAppPackageName()) }
        }
    }

    override fun onUnbind() {
        checkServiceEnable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        BarUtils.setStatusBarLightMode(this, true)
        setContentView(viewBind.root)
        AssistsService.listeners.add(this)
        checkPermission()
    }

    private fun checkPermission() {
        val areNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!areNotificationsEnabled) {
            // 通知权限未开启，提示用户去设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionUtils.permission(Manifest.permission.POST_NOTIFICATIONS).callback(object : SimpleCallback {
                    override fun onGranted() {

                    }

                    override fun onDenied() {
                        showNotificationPermissionOpenDialog()
                    }
                }).request()
            } else {
                showNotificationPermissionOpenDialog()
            }
        }
    }

    private fun showNotificationPermissionOpenDialog() {
        XPopup.Builder(this).asConfirm("提示", "未开启通知权限，开启通知权限以获得完整测试相关通知提示") {
            val intent = Intent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上版本，跳转到应用的通知设置页面
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                // Android 8.0以下版本，跳转到应用详情页面
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.setData(("package:" + getPackageName()).toUri())
            }
            startActivity(intent)
        }.show()

    }

    override fun onDestroy() {
        super.onDestroy()
        AssistsService.listeners.remove(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        moveTaskToBack(true)
    }
}