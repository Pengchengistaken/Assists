package com.ven.assists.simple.guard

import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.AssistsCore.setNodeText
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 拟人化交互：随机延迟、手势点击、逐字输入。
 */
object HumanBehavior {

    private val random = Random.Default

    fun randomOffset(): Pair<Float, Float> {
        val screenW = ScreenUtils.getScreenWidth().toFloat()
        val ratio = random.nextDouble(0.03, 0.08).toFloat()
        val signX = if (random.nextBoolean()) 1f else -1f
        val signY = if (random.nextBoolean()) 1f else -1f
        return screenW * ratio * signX to screenW * ratio * signY
    }

    fun randomStepDelayMs(): Long {
        val t = AntiBanConfig.thresholds
        return random.nextLong(t.stepDelayMsLow, t.stepDelayMsHigh + 1)
    }

    fun randomPollIntervalSec(): Int {
        val t = AntiBanConfig.thresholds
        return random.nextInt(t.pollIntervalSecLow, t.pollIntervalSecHigh + 1)
    }

    fun randomPreClickDelaySec(): Int {
        val t = AntiBanConfig.thresholds
        return random.nextInt(t.preClickDelaySecLow, t.preClickDelaySecHigh + 1)
    }

    fun randomGapBetweenForwardsSec(): Int {
        val t = AntiBanConfig.thresholds
        return random.nextInt(t.minGapBetweenForwardsSecLow, t.minGapBetweenForwardsSecHigh + 1)
    }

    suspend fun randomDelaySeconds(rangeSec: IntRange) {
        val sec = random.nextInt(rangeSec.first, rangeSec.last + 1)
        for (left in sec downTo 1) {
            delay(1000)
        }
    }

    suspend fun randomDelaySecondsWithLog(rangeSec: IntRange, label: (Int) -> String) {
        val sec = random.nextInt(rangeSec.first, rangeSec.last + 1)
        for (left in sec downTo 1) {
            label(left)
            delay(1000)
        }
    }

    /** 步骤间偶发长停顿（约 5% 概率多等 5–10 秒） */
    suspend fun maybeExtraPause() {
        if (random.nextInt(100) < 5) {
            delay(random.nextLong(5000, 10001))
        } else {
            delay(random.nextLong(1000, 3001))
        }
    }

    suspend fun humanClick(
        node: AccessibilityNodeInfo,
        log: ((String) -> Unit)? = null,
    ): Boolean {
        val (ox, oy) = randomOffset()
        val duration = random.nextLong(80, 181)
        log?.invoke("拟人点击 duration=${duration}ms")
        return node.nodeGestureClick(offsetX = ox, offsetY = oy, duration = duration)
    }

    suspend fun humanType(
        node: AccessibilityNodeInfo,
        text: String,
        log: ((String) -> Unit)? = null,
    ): Boolean {
        if (text.isEmpty()) return false
        log?.invoke("拟人输入 ${text.length} 字")
        val builder = StringBuilder()
        for (ch in text) {
            builder.append(ch)
            node.setNodeText(builder.toString())
            delay(random.nextLong(50, 151))
        }
        return true
    }
}
