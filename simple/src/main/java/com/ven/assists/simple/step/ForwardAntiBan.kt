package com.ven.assists.simple.step

import android.view.accessibility.AccessibilityNodeInfo
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.log.logAppend
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.constants.WechatResourceIds
import com.ven.assists.simple.guard.AntiBanConfig
import com.ven.assists.simple.guard.AntiBanGuard
import com.ven.assists.simple.guard.ForwardNotificationListener
import com.ven.assists.simple.guard.HumanBehavior
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepManager
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Forward 防封辅助：守卫检查、Deep Sleep、拟人点击。
 */
internal object ForwardAntiBan {

    private val running = AtomicBoolean(false)

    fun isRunning(): Boolean = running.get()

    fun markRunning(active: Boolean) {
        running.set(active)
    }

    fun isDebugEnabled(): Boolean {
        return com.blankj.utilcode.util.AppUtils.isAppDebug() && debugFlag
    }

    private var debugFlag = false

    fun toggleDebugFlag(): Boolean {
        if (!com.blankj.utilcode.util.AppUtils.isAppDebug()) {
            "生产环境禁止 DEBUG 模式".logAppend()
            debugFlag = false
            return false
        }
        debugFlag = !debugFlag
        return debugFlag
    }

    suspend fun ensureGuardAllowed(): Boolean {
        AntiBanGuard.load()
        return when (val result = AntiBanGuard.assertAllowed()) {
            is AntiBanGuard.GuardResult.Allowed -> true
            is AntiBanGuard.GuardResult.Blocked -> {
                result.reason.logAppend()
                false
            }
        }
    }

    fun stepDelayMs(): Long = AntiBanGuard.randomStepDelayMs()

    fun enterDeepSleep(): Step {
        AssistsService.releaseScreenWakeLock()
        ForwardNotificationListener.setWaitingForWake(true)
        "进入 Deep Sleep，等待通知或慢轮询兜底".logAppend()
        return Step.get(StepTag.STEP_200, delay = 500)
    }

    suspend fun afterForwardCycle(): Step {
        AntiBanGuard.onForwardComplete()
        AssistsService.releaseScreenWakeLock()

        val t = AntiBanConfig.thresholds
        if (!AntiBanConfig.isDailyForwardUnlimited()
            && AntiBanConfig.dailyForwardCount() >= t.dailyForwardMax
        ) {
            AntiBanGuard.enterDailyQuotaCooldown()
            return enterDeepSleep()
        }

        if (AntiBanGuard.shouldNurtureAccount()) {
            "完成 ${AntiBanConfig.totalForwardsForNurture()} 次转发，插入养号浏览".logAppend()
            AssistsService.acquireScreenWakeLock()
            return Step.get(StepTag.STEP_201, delay = stepDelayMs())
        }

        AntiBanGuard.randomGapCooldown { left ->
            "转发冷却，距下次检查还有 ${left}s".logAppend()
        }
        return enterDeepSleep()
    }

    suspend fun humanClickParent(node: AccessibilityNodeInfo?): Boolean {
        val target = node?.findFirstParentClickable() ?: return false
        return HumanBehavior.humanClick(target) { it.logAppend() }
    }

    suspend fun humanClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            return HumanBehavior.humanClick(node) { it.logAppend() }
        }
        return humanClickParent(node)
    }

    suspend fun humanDoubleClickParent(node: AccessibilityNodeInfo?) {
        val target = node?.findFirstParentClickable() ?: return
        HumanBehavior.humanClick(target)
        delay(80L + kotlin.random.Random.nextLong(40, 120))
        HumanBehavior.humanClick(target)
    }

    suspend fun humanType(node: AccessibilityNodeInfo?, text: String?): Boolean {
        if (node == null || text.isNullOrBlank()) return false
        return HumanBehavior.humanType(node, text) { it.logAppend() }
    }

    suspend fun pauseBeforeReadChat() {
        delay(kotlin.random.Random.nextLong(2000, 5001))
    }

    suspend fun maybeStepPause() {
        HumanBehavior.maybeExtraPause()
    }
}
