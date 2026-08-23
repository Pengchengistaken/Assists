package com.ven.assists.simple.guard

import com.ven.assists.log.logAppend
import java.util.Calendar

/**
 * 防封守卫：时间窗、配额、会话冷却、随机延迟。
 */
object AntiBanGuard {

    sealed class GuardResult {
        data object Allowed : GuardResult()
        data class Blocked(val reason: String) : GuardResult()
    }

    fun load() {
        AntiBanConfig.load()
    }

    fun checkBlockedReason(now: Long = System.currentTimeMillis()): String? {
        if (!AntiBanConfig.isEnabled()) return null
        val t = AntiBanConfig.thresholds

        if (now < AntiBanConfig.cooldownUntil()) {
            val leftSec = ((AntiBanConfig.cooldownUntil() - now) / 1000).coerceAtLeast(1)
            return "冷却中，剩余约 ${leftSec}s"
        }

        if (!AntiBanConfig.isWithinActiveWindow(now)) {
            return "非允许时段（${AntiBanConfig.activeWindowLabel()}）"
        }

        if (!AntiBanConfig.isDailyForwardUnlimited()
            && AntiBanConfig.dailyForwardCount() >= t.dailyForwardMax
        ) {
            return "已达日转发上限 ${t.dailyForwardMax}"
        }

        if (AntiBanConfig.hourlyForwardCount() >= t.hourlyForwardMax) {
            return "已达小时转发上限 ${t.hourlyForwardMax}"
        }

        val sessionStart = AntiBanConfig.sessionStartAt()
        if (sessionStart > 0) {
            val sessionMinutes = (now - sessionStart) / 60_000
            if (sessionMinutes >= t.sessionMaxMinutes) {
                enterSessionCooldown(now)
                return "单次会话已达 ${t.sessionMaxMinutes} 分钟，进入冷却"
            }
        }

        val lastForward = AntiBanConfig.lastForwardAt()
        if (lastForward > 0) {
            val gapSec = (now - lastForward) / 1000
            if (gapSec < t.minGapBetweenForwardsSecLow) {
                return "距上次转发过近，需再等 ${t.minGapBetweenForwardsSecLow - gapSec}s"
            }
        }

        return null
    }

    fun assertAllowed(): GuardResult {
        val reason = checkBlockedReason()
        return if (reason == null) {
            GuardResult.Allowed
        } else {
            reason.logAppend()
            GuardResult.Blocked(reason)
        }
    }

    fun onForwardSessionStart(now: Long = System.currentTimeMillis()) {
        AntiBanConfig.markSessionStart(now)
    }

    fun onForwardComplete(now: Long = System.currentTimeMillis()) {
        AntiBanConfig.recordForward(now)
    }

    fun enterSessionCooldown(now: Long = System.currentTimeMillis()) {
        val t = AntiBanConfig.thresholds
        val minutes = kotlin.random.Random.nextInt(
            t.cooldownAfterSessionMinLow,
            t.cooldownAfterSessionMinHigh + 1,
        )
        val until = now + minutes * 60_000L
        AntiBanConfig.setCooldownUntil(until)
        AntiBanConfig.clearSession()
        "会话冷却 ${minutes} 分钟".logAppend()
    }

    fun enterDailyQuotaCooldown(now: Long = System.currentTimeMillis()) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, AntiBanConfig.thresholds.activeHourStart)
        cal.set(Calendar.MINUTE, AntiBanConfig.thresholds.activeMinuteStart)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        AntiBanConfig.setCooldownUntil(cal.timeInMillis)
        AntiBanConfig.clearSession()
        "日配额已满，冷却至次日允许时段".logAppend()
    }

    fun randomStepDelayMs(): Long = HumanBehavior.randomStepDelayMs()

    fun randomPollIntervalSec(): Int = HumanBehavior.randomPollIntervalSec()

    suspend fun randomPreClickDelay(log: (Int) -> Unit) {
        val sec = HumanBehavior.randomPreClickDelaySec()
        for (left in sec downTo 1) {
            log(left)
            kotlinx.coroutines.delay(1000)
        }
    }

    suspend fun randomGapCooldown(log: (Int) -> Unit) {
        val sec = HumanBehavior.randomGapBetweenForwardsSec()
        for (left in sec downTo 1) {
            log(left)
            kotlinx.coroutines.delay(1000)
        }
    }

    fun shouldNurtureAccount(): Boolean = AntiBanConfig.shouldNurtureAccount()
}
