package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.getNodes
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.common.LogWrapper
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl

/**
 * 转发功能实现
 */
class Forward : StepImpl() {
    override fun onImpl(collector: StepCollector) {
        //1. 打开微信
        collector.next(StepTag.STEP_1, isRunCoroutineIO = true) {
            LogWrapper.logAppend("启动微信")
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.instance?.startActivity(this)
            }
            return@next Step.get(StepTag.STEP_2)
        }

        //2. 点击聊天列表中的京东线报交流群
        collector.next(StepTag.STEP_2) { step ->
            // 1. 双击底部Tab"微信"
            val tabNodes = AssistsCore.findByText("微信")
            val screenHeight = com.blankj.utilcode.util.ScreenUtils.getScreenHeight()
            tabNodes.forEach { node ->
                val rect = node.getBoundsInScreen()
                if (rect.top > screenHeight * 0.75) {
                    node.findFirstParentClickable()?.let { parent ->
                        parent.click()
                        Thread.sleep(100)
                        parent.click()
                        LogWrapper.logAppend("已双击底部Tab微信")
                        return@forEach
                    }
                }
            }

            // 2. 查找所有聊天行（每一行的 LinearLayout，id=cj0）
            val allRows = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.LinearLayout" && it.viewIdResourceName == "com.tencent.mm:id/cj0"
            }

            // 3. 遍历每一行，递归查找 a_h 和 kbq
            for (row in allRows) {
                val allDescendants = row.getNodes() // 递归获取所有后代节点
                val hasAh = allDescendants.any { it.viewIdResourceName == "com.tencent.mm:id/a_h" }
                val kbqNode = allDescendants.find { 
                    it.viewIdResourceName == "com.tencent.mm:id/kbq" && (it.text?.contains("京东线报交流群") == true)
                }
                if (hasAh && kbqNode != null) {
                    kbqNode.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已找到并点击京东线报交流群")
                    return@next Step.get(StepTag.STEP_3)
                }
            }

            LogWrapper.logAppend("未找到京东线报交流群，10秒后重试")
            return@next Step.get(StepTag.STEP_2, delay = 10_000)
        }

        // //3. 长按消息
        // collector.next(StepTag.STEP_3) {
        //     LogWrapper.logAppend("长按消息")
        //     AssistsCore.getNodes().find { it.containsText("消息") }?.let {
        //         longClick(it)
        //     }
        //     return@next Step.get(StepTag.STEP_4)
        // }

        // //4. 点击转发按钮
        // collector.next(StepTag.STEP_4) {
        //     LogWrapper.logAppend("点击转发按钮")
        //     AssistsCore.getNodes().find { it.containsText("转发") }?.let {
        //         click(it)
        //     }
        //     return@next Step.get(StepTag.STEP_5)
        // }

        // //5. 选择转发对象
        // collector.next(StepTag.STEP_5) {
        //     LogWrapper.logAppend("选择转发对象")
        //     AssistsCore.getNodes().find { it.containsText("选择联系人") }?.let {
        //         click(it)
        //     }
        //     return@next Step.get(StepTag.STEP_6)
        // }

        // //6. 确认转发
        // collector.next(StepTag.STEP_6) {
        //     LogWrapper.logAppend("确认转发")
        //     AssistsCore.getNodes().find { it.containsText("发送") }?.let {
        //         click(it)
        //     }
        //     return@next null
        // }
    }
} 