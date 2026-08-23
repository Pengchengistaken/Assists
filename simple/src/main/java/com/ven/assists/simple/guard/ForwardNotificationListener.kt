package com.ven.assists.simple.guard

import android.view.accessibility.AccessibilityEvent
import com.ven.assists.log.logAppend
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.simple.constants.WechatResourceIds
import com.ven.assists.simple.step.ContactList
import com.ven.assists.simple.step.Forward
import com.ven.assists.simple.step.StepTag
import com.ven.assists.stepper.StepManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 监听微信通知，匹配目标群后唤醒 Forward（替代高频界面轮询）。
 */
object ForwardNotificationListener : AssistsServiceListener {

    private const val WECHAT_PACKAGE = "com.tencent.mm"

    private val wakeRequested = AtomicBoolean(false)
    private val waitingForWake = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)

    fun register() {
        if (registered.compareAndSet(false, true)) {
            com.ven.assists.service.AssistsService.listeners.add(this)
            "Forward 通知监听已注册".logAppend()
        }
    }

    fun unregister() {
        if (registered.compareAndSet(true, false)) {
            com.ven.assists.service.AssistsService.listeners.remove(this)
            waitingForWake.set(false)
            wakeRequested.set(false)
        }
    }

    fun setWaitingForWake(waiting: Boolean) {
        waitingForWake.set(waiting)
    }

    fun consumeWakeRequest(): Boolean = wakeRequested.compareAndSet(true, false)

    fun hasWakeRequest(): Boolean = wakeRequested.get()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        if (event.packageName?.toString() != WECHAT_PACKAGE) return
        if (!AntiBanConfig.isEnabled()) return

        val texts = buildList {
            event.text?.forEach { if (!it.isNullOrBlank()) add(it.toString()) }
            event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (texts.isEmpty()) return

        val combined = texts.joinToString(" ")
        val groupMatched = combined.contains(ContactList.sourceGroupName)
        val followedHint = combined.contains(WechatResourceIds.ButtonTexts.FOLLOWED_PEOPLE)
            || combined.contains("关注")

        if (!groupMatched) return
        if (!followedHint && !combined.contains(ContactList.sourceGroupName)) return

        "收到目标群通知，唤醒 Forward: $combined".logAppend()
        wakeRequested.set(true)
        waitingForWake.set(false)

        if (StepManager.isStop || !Forward.isRunning()) {
            StepManager.isStop = false
            StepManager.execute(Forward::class.java, StepTag.STEP_2, begin = true)
        }
    }
}
