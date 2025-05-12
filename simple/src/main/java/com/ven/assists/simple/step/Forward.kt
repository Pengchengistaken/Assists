package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.getNodes
import com.ven.assists.AssistsCore.longClick
import com.ven.assists.AssistsCore.nodeGestureClickByDouble
import com.ven.assists.AssistsCore.scrollForward
import com.ven.assists.AssistsCore.setNodeText
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.common.LogWrapper
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl
import com.ven.assists.window.AssistsWindowManager
import kotlinx.coroutines.delay

/**
 * 转发功能实现
 */
class Forward : StepImpl() {
    // 用于存储最后一张图片的 bounds
    companion object {
        private var lastImageBounds: String? = null
        private var lastTextMsg: String? = null // 新增：记录上一次的文字消息内容
        private var DEBUG: Boolean ?= false
        private var isLastMsgText: Boolean ?= false
        private var retryCount: Int = 0 // 新增：重试计数器
        private var stepRetryCount: Int = 0 // 新增：步骤重试计数器
        private const val MAX_STEP_RETRY = 10 // 新增：最大步骤重试次数
    }

    override fun onImpl(collector: StepCollector) {
        //1. 打开微信
        collector.next(StepTag.STEP_1, isRunCoroutineIO = true) {
            LogWrapper.logAppend("STEP_1: 开始执行 - 启动微信")
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
            LogWrapper.logAppend("STEP_2: 开始执行 - 查找并点击京东线报交流群")
            stepRetryCount++
            if (stepRetryCount > MAX_STEP_RETRY) {
                LogWrapper.logAppend("STEP_2 重试次数超过限制，跳转到恢复步骤")
                stepRetryCount = 0
                return@next Step.get(StepTag.STEP_100)
            }
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
                    retryCount = 0 // 重置计数器
                    return@next Step.get(StepTag.STEP_3)
                } else if (hasAh && kbqNode != null) {
                   LogWrapper.logAppend("京东线报交流群有新消息，2分钟后点击并进入执行。")
                   delay(120_000)
                   kbqNode.findFirstParentClickable()?.click()
                   retryCount = 0 // 重置计数器
                   return@next Step.get(StepTag.STEP_3)
               }
            }

            retryCount++
            LogWrapper.logAppend("未找到京东线报交流群或者群里没有新消息，当前重试次数: $retryCount/60")
            
