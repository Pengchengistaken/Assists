package com.ven.assists.simple.step

import android.content.Context
import android.content.SharedPreferences

object ContactList {
    private const val PREF_NAME = "contact_settings"
    private const val KEY_GROUP_NAME = "group_name"
    private const val KEY_ROBOT_NAME = "robot_name"
    
    var sourceGroupName = "京东线报交流群"
    var sourceRobotNames: List<String> = listOf("阿汤哥会爆单吗＠自在极意线报", "京东优惠线报@自在极意线报")
    
    fun saveSettings(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_GROUP_NAME, sourceGroupName)
            putString(KEY_ROBOT_NAME, sourceRobotNames.joinToString(","))
            apply()
        }
    }
    
    fun loadSettings(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).apply {
            sourceGroupName = getString(KEY_GROUP_NAME, sourceGroupName) ?: sourceGroupName
            val robotNamesString = getString(KEY_ROBOT_NAME, null)
            sourceRobotNames = if (robotNamesString.isNullOrBlank()) {
                sourceRobotNames
            } else {
                robotNamesString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }
}