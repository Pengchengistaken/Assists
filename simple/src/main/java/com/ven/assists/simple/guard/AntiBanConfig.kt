package com.ven.assists.simple.guard

import android.content.Context
import android.content.SharedPreferences
import com.blankj.utilcode.util.Utils

/**
 * 防封策略配置，支持 Strict / Normal / Off 三档预设。
 */
object AntiBanConfig {

    enum class Preset {
        STRICT,
        NORMAL,
        OFF,
    }

    data class Thresholds(
        val activeHourStart: Int = 8,
        val activeMinuteStart: Int = 0,
        val activeHourEnd: Int = 23,
        val activeMinuteEnd: Int = 30,
        val dailyForwardMax: Int = Int.MAX_VALUE,
        val hourlyForwardMax: Int = 5,
        val sessionMaxMinutes: Int = 240,
        val cooldownAfterSessionMinLow: Int = 30,
        val cooldownAfterSessionMinHigh: Int = 60,
        val minGapBetweenForwardsSecLow: Int = 180,
        val minGapBetweenForwardsSecHigh: Int = 600,
        val pollIntervalSecLow: Int = 90,
        val pollIntervalSecHigh: Int = 240,
        val preClickDelaySecLow: Int = 120,
        val preClickDelaySecHigh: Int = 300,
        val stepDelayMsLow: Long = 1500,
        val stepDelayMsHigh: Long = 4000,
        val nurtureEveryForwards: Int = 5,
    )

    private const val PREF_NAME = "anti_ban_config"
    private const val KEY_PRESET = "preset"
    private const val KEY_DAILY_COUNT = "daily_forward_count"
    private const val KEY_DAILY_DATE = "daily_forward_date"
    private const val KEY_HOURLY_COUNT = "hourly_forward_count"
    private const val KEY_HOURLY_BUCKET = "hourly_forward_bucket"
    private const val KEY_LAST_FORWARD_AT = "last_forward_at"
    private const val KEY_SESSION_START = "session_start_at"
    private const val KEY_COOLDOWN_UNTIL = "cooldown_until"
    private const val KEY_TOTAL_FORWARDS = "total_forwards_for_nurture"

    private val prefs: SharedPreferences
        get() = Utils.getApp().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var preset: Preset
        get() = runCatching { Preset.valueOf(prefs.getString(KEY_PRESET, Preset.STRICT.name)!!) }
            .getOrDefault(Preset.STRICT)
        set(value) {
            prefs.edit().putString(KEY_PRESET, value.name).apply()
        }

    val thresholds: Thresholds
        get() = when (preset) {
            Preset.STRICT -> Thresholds()
            Preset.NORMAL -> Thresholds(
                dailyForwardMax = Int.MAX_VALUE,
                hourlyForwardMax = 10,
                pollIntervalSecLow = 60,
                pollIntervalSecHigh = 120,
                preClickDelaySecLow = 60,
                preClickDelaySecHigh = 120,
                minGapBetweenForwardsSecLow = 60,
                minGapBetweenForwardsSecHigh = 180,
            )
            Preset.OFF -> Thresholds(
                dailyForwardMax = Int.MAX_VALUE,
                hourlyForwardMax = Int.MAX_VALUE,
                sessionMaxMinutes = Int.MAX_VALUE,
                pollIntervalSecLow = 30,
                pollIntervalSecHigh = 30,
                preClickDelaySecLow = 0,
                preClickDelaySecHigh = 0,
                minGapBetweenForwardsSecLow = 0,
                minGapBetweenForwardsSecHigh = 0,
            )
        }

    fun isEnabled(): Boolean = preset != Preset.OFF

    fun activeWindowLabel(): String {
        val t = thresholds
        return formatTime(t.activeHourStart, t.activeMinuteStart) +
            "–" + formatTime(t.activeHourEnd, t.activeMinuteEnd)
    }

    fun isDailyForwardUnlimited(): Boolean = thresholds.dailyForwardMax >= Int.MAX_VALUE / 2

    private fun formatTime(hour: Int, minute: Int): String {
        return "%02d:%02d".format(hour, minute)
    }

    fun isWithinActiveWindow(now: Long = System.currentTimeMillis()): Boolean {
        val t = thresholds
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = t.activeHourStart * 60 + t.activeMinuteStart
        val end = t.activeHourEnd * 60 + t.activeMinuteEnd
        return minutes in start..end
    }

    fun load() {
        resetDailyIfNeeded()
        resetHourlyIfNeeded()
    }

    fun dailyForwardCount(): Int {
        resetDailyIfNeeded()
        return prefs.getInt(KEY_DAILY_COUNT, 0)
    }

    fun hourlyForwardCount(): Int {
        resetHourlyIfNeeded()
        return prefs.getInt(KEY_HOURLY_COUNT, 0)
    }

    fun lastForwardAt(): Long = prefs.getLong(KEY_LAST_FORWARD_AT, 0L)

    fun sessionStartAt(): Long = prefs.getLong(KEY_SESSION_START, 0L)

    fun cooldownUntil(): Long = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)

    fun totalForwardsForNurture(): Int = prefs.getInt(KEY_TOTAL_FORWARDS, 0)

    fun markSessionStart(now: Long = System.currentTimeMillis()) {
        if (sessionStartAt() == 0L) {
            prefs.edit().putLong(KEY_SESSION_START, now).apply()
        }
    }

    fun clearSession(now: Long = System.currentTimeMillis()) {
        prefs.edit().remove(KEY_SESSION_START).apply()
    }

    fun setCooldownUntil(until: Long) {
        prefs.edit().putLong(KEY_COOLDOWN_UNTIL, until).apply()
    }

    fun recordForward(now: Long = System.currentTimeMillis()) {
        resetDailyIfNeeded()
        resetHourlyIfNeeded()
        val daily = prefs.getInt(KEY_DAILY_COUNT, 0) + 1
        val hourly = prefs.getInt(KEY_HOURLY_COUNT, 0) + 1
        val nurture = prefs.getInt(KEY_TOTAL_FORWARDS, 0) + 1
        prefs.edit()
            .putInt(KEY_DAILY_COUNT, daily)
            .putInt(KEY_HOURLY_COUNT, hourly)
            .putLong(KEY_LAST_FORWARD_AT, now)
            .putInt(KEY_TOTAL_FORWARDS, nurture)
            .apply()
    }

    fun shouldNurtureAccount(): Boolean {
        val t = thresholds
        if (t.nurtureEveryForwards <= 0) return false
        return totalForwardsForNurture() > 0 && totalForwardsForNurture() % t.nurtureEveryForwards == 0
    }

    private fun resetDailyIfNeeded() {
        val today = dayKey(System.currentTimeMillis())
        val saved = prefs.getString(KEY_DAILY_DATE, null)
        if (saved != today) {
            prefs.edit()
                .putString(KEY_DAILY_DATE, today)
                .putInt(KEY_DAILY_COUNT, 0)
                .apply()
        }
    }

    private fun resetHourlyIfNeeded() {
        val bucket = hourKey(System.currentTimeMillis())
        val saved = prefs.getString(KEY_HOURLY_BUCKET, null)
        if (saved != bucket) {
            prefs.edit()
                .putString(KEY_HOURLY_BUCKET, bucket)
                .putInt(KEY_HOURLY_COUNT, 0)
                .apply()
        }
    }

    private fun dayKey(now: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    private fun hourKey(now: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}-${cal.get(java.util.Calendar.HOUR_OF_DAY)}"
    }
}