            if (retryCount >= 60) {
                LogWrapper.logAppend("已达到最大重试次数(60次)，返回STEP_1")
                retryCount = 0 // 重置计数器
                return@next Step.get(StepTag.STEP_1, delay = 3000)
            }
            LogWrapper.logAppend("1分钟后再次检查。")
            return@next Step.get(StepTag.STEP_2, delay = 60_000)
        }

        //3. 获取最后一张图片
        collector.next(StepTag.STEP_3) { step ->
            LogWrapper.logAppend("STEP_3: 开始执行 - 获取最后一张图片")
            stepRetryCount++
            if (stepRetryCount > MAX_STEP_RETRY) {
                LogWrapper.logAppend("STEP_3 重试次数超过限制，跳转到恢复步骤")
                stepRetryCount = 0
                return@next Step.get(StepTag.STEP_100)
            }
            //滑动一下聊天窗口
            AssistsWindowManager.nonTouchableByAll()
            delay(250)
            val x = ScreenUtils.getAppScreenWidth() / 2F
            val distance = ScreenUtils.getAppScreenHeight() / 2F
            val startY = distance + distance / 2F
            val endY = distance - distance / 2F
            LogWrapper.logAppend("滑动：$x/$startY,$x/$endY")
            AssistsCore.gesture(
                floatArrayOf(x, startY), floatArrayOf(x, endY), 0, 1000L
            )
            AssistsWindowManager.touchableByAll()
            delay(1000)

            // 1. 获取所有图片消息节点
            val allImageNodes = AssistsCore.getAllNodes().filter {
                it.viewIdResourceName == "com.tencent.mm:id/bkg" && it.isClickable && it.isLongClickable
            }
            if (allImageNodes.isEmpty()) {
                LogWrapper.logAppend("未找到图片消息，3秒后重试")
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 3000)
            }
            // 2. 取最后一个图片节点
            val lastImageNode = allImageNodes.last()
            // 3. 记录唯一特征（bounds）
            val currentImageBounds = lastImageNode.getBoundsInScreen().toShortString()
            LogWrapper.logAppend("当前图片唯一特征: $currentImageBounds，历史特征: $lastImageBounds")
            if (currentImageBounds == lastImageBounds) {
                LogWrapper.logAppend("图片未变化，无需转发，发送返回事件，30秒后重试")
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 30000)
            }
            lastImageBounds = currentImageBounds
            LogWrapper.logAppend("图片已变化，准备转发")

            // 4. 长按图片
            if (lastImageNode.isVisibleToUser && lastImageNode.isLongClickable && lastImageNode.isEnabled) {
                LogWrapper.logAppend("节点可交互，准备长按: bounds=${lastImageNode.getBoundsInScreen()}")
                lastImageNode.longClick()
                return@next Step.get(StepTag.STEP_4, delay = 2000)
            } else {
                LogWrapper.logAppend("节点不可交互，isVisibleToUser=${lastImageNode.isVisibleToUser}, isLongClickable=${lastImageNode.isLongClickable}, isEnabled=${lastImageNode.isEnabled}")
                // 延迟重试
                return@next Step.get(StepTag.STEP_3, delay = 2000)
            }
        }

        //4. 查找并点击"转发"按钮
        collector.next(StepTag.STEP_4) { step ->
            LogWrapper.logAppend("STEP_4: 开始执行 - 查找并点击转发按钮")
            stepRetryCount++
            if (stepRetryCount > MAX_STEP_RETRY) {
                LogWrapper.logAppend("STEP_4 重试次数超过限制，跳转到恢复步骤")
                stepRetryCount = 0
                return@next Step.get(StepTag.STEP_100)
            }
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
            Thread.sleep(1500)
            lastImageBounds = null
            AssistsCore.back()
            return@next Step.get(StepTag.STEP_2, delay = 2000)
        }

        // STEP_5，真正执行点击
        collector.next(StepTag.STEP_5) { step ->
            LogWrapper.logAppend("STEP_5: 开始执行 - 点击转发按钮")
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
                    isLastMsgText = false
                    LogWrapper.logAppend("设置 isLastMsgText 为 false")
                    return@next Step.get(StepTag.STEP_6)
                }
            }
            LogWrapper.logAppend("已点击转发按钮，下一步")
            return@next Step.get(StepTag.STEP_6, delay = 3000)
        }

         //6. 选择转发对象
         collector.next(StepTag.STEP_6) { step ->
             LogWrapper.logAppend("STEP_6: 开始执行 - 选择转发对象")
             stepRetryCount++
             if (stepRetryCount > MAX_STEP_RETRY) {
                 LogWrapper.logAppend("STEP_6 重试次数超过限制，跳转到恢复步骤")
                 stepRetryCount = 0
                 return@next Step.get(StepTag.STEP_100)
             }
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
                 return@next Step.get(StepTag.STEP_7, delay = 2000)
             } else {
                 LogWrapper.logAppend("未找到多选按钮，重试")
                 return@next Step.get(StepTag.STEP_6, delay = 2000)
             }
         }

        if (DEBUG == true) {
            collector.next(StepTag.STEP_7) { step ->
                LogWrapper.logAppend("STEP_7: 开始执行 - 查找并点击文件传输助手")
                val group8Node = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.TextView"
                            && it.text?.toString()?.contains("文件传输助手") == true
                }
                if (group8Node != null) {
                    group8Node.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已点击文件传输助手")
                    return@next Step.get(StepTag.STEP_9, delay = 2000)
                } else {
                    LogWrapper.logAppend("未找到文件传输助手，重试")
                    return@next Step.get(StepTag.STEP_7, delay = 2000)
                }
            }
        } else {
            //7. 点击"京东优质线报8群"
            collector.next(StepTag.STEP_7) { step ->
                LogWrapper.logAppend("STEP_7: 开始执行 - 查找并点击京东优质线报8群")
                val group8Node = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.TextView"
                            && it.text?.toString()?.contains("京东优质线报8群") == true
                }
                if (group8Node != null) {
                    group8Node.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已点击京东优质线报8群")
                    return@next Step.get(StepTag.STEP_8, delay = 2000)
                } else {
                    LogWrapper.logAppend("未找到京东优质线报8群，重试")
                    return@next Step.get(StepTag.STEP_7, delay = 2000)
                }
            }

            //8. 点击"京东优质线报9群"
            collector.next(StepTag.STEP_8) { step ->
                LogWrapper.logAppend("STEP_8: 开始执行 - 查找并点击京东优质线报9群")
                val group9Node = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.TextView"
                            && it.text?.toString()?.contains("京东优质线报9群") == true
                }
                if (group9Node != null) {
                    group9Node.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已点击京东优质线报9群")
                    return@next Step.get(StepTag.STEP_9, delay = 2000)
                } else {
                    LogWrapper.logAppend("未找到京东优质线报9群，重试")
                    return@next Step.get(StepTag.STEP_8, delay = 2000)
                }
            }
        }

        //9. 点击"完成"按钮
        collector.next(StepTag.STEP_9) { step ->
            LogWrapper.logAppend("STEP_9: 开始执行 - 查找并点击完成按钮")
            val finishBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                    && it.text?.toString()?.contains("完成") == true
                    && it.isClickable
            }
            if (finishBtn != null) {
                finishBtn.click()
                LogWrapper.logAppend("已点击完成按钮")
                return@next Step.get(StepTag.STEP_10, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到完成按钮，重试")
                return@next Step.get(StepTag.STEP_9, delay = 2000)
            }
        }

        //10. 点击"发送"按钮
        collector.next(StepTag.STEP_10) { step ->
            LogWrapper.logAppend("STEP_10: 开始执行 - 查找并点击发送按钮")
            val sendBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                    && it.text?.toString()?.contains("发送") == true
                    && it.isClickable
            }
            if (sendBtn != null) {
                sendBtn.click()
                if (isLastMsgText == true) {
                    LogWrapper.logAppend("已点击发送按钮，准备查找最新图片消息")
                    repeat(8) { attempt ->
                        if (AssistsCore.back()) {
                            LogWrapper.logAppend("返回第 ${attempt + 1} 次")
                        }
                        Thread.sleep(2000)
                        val wechatNode = AssistsCore.getAllNodes().find {
                            it.className == "android.widget.TextView"
                                    && it.viewIdResourceName == "android:id/text1"
                                    && it.text?.toString() == "微信"
                        }
                        if (wechatNode != null) {
                            LogWrapper.logAppend("到了微信主页面。")
                            return@next Step.get(StepTag.STEP_2, delay = 3000)
                        }
                    }
                    LogWrapper.logAppend("没有到微信主页面。")
                    return@next Step.get(StepTag.STEP_1, delay = 3000)
                } else {
                    LogWrapper.logAppend("已点击发送按钮，准备查找最新文字消息")
                    return@next Step.get(StepTag.STEP_11, delay = 2000)
                }
            } else {
                LogWrapper.logAppend("未找到发送按钮，重试")
                return@next Step.get(StepTag.STEP_10, delay = 2000)
            }
        }

        // 11. 查找"阿汤哥会爆单吗@自在极意京粉线报"发送的最新一条文字消息，并log输出
        collector.next(StepTag.STEP_11) { step ->
            LogWrapper.logAppend("STEP_11: 开始执行 - 查找阿汤哥最新文字消息")
            val allMsgBlocks = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.RelativeLayout" && it.viewIdResourceName == "com.tencent.mm:id/bn1"
            }

            val targetSender = "阿汤哥会爆单吗＠自在极意京粉线报"
            var latestMsg: String? = null
            var latestMsgNode: android.view.accessibility.AccessibilityNodeInfo? = null
            var latestMsgIndex = -1
            var latestImageIndex = -1

            // 倒序遍历，优先取最新
            for ((i, msgBlock) in allMsgBlocks.withIndex().reversed()) {
                // 1. 查找图片节点
                val imageNode = msgBlock.getNodes().find {
                    it.viewIdResourceName == "com.tencent.mm:id/bkg"
                }
                if (latestImageIndex == -1 && imageNode != null) {
                    latestImageIndex = i
                }

                // 2. 查找阿汤哥文字消息节点
                val senderNode = msgBlock.getNodes().find {
                    it.className == "android.widget.TextView"
                        && it.viewIdResourceName == "com.tencent.mm:id/brc"
                        && it.text?.toString()?.contains(targetSender) == true
                }
                val contentNode = msgBlock.getNodes().find {
                    it.className == "android.widget.TextView"
                        && it.viewIdResourceName == "com.tencent.mm:id/bkl"
                        && !it.text.isNullOrBlank()
                }
                if (latestMsgIndex == -1 && senderNode != null && contentNode != null) {
                    latestMsg = contentNode.text?.toString()
                    latestMsgNode = contentNode
                    latestMsgIndex = i
                }
            }

            // 判断是否需要back
            if (latestMsg == null || latestMsg == lastTextMsg || (latestMsgIndex < latestImageIndex && latestImageIndex != -1)) {
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 2000)
            }

            // 内容有变化，复制内容并处理
            lastTextMsg = latestMsg
            val processedMsg = processAtangText(latestMsg)
            latestMsgNode?.let { node ->
                val clipboard = AssistsService.instance?.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("msg", processedMsg)
                clipboard?.setPrimaryClip(clip)
                LogWrapper.logAppend("已复制最新消息到剪贴板: $processedMsg")
                LogWrapper.logAppend("阿汤哥最新消息内容: $processedMsg")
                AssistsCore.back()
            }
            return@next Step.get(StepTag.STEP_12, delay = 2000)
        }

        //12. 进入京粉并自动发消息
        collector.next(StepTag.STEP_12) { step ->
            LogWrapper.logAppend("STEP_12: 开始执行 - 查找并进入京粉")
            stepRetryCount++
            if (stepRetryCount > MAX_STEP_RETRY) {
                LogWrapper.logAppend("STEP_12 重试次数超过限制，跳转到恢复步骤")
                stepRetryCount = 0
                return@next Step.get(StepTag.STEP_100)
            }
            // 1. 查找所有聊天行（每一行的 LinearLayout，id=cj0）
            val allRows = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.LinearLayout" && it.viewIdResourceName == "com.tencent.mm:id/cj0"
            }

            // 2. 遍历每一行，查找 kbq（群名）
            for (row in allRows) {
                val allDescendants = row.getNodes() // 递归获取所有后代节点
                val kbqNode = allDescendants.find { 
                    it.viewIdResourceName == "com.tencent.mm:id/kbq" && (it.text?.contains("京粉") == true)
                }
                if (kbqNode != null) {
                    kbqNode.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已找到并点击京粉")
                    return@next Step.get(StepTag.STEP_13)
                }
            }

            // 3. 如果没找到，尝试滚动列表
            val listContainer = AssistsCore.getAllNodes().find {
                it.className == "android.widget.ListView" &&
                it.viewIdResourceName == "com.tencent.mm:id/i3y"
            }
            
            if (listContainer != null && listContainer.scrollForward()) {
                LogWrapper.logAppend("未找到京粉，向下滚动后重试")
                return@next Step.get(StepTag.STEP_12, delay = 2000)
            }
            return@next Step.get(StepTag.STEP_13, delay = 2000)

        }

        //13. 切换到发消息并粘贴内容
        collector.next(StepTag.STEP_13) { step ->
            LogWrapper.logAppend("STEP_13: 开始执行 - 切换到发消息")
            val switchMsgNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.ImageView"
                    && it.viewIdResourceName == "com.tencent.mm:id/blp"
                    && it.isClickable
                    && it.contentDescription?.contains("切换到发消息") == true
            }
            if (switchMsgNode != null) {
                switchMsgNode.click()
                LogWrapper.logAppend("已点击切换到发消息")
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到切换到发消息按钮，重试")
                return@next Step.get(StepTag.STEP_13, delay = 2000)
            }
        }

        //14. 点击输入框并粘贴内容
        collector.next(StepTag.STEP_14) { step ->
            LogWrapper.logAppend("STEP_14: 开始执行 - 点击输入框并粘贴内容")
            stepRetryCount++
            if (stepRetryCount > MAX_STEP_RETRY) {
                LogWrapper.logAppend("STEP_14 重试次数超过限制，跳转到恢复步骤")
                stepRetryCount = 0
                return@next Step.get(StepTag.STEP_100)
            }
            val editTextNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.EditText"
                    && it.viewIdResourceName == "com.tencent.mm:id/bkk"
                    && it.isClickable && it.isEnabled && it.isFocusable
            }
            if (editTextNode != null) {
                // 长按输入框
                val longClickResult = editTextNode.longClick()
                LogWrapper.logAppend("长按输入框结果: $longClickResult")
                delay(500) // 等待菜单出现

                // 点击固定坐标的"粘贴"按钮
                val clickResult = AssistsCore.gestureClick(120f, 2180f)
                LogWrapper.logAppend("点击粘贴按钮结果: $clickResult")
                if (clickResult) {
                    LogWrapper.logAppend("已点击粘贴按钮")
                    return@next Step.get(StepTag.STEP_15, delay = 2000)
                } else {
                    LogWrapper.logAppend("点击粘贴按钮失败，重试")
                    return@next Step.get(StepTag.STEP_14, delay = 2000)
                }
            } else {
                LogWrapper.logAppend("未找到输入框，重试")
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            }
        }

        //15. 点击发送按钮
        collector.next(StepTag.STEP_15) { step ->
            LogWrapper.logAppend("STEP_15: 开始执行 - 点击发送按钮")
            delay(2000) //延迟 2 秒让节点加载
            val sendBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                    && it.text?.contains("发送") == true && it.isClickable
            }
            if (sendBtn != null) {
                sendBtn.click()
                LogWrapper.logAppend("已点击发送按钮，进入下一步")
                return@next Step.get(StepTag.STEP_16, delay = 5000)
            } else {
                LogWrapper.logAppend("未找到发送按钮，重试")
                return@next Step.get(StepTag.STEP_15, delay = 2000)
            }
        }

        collector.next(StepTag.STEP_16) { step ->
            LogWrapper.logAppend("STEP_16: 开始执行 - 查找京粉最新文字消息")
            
            // 1. 获取所有消息块
            val allMsgBlocks = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.RelativeLayout" && it.viewIdResourceName == "com.tencent.mm:id/bn1"
            }

            // 2. 查找京粉发送的最新文字消息
            var latestMsg: String? = null
            var latestMsgNode: android.view.accessibility.AccessibilityNodeInfo? = null

            // 倒序遍历，优先取最新
            for (msgBlock in allMsgBlocks.reversed()) {
                // 查找消息内容节点
                val contentNode = msgBlock.getNodes().find {
                    it.className == "android.widget.TextView"
                        && it.viewIdResourceName == "com.tencent.mm:id/bkl"
                        && !it.text.isNullOrBlank()
                }
                
                if (contentNode != null) {
                    latestMsg = contentNode.text?.toString()
                    latestMsgNode = contentNode
                    break
                }
            }

            val finalLatestMsg = latestMsg
            if (finalLatestMsg != null) {
                // 3. 处理消息内容
                val processedMsg = processJingfenText(finalLatestMsg)
                if (processedMsg.isNotEmpty()) {
                    // 4. 复制到剪贴板
                    latestMsgNode?.let { node ->
                        val clipboard = AssistsService.instance?.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("msg", processedMsg)
                        clipboard?.setPrimaryClip(clip)
                        LogWrapper.logAppend("已复制处理后的消息到剪贴板: $processedMsg")
                    }
                    AssistsCore.back()
                    return@next Step.get(StepTag.STEP_17, delay = 2000)
                } else {
                    LogWrapper.logAppend("消息内容不包含jd.com链接，跳过处理")
                    AssistsCore.back()
                    return@next Step.get(StepTag.STEP_17, delay = 2000)
                }
            } else {
                LogWrapper.logAppend("未找到京粉的文字消息，重试")
                return@next Step.get(StepTag.STEP_16, delay = 2000)
            }
        }

        //17. 双击顶部微信并进入文件传输助手
        collector.next(StepTag.STEP_17) { step ->
            LogWrapper.logAppend("STEP_17: 开始执行 - 双击顶部微信并进入文件传输助手")
            stepRetryCount++
            if (stepRetryCount > MAX_STEP_RETRY) {
                LogWrapper.logAppend("STEP_17 重试次数超过限制，跳转到恢复步骤")
                stepRetryCount = 0
                return@next Step.get(StepTag.STEP_100)
            }
            // 1. 查找顶部的"微信"文本
            val wechatNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.TextView"
                    && it.viewIdResourceName == "android:id/text1"
                    && it.text?.toString() == "微信"
            }
            
            if (wechatNode != null) {
                // 双击"微信"
                wechatNode.findFirstParentClickable()?.let { parent ->
                    parent.click()
                    delay(100)
                    parent.click()
                    LogWrapper.logAppend("已双击顶部微信")
                }
            } else {
                LogWrapper.logAppend("未找到顶部微信，重试")
                return@next Step.get(StepTag.STEP_17, delay = 2000)
            }

            // 2. 查找并点击"文件传输助手"
            val fileTransferNode = AssistsCore.getAllNodes().find {
                it.className == "android.view.View"
                    && it.viewIdResourceName == "com.tencent.mm:id/kbq"
                    && it.text?.toString() == "文件传输助手"
            }

            if (fileTransferNode != null) {
                fileTransferNode.findFirstParentClickable()?.click()
                LogWrapper.logAppend("已点击文件传输助手")
                return@next Step.get(StepTag.STEP_18, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到文件传输助手，重试")
                return@next Step.get(StepTag.STEP_17, delay = 2000)
            }
        }

        //18. 在文件传输助手中粘贴内容并发送
        collector.next(StepTag.STEP_18) { step ->
            LogWrapper.logAppend("STEP_18: 开始执行 - 在文件传输助手中粘贴内容并发送")
            // 1. 查找输入框
            val editTextNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.EditText"
                    && it.viewIdResourceName == "com.tencent.mm:id/bkk"
                    && it.isClickable && it.isEnabled && it.isFocusable
            }
            
            if (editTextNode != null) {
                // 2. 长按输入框
                val longClickResult = editTextNode.longClick()
                LogWrapper.logAppend("长按输入框结果: $longClickResult")
                delay(500) // 等待菜单出现

                // 3. 点击固定坐标的"粘贴"按钮
                val clickResult = AssistsCore.gestureClick(120f, 2180f)
                LogWrapper.logAppend("点击粘贴按钮结果: $clickResult")
                if (clickResult) {
                    LogWrapper.logAppend("已点击粘贴按钮")
                    delay(1000) // 等待粘贴完成

                    // 4. 查找并点击发送按钮
                    val sendBtn = AssistsCore.getAllNodes().find {
                        (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                            && it.text?.contains("发送") == true && it.isClickable
                    }
                    if (sendBtn != null) {
                        sendBtn.click()
                        LogWrapper.logAppend("已点击发送按钮，完成所有步骤")
                        return@next Step.get(StepTag.STEP_19, delay = 2000)
                    } else {
                        LogWrapper.logAppend("未找到发送按钮，重试")
                        return@next Step.get(StepTag.STEP_18, delay = 2000)
                    }
                } else {
                    LogWrapper.logAppend("点击粘贴按钮失败，重试")
                    return@next Step.get(StepTag.STEP_18, delay = 2000)
                }
            } else {
                LogWrapper.logAppend("未找到输入框，重试")
                return@next Step.get(StepTag.STEP_18, delay = 2000)
            }
        }

        //19. 查找最新消息并转发
        collector.next(StepTag.STEP_19) { step ->
            LogWrapper.logAppend("STEP_19: 开始执行 - 查找最新消息并转发")
            // 3. 双击消息
            AssistsCore.gestureClick(800f, 2050f)
            delay(100)
            AssistsCore.gestureClick(800f, 2050f)
            LogWrapper.logAppend("双击右下角，展开消息全屏，开始分享")
            delay(3000)

            // 4. 查找并点击"分享"按钮
            val shareButton = AssistsCore.getAllNodes().find {
                it.className == "android.widget.ImageButton"
                        && it.contentDescription?.toString() == "分享"
                        && it.isClickable
            }

            if (shareButton != null) {
                shareButton.click()
                LogWrapper.logAppend("已点击分享按钮")
                isLastMsgText = true
                LogWrapper.logAppend("设置 isLastMsgText 为 true")
                return@next Step.get(StepTag.STEP_6, delay = 2000)
            }else{
                LogWrapper.logAppend("未找到分享按钮，重试")
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_19, delay = 2000)
            }
        }

        //100. 恢复到微信主页面
        collector.next(StepTag.STEP_100) { step ->
            LogWrapper.logAppend("STEP_100: 开始执行 - 恢复到微信主页面")
            
            // 1. 检查是否已经在微信主页面
            val wechatNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.TextView"
                    && it.viewIdResourceName == "android:id/text1"
                    && it.text?.toString() == "微信"
            }
            
            if (wechatNode != null) {
                LogWrapper.logAppend("已在微信主页面，重置状态并返回 STEP_2")
                lastImageBounds = null
                lastTextMsg = null
                isLastMsgText = false
                retryCount = 0
                return@next Step.get(StepTag.STEP_2, delay = 3000)
            }

            // 2. 尝试返回操作
            repeat(10) { attempt ->
                if (AssistsCore.back()) {
                    LogWrapper.logAppend("返回第 ${attempt + 1} 次")
                    delay(1000)
                    
                    // 检查是否已回到微信主页面
                    val checkWechatNode = AssistsCore.getAllNodes().find {
                        it.className == "android.widget.TextView"
                            && it.viewIdResourceName == "android:id/text1"
                            && it.text?.toString() == "微信"
                    }
                    
                    if (checkWechatNode != null) {
                        LogWrapper.logAppend("已回到微信主页面，重置状态并返回 STEP_2")
                        lastImageBounds = null
                        lastTextMsg = null
                        isLastMsgText = false
                        retryCount = 0
                        return@next Step.get(StepTag.STEP_2, delay = 3000)
                    }
                }
            }

            // 3. 如果多次返回后仍未回到主页面，重新启动微信
            LogWrapper.logAppend("多次返回后仍未回到主页面，重新启动微信")
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.instance?.startActivity(this)
            }
            
            // 重置所有状态
            lastImageBounds = null
            lastTextMsg = null
            isLastMsgText = false
            retryCount = 0
            
            return@next Step.get(StepTag.STEP_2, delay = 5000)
        }
    }

    // 处理阿汤哥的文字
    private fun processAtangText(text: String): String {
        // 替换 .cn 为指定字符串
        return text.replace(".cn", "FKD4RaByh_7pz")
    }

    // 处理京粉的文字
    private fun processJingfenText(text: String): String {
        // 只处理包含 jd.com 的消息
        // 如果包含指定字符串，替换回.cn
        var content = text.replace("FKD4RaByh_7pz", ".cn")
        if (!content.contains("jd.com")) {
            return ""
        }
        return content + "\n\n\n防失联，关注服务号：小小阿土哥"
    }
}