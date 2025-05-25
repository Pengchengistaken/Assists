package com.ven.assists.simple.step

import android.content.Context
import android.content.SharedPreferences

object ContactList {
    private const val PREF_NAME = "contact_settings"
    private const val KEY_GROUP_NAME = "group_name"
    private const val KEY_ROBOT_NAME = "robot_name"
    
    var sourceGroupName = "京东线报交流群"
    var sourceRobotName = "阿汤哥会爆单吗＠自在极意京粉线报"
    
    fun saveSettings(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_GROUP_NAME, sourceGroupName)
            putString(KEY_ROBOT_NAME, sourceRobotName)
            apply()
        }
    }
    
    fun loadSettings(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).apply {
            sourceGroupName = getString(KEY_GROUP_NAME, sourceGroupName) ?: sourceGroupName
            sourceRobotName = getString(KEY_ROBOT_NAME, sourceRobotName) ?: sourceRobotName
        }
    }
}