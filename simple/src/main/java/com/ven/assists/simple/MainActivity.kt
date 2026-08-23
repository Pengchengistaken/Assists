package com.ven.assists.simple

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.PermissionUtils.SimpleCallback
import com.blankj.utilcode.util.TimeUtils
import com.lxj.xpopup.XPopup
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.logNode
import com.ven.assists.log.AssistsLog
import com.ven.assists.log.AssistsLogDiagnostics
import com.ven.assists.log.log
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.simple.databinding.ActivityMainBinding
import com.ven.assists.simple.overlays.OverlayAdvanced
import com.ven.assists.simple.overlays.OverlayBasic
import com.ven.assists.simple.overlays.OverlayLog
import com.ven.assists.simple.overlays.OverlayPro
import com.ven.assists.simple.overlays.OverlayStatusCard
import com.ven.assists.simple.overlays.OverlayWeb
import com.ven.assists.simple.step.Forward
import com.ven.assists.simple.step.StepTag
import com.ven.assists.stepper.StepManager
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.window.AssistsWindowManager.overlayToast
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import com.ven.assists.simple.guard.AntiBanConfig
import com.ven.assists.simple.guard.AntiBanGuard
import com.ven.assists.simple.guard.ForwardNotificationListener
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
                // 先询问是否需要修改设置
                XPopup.Builder(this@MainActivity).asConfirm(
                    "提示",
                    "是否要修改监听设置？\n\n当前监听群：\n${ContactList.sourceGroupName}\n\n当前监听用户：\n${ContactList.sourceRobotNames.joinToString("\n")}",
                    {
                        // 用户选择修改设置
                        // 第一个对话框：询问群名称
                        XPopup.Builder(this@MainActivity).asInputConfirm("设置监听群", "请输入要监听的群名称：", ContactList.sourceGroupName) { groupName ->
                            val finalGroupName = groupName.ifEmpty { ContactList.sourceGroupName }
                            // 第二个对话框：询问用户名称
                            XPopup.Builder(this@MainActivity).asInputConfirm("设置监听用户", "请输入要监听的微信用户名称（多个用逗号分隔）：", ContactList.sourceRobotNames.joinToString(",")) { userName ->
                                val finalUserNames = if (userName.isBlank()) {
                                    ContactList.sourceRobotNames
                                } else {
                                    userName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                }
                                // 设置新的监听群和用户
                                ContactList.sourceGroupName = finalGroupName
                                ContactList.sourceRobotNames = finalUserNames
                                // 保存设置
                                ContactList.saveSettings(this@MainActivity)
                                startForwardAutomation()
                            }.show()
                        }.show()
                    },
                    {
                        startForwardAutomation()
                    }
                ).setConfirmText("修改设置")
                .setCancelText("使用当前设置")
                .show()
            }
            btnAntiban.setOnClickListener {
                showAntiBanSettingsDialog()
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
            btnLog.setOnClickListener {
                OverlayLog.onClose = {
                    OverlayLog.hide()
                }
                if (OverlayLog.showed) {
                    OverlayLog.hide()
                } else {
                    OverlayLog.show(clearLog = false, mainPageLogViewer = true)
                }
            }
            btnTest.isVisible = AppUtils.isAppDebug()
            btnTest.setOnClickListener {
                OverlayStatusCard.onClose = {
                    OverlayStatusCard.hide()
                }
                if (OverlayStatusCard.showed) {
                    OverlayStatusCard.hide()
                } else {

                    OverlayStatusCard.show("")
                }

            }
        }
    }
    private val foregroundServiceIntent: Intent by lazy {
        Intent(this, ForegroundService::class.java)
    }
    private var disableNotificationView: View? = null


    private lateinit var drawingView: MultiTouchDrawingView


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
        if (AssistsCore.isA11yEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(foregroundServiceIntent)
            }
            viewBind.btnEnable.isVisible = false
            viewBind.llOption.isVisible = true
        } else {
            stopService(foregroundServiceIntent)
            viewBind.btnEnable.isVisible = true
            viewBind.llOption.isVisible = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)
//        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
//            LogUtils.d(event.text)
//        }
    }

    override fun onServiceConnected(service: AssistsService) {
        checkServiceEnable()
        if (AssistsCore.getPackageName() != AppUtils.getAppPackageName()) {
            CoroutineWrapper.launch { AssistsCore.launchApp(AppUtils.getAppPackageName()) }
        }
    }

    override fun onUnbind() {
        checkServiceEnable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppUtils.isAppDebug()) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        BarUtils.setStatusBarLightMode(this, true)
        setContentView(viewBind.root)
        AssistsService.listeners.add(this)
        checkPermission()

        ContactList.loadSettings(this)
        AntiBanGuard.load()
    }

    private fun startForwardAutomation() {
        when (AntiBanGuard.assertAllowed()) {
            is AntiBanGuard.GuardResult.Blocked -> {
                XPopup.Builder(this).asConfirm(
                    "防封限制",
                    "当前不满足运行条件，是否仍要启动？\n建议使用「防封设置」查看 Strict 预设。",
                    { doStartForward() },
                    null,
                ).show()
            }
            is AntiBanGuard.GuardResult.Allowed -> doStartForward()
        }
    }

    private fun doStartForward() {
        ForwardNotificationListener.register()
        OverlayLog.show()
        StepManager.isStop = false
        StepManager.execute(Forward::class.java, StepTag.STEP_1, begin = true)
    }

    private fun showAntiBanSettingsDialog() {
        val presets = AntiBanConfig.Preset.entries.map { it.name }.toTypedArray()
        val currentIndex = AntiBanConfig.Preset.entries.indexOf(AntiBanConfig.preset).coerceAtLeast(0)
        XPopup.Builder(this).asCenterList(
            "防封预设（当前：${AntiBanConfig.preset.name}）",
            presets,
            null,
            currentIndex,
        ) { _, text ->
            runCatching { AntiBanConfig.preset = AntiBanConfig.Preset.valueOf(text) }
            AntiBanGuard.load()
            val t = AntiBanConfig.thresholds
            val dailyLabel = if (AntiBanConfig.isDailyForwardUnlimited()) {
                "无限制"
            } else {
                "${t.dailyForwardMax}"
            }
            XPopup.Builder(this@MainActivity).asConfirm(
                "已切换为 $text",
                "运行窗口 ${AntiBanConfig.activeWindowLabel()}\n" +
                    "日上限 $dailyLabel / 小时 ${t.hourlyForwardMax}\n" +
                    "轮询 ${t.pollIntervalSecLow}–${t.pollIntervalSecHigh}s\n" +
                    "今日已转发 ${AntiBanConfig.dailyForwardCount()} 次",
                null,
                null,
            ).show()
        }.show()
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