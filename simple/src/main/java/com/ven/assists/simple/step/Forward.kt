package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.getNodes
import com.ven.assists.AssistsCore.longClick
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.common.LogWrapper
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl

/**
 * 转发功能实现
 */
class Forward : StepImpl() {
    // 用于存储最后一张图片的 bounds
    companion object {
        private var lastImageBounds: String? = null
    }

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

            // 3. 遍历每一行，递归查找 a_h（小红点） 和 kbq（群名）
            for (row in allRows) {
                val allDescendants = row.getNodes() // 递归获取所有后代节点
                val hasAh = allDescendants.any { it.viewIdResourceName == "com.tencent.mm:id/a_h" }
                val kbqNode = allDescendants.find { 
                    it.viewIdResourceName == "com.tencent.mm:id/kbq" && (it.text?.contains("京东线报交流群") == true)
                }
                if (hasAh && kbqNode != null) {
//                if (kbqNode != null) { //调试：不需要小红点
                    kbqNode.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已找到并点击京东线报交流群")
                    return@next Step.get(StepTag.STEP_3)
                }
            }

            LogWrapper.logAppend("未找到京东线报交流群或者群里没有新消息，10秒后重试")
            return@next Step.get(StepTag.STEP_2, delay = 10_000)
        }

        //3. 获取最后一张图片
        collector.next(StepTag.STEP_3) { step ->
            // 1. 获取所有图片消息节点
            val allImageNodes = AssistsCore.getAllNodes().filter {
                it.viewIdResourceName == "com.tencent.mm:id/bkg" && it.isClickable && it.isLongClickable
            }
            if (allImageNodes.isEmpty()) {
                LogWrapper.logAppend("未找到图片消息，3秒后重试")
                return@next Step.get(StepTag.STEP_3, delay = 3000)
            }
            // 2. 取最后一个图片节点
            val lastImageNode = allImageNodes.last()
            // 3. 记录唯一特征（bounds）
            lastImageBounds = lastImageNode.getBoundsInScreen().toShortString()
            LogWrapper.logAppend("已记录最后一张图片位置: $lastImageBounds")

            // 4. 长按图片
            lastImageNode.longClick()
            LogWrapper.logAppend("已长按图片，准备点击转发")

            // 5. 进入下一步，等待弹窗出现
            return@next Step.get(StepTag.STEP_4, delay = 2000)
        }

        //4. 查找并点击"转发"按钮
        collector.next(StepTag.STEP_4) { step ->
            // 1. 查找所有 text=转发 且 resource-id=obc 的 TextView
            val forwardTextNodes = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.TextView"
                    && it.viewIdResourceName == "com.tencent.mm:id/obc"
                    && it.text?.toString() == "转发"
            }
            if (forwardTextNodes.isNotEmpty()) {
                val forwardTextNode = forwardTextNodes.first()
                // 2. 找到可点击的父 LinearLayout
                val clickableParent = forwardTextNode.findFirstParentClickable()
                if (clickableParent != null) {
                    LogWrapper.logAppend("已定位到转发按钮，2秒后点击")
                    // 延时2秒后点击
                    return@next Step.get(StepTag.STEP_5, delay = 2000)
                }
            }
            LogWrapper.logAppend("未找到转发按钮，重试")
            return@next Step.get(StepTag.STEP_5, delay = 1000)
        }

        // STEP_5，真正执行点击
        collector.next(StepTag.STEP_5) { step ->
            val forwardTextNodes = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.TextView"
                    && it.viewIdResourceName == "com.tencent.mm:id/obc"
                    && it.text?.toString() == "转发"
            }
            if (forwardTextNodes.isNotEmpty()) {
                val forwardTextNode = forwardTextNodes.first()
                val clickableParent = forwardTextNode.findFirstParentClickable()
                if (clickableParent != null) {
                    clickableParent.click()
                    LogWrapper.logAppend("已点击转发按钮")
                    return@next Step.get(StepTag.STEP_6)
                }
            }
            LogWrapper.logAppend("已点击转发按钮，下一步")
            return@next Step.get(StepTag.STEP_6, delay = 3000)
        }

         //6. 选择转发对象
         collector.next(StepTag.STEP_6) {
             LogWrapper.logAppend("选择转发对象")
             return@next Step.none
         }

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