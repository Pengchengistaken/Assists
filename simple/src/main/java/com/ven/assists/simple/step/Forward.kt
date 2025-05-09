package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.getNodes
import com.ven.assists.AssistsCore.longClick
import com.ven.assists.AssistsCore.scrollForward
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
        private var DEBUG: Boolean ?= true
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
               if (DEBUG == true && kbqNode != null) { //调试：不需要小红点
                    kbqNode.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已找到并点击京东线报交流群")
                    return@next Step.get(StepTag.STEP_3)
                } else if (hasAh && kbqNode != null) {
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
            // 0. 聊天窗口向上滚动一次（查找ListView或RecyclerView并scrollForward）
            val listView = AssistsCore.getAllNodes().find {
                it.className == "android.widget.ListView" || it.className == "androidx.recyclerview.widget.RecyclerView"
            }
            if (listView != null) {
                val result = listView.scrollForward()
                if (result) {
                    LogWrapper.logAppend("已向上滚动一次聊天窗口")
                } else {
                    LogWrapper.logAppend("聊天窗口无法继续向上滚动")
                }
            } else {
                LogWrapper.logAppend("未找到聊天窗口")
            }

            // 1. 获取所有图片消息节点
            val allImageNodes = AssistsCore.getAllNodes().filter {
                it.viewIdResourceName == "com.tencent.mm:id/bkg" && it.isClickable && it.isLongClickable
            }
            if (allImageNodes.isEmpty()) {
                LogWrapper.logAppend("未找到图片消息，3秒后重试")
                return@next Step.get(StepTag.STEP_2, delay = 3000)
            }
            // 2. 取最后一个图片节点
            val lastImageNode = allImageNodes.last()
            // 3. 记录唯一特征（bounds）
            val currentImageBounds = lastImageNode.getBoundsInScreen().toShortString()
            LogWrapper.logAppend("当前图片唯一特征: $currentImageBounds，历史特征: $lastImageBounds")
            if (currentImageBounds == lastImageBounds) {
                LogWrapper.logAppend("图片未变化，无需转发，发送返回事件，3秒后重试")
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 3000)
            }
            lastImageBounds = currentImageBounds
            LogWrapper.logAppend("图片已变化，准备转发")

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
            return@next Step.get(StepTag.STEP_2, delay = 1000)
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
         collector.next(StepTag.STEP_6) { step ->
             LogWrapper.logAppend("选择转发对象")
             // 1. 查找并点击"多选"按钮
             val multiSelectNode = AssistsCore.getAllNodes().find {
                 it.className == "android.widget.TextView"
                     && it.text?.toString()?.contains("多选") == true
                     && it.isClickable
             }
             if (multiSelectNode != null) {
                 multiSelectNode.click()
                 LogWrapper.logAppend("已点击多选按钮")
                 return@next Step.get(StepTag.STEP_7, delay = 500)
             } else {
                 LogWrapper.logAppend("未找到多选按钮，重试")
                 return@next Step.get(StepTag.STEP_6, delay = 1000)
             }
         }

        if (DEBUG == true) {
            collector.next(StepTag.STEP_7) { step ->
                val group8Node = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.TextView"
                            && it.text?.toString()?.contains("文件传输助手") == true
                }
                if (group8Node != null) {
                    group8Node.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已点击文件传输助手")
                    return@next Step.get(StepTag.STEP_9, delay = 1000)
                } else {
                    LogWrapper.logAppend("未找到文件传输助手，重试")
                    return@next Step.get(StepTag.STEP_7, delay = 1000)
                }
            }
        } else {
            //7. 点击"京东优质线报8群"
            collector.next(StepTag.STEP_7) { step ->
                val group8Node = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.TextView"
                            && it.text?.toString()?.contains("京东优质线报8群") == true
                }
                if (group8Node != null) {
                    group8Node.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已点击京东优质线报8群")
                    return@next Step.get(StepTag.STEP_8, delay = 1000)
                } else {
                    LogWrapper.logAppend("未找到京东优质线报8群，重试")
                    return@next Step.get(StepTag.STEP_7, delay = 1000)
                }
            }

            //8. 点击"京东优质线报9群"
            collector.next(StepTag.STEP_8) { step ->
                val group9Node = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.TextView"
                            && it.text?.toString()?.contains("京东优质线报9群") == true
                }
                if (group9Node != null) {
                    group9Node.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已点击京东优质线报9群")
                    return@next Step.get(StepTag.STEP_9, delay = 1000)
                } else {
                    LogWrapper.logAppend("未找到京东优质线报9群，重试")
                    return@next Step.get(StepTag.STEP_8, delay = 1000)
                }
            }
        }

        //9. 点击"完成"按钮
        collector.next(StepTag.STEP_9) { step ->
            val finishBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                    && it.text?.toString()?.contains("完成") == true
                    && it.isClickable
            }
            if (finishBtn != null) {
                finishBtn.click()
                LogWrapper.logAppend("已点击完成按钮")
                return@next Step.get(StepTag.STEP_10, delay = 800)
            } else {
                LogWrapper.logAppend("未找到完成按钮，重试")
                return@next Step.get(StepTag.STEP_9, delay = 1000)
            }
        }

        //10. 点击"发送"按钮
        collector.next(StepTag.STEP_10) { step ->
            val sendBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                    && it.text?.toString()?.contains("发送") == true
                    && it.isClickable
            }
            if (sendBtn != null) {
                sendBtn.click()
                LogWrapper.logAppend("已点击发送按钮，准备查找最新文字消息")
                return@next Step.get(StepTag.STEP_11, delay = 1000)
            } else {
                LogWrapper.logAppend("未找到发送按钮，重试")
                return@next Step.get(StepTag.STEP_10, delay = 1000)
            }
        }

        // 11. 查找"阿汤哥会爆单吗@自在极意京粉线报"发送的最新一条文字消息，并log输出
        collector.next(StepTag.STEP_11) { step ->
            val allMsgBlocks = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.RelativeLayout" && it.viewIdResourceName == "com.tencent.mm:id/bn1"
            }

            val targetSender = "阿汤哥会爆单吗＠自在极意京粉线报"
            var latestMsg: String? = null

            // 倒序遍历，优先取最新
            for (msgBlock in allMsgBlocks.reversed()) {
                // 1. 查找昵称节点
                val senderNode = msgBlock.getNodes().find {
                    it.className == "android.widget.TextView"
                        && it.viewIdResourceName == "com.tencent.mm:id/brc"
                        && it.text?.toString()?.contains(targetSender) == true
                }
                if (senderNode != null) {
                    // 2. 查找消息内容节点
                    val contentNode = msgBlock.getNodes().find {
                        it.className == "android.widget.TextView"
                            && it.viewIdResourceName == "com.tencent.mm:id/bkl"
                            && !it.text.isNullOrBlank()
                    }
                    if (contentNode != null) {
                        latestMsg = contentNode.text?.toString()
                        break
                    }
                }
            }

            if (latestMsg != null) {
                val processedMsg = processText(latestMsg)
                LogWrapper.logAppend("阿汤哥最新消息内容: $processedMsg")
            } else {
                LogWrapper.logAppend("未找到目标发送者的消息")
            }
            return@next Step.none
        }
    }

    // 简单的文字处理函数，可根据需要扩展
    private fun processText(text: String): String {
        // 示例：去除首尾空格、换行、表情符号等
        return text.trim().replace("\n", " ")
    }
}