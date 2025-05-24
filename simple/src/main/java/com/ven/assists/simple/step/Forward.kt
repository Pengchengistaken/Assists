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
import com.ven.assists.AssistsCore.setNodeText
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.common.LogWrapper
import com.ven.assists.simple.overlays.OverlayLog
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl
import kotlinx.coroutines.delay

/**
 * 转发功能实现
 */
class Forward : StepImpl() {
    // 用于存储最后一张图片的 bounds
    companion object {
        private var lastImageBounds: String? = null
        private var lastTextMsg: String? = null // 记录上一次的文字消息内容
        private var DEBUG: Boolean = false
        private var isLastMsgText: Boolean? = false
        private var lastStep: Int? = 0 // 记录最后执行的步骤
        private var ProcessedMsgText: String? = null // 记录全局内容
        private var lastMessageTime: String? = null // 记录上一条消息的时间
        private var retryCount: Int = 0 // 记录重试次数
        private var currentGroupIndex: Int = 0 // 当前处理的群组索引
        private val targetGroups = mutableSetOf(
            "文件传输助手"
        )

        private fun setLastStep(step: Int) {
            lastStep = step
            LogWrapper.logAppend("当前执行步骤: $step")
        }

        fun toggleDebug() {
            DEBUG = !DEBUG
            LogWrapper.logAppend("Debug模式已${if (DEBUG) "开启" else "关闭"}")
            // 通知按钮颜色变化
            OverlayLog.updateDebugButtonColor(DEBUG)
        }

        private fun resetRetryCount() {
            retryCount = 0
        }

        private fun incrementRetryCount(): Int {
            return ++retryCount
        }

        private fun resetGroupIndex() {
            currentGroupIndex = 0
        }
    }

    /**
     * 查找指定 ID 和文本的 TextView 节点
     */
    private fun findTextViewByIdAndText(viewId: String, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == "android.widget.TextView" &&
            it.viewIdResourceName == viewId &&
            it.text?.toString() == text
        }
    }

    /**
     * 查找指定 ID 的 TextView 节点
     */
    private fun findTextViewById(viewId: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == "android.widget.TextView" &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 查找指定 ID 的 TextView 节点列表
     */
    private fun findAllTextViewById(viewId: String): List<android.view.accessibility.AccessibilityNodeInfo> {
        return AssistsCore.getAllNodes().filter {
            it.className == "android.widget.TextView" &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 查找指定 ID 的 LinearLayout 节点
     */
    private fun findLinearLayoutById(viewId: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == "android.widget.LinearLayout" &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 查找指定 ID 的 ListView 节点
     */
    private fun findListViewById(viewId: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == "android.widget.ListView" &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 检查消息时间戳是否发生变化
     * @param currentTime 当前消息的时间戳
     * @return 如果时间戳发生变化返回true，否则返回false
     */
    private fun checkMessageTime(currentTime: String?): Boolean {
        LogWrapper.logAppend("当前消息时间: $currentTime，历史时间: $lastMessageTime")
        if (currentTime == lastMessageTime) {
            if (currentTime == null) {
                LogWrapper.logAppend("当前消息时间为 null，可能是界面没有时间的节点信息。")
                LogWrapper.logAppend("当做是新消息来处理。")
                return true
            }
            LogWrapper.logAppend("消息时间未变化，无需转发")
            return false
        }
        if (currentTime != null) {
            lastMessageTime = currentTime
        }
        LogWrapper.logAppend("消息时间已变化")
        return true
    }

    /**
     * 处理线报员的文字
     * @param text 原始文字
     * @return 处理后的文字
     */
    private fun processAtangText(text: String): String {
        // 替换 .cn 为指定字符串
        return text.replace(".cn", "FKD4RaByh_7pz")
    }

    /**
     * 处理京粉的文字
     * @param text 原始文字
     * @return 处理后的文字
     */
    private fun processJingfenText(text: String): String {
        // 只处理包含 jd.com 的消息
        // 如果包含指定字符串，替换回.cn
        var content = text.replace("FKD4RaByh_7pz", ".cn")
        if (!content.contains("jd.com")) {
            return ""
        }
        return content + "\n\n\n防失联，关注服务号：小小阿土哥"
    }

    /**
     * 判断当前是否在微信主页面
     * @return 是否在微信主页面
     */
    private fun isWechatMainPage(): Boolean {
        val nodes = AssistsCore.getAllNodes()
        // 检查是否在通讯录页面
        val isInContactPage = findTextViewByIdAndText("com.tencent.mm:id/icon_tv", "通讯录") != null
        
        if (isInContactPage) {
            // 如果在通讯录页面，点击微信切换到主页面
            val wechatTab = findTextViewByIdAndText("com.tencent.mm:id/icon_tv", "微信")
            wechatTab?.findFirstParentClickable()?.click()
        }
        
        // 检查是否在微信主页面
        return findTextViewByIdAndText("android:id/text1", "微信") != null
    }

    /**
     * 检查是否回到微信主页面
     * @param maxAttempts 最大尝试次数
     * @param delayMs 每次尝试之间的延迟时间（毫秒）
     * @return 是否成功回到微信主页面
     */
    private suspend fun checkBackToWechatMain(maxAttempts: Int = 5, delayMs: Long = 2000): Boolean {
        repeat(maxAttempts) { attempt ->
            if (AssistsCore.back()) {
                LogWrapper.logAppend("返回第 ${attempt + 1} 次")
            }
            delay(delayMs)
            if (isWechatMainPage()) {
                LogWrapper.logAppend("到了微信主页面。")
                return true
            }
        }
        LogWrapper.logAppend("未能返回微信主页面，重新启动微信")
        Intent().apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            AssistsService.instance?.startActivity(this)
        }
        delay(3000) // 等待微信启动
        return isWechatMainPage()
    }

    override fun onImpl(collector: StepCollector) {
        //1. 打开微信
        collector.next(StepTag.STEP_1, isRunCoroutineIO = true) {
            setLastStep(StepTag.STEP_1)
            LogWrapper.logAppend("STEP_1: 开始执行 - 启动微信")
            LogWrapper.logAppend("启动微信")
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.instance?.startActivity(this)
            }
            return@next Step.get(StepTag.STEP_1001, delay = 2000)
        }

        //1001. 获取联系人列表
        collector.next(StepTag.STEP_1001) { step ->
            setLastStep(StepTag.STEP_1001)
            LogWrapper.logAppend("STEP_1001: 开始执行 - 获取联系人列表")
            
            // 1. 点击通讯录
            val contactTab = findTextViewByIdAndText("com.tencent.mm:id/icon_tv", "通讯录")
            
            if (contactTab != null) {
                LogWrapper.logAppend("已找到通讯录按钮，2秒后点击")
                delay(2000)
                contactTab.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1002, delay = 2000)
            }
            
            LogWrapper.logAppend("未找到通讯录按钮，重试")
            return@next Step.get(StepTag.STEP_1001, delay = 2000)
        }

        //1002. 点击群聊
        collector.next(StepTag.STEP_1002) { step ->
            setLastStep(StepTag.STEP_1002)
            LogWrapper.logAppend("STEP_1002: 开始执行 - 点击群聊")
            
            val groupChat = findTextViewByIdAndText("com.tencent.mm:id/n9", "群聊")
            
            if (groupChat != null) {
                LogWrapper.logAppend("已找到群聊按钮，2秒后点击")
                delay(2000)
                groupChat.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1003, delay = 2000)
            }
            
            LogWrapper.logAppend("未找到群聊按钮，重试")
            return@next Step.get(StepTag.STEP_1002, delay = 2000)
        }

        //1003. 遍历群聊列表
        collector.next(StepTag.STEP_1003) { step ->
            setLastStep(StepTag.STEP_1003)
            LogWrapper.logAppend("STEP_1003: 开始执行 - 遍历群聊列表")
            
            val groupNames = mutableSetOf<String>()
            val groupNodes = findAllTextViewById("com.tencent.mm:id/cg1")
            
            groupNodes.forEach { node ->
                node.text?.toString()?.let { name ->
                    if (name.isNotEmpty()) {
                        groupNames.add(name)
                        LogWrapper.logAppend("找到群聊: $name")
                    }
                }
            }
            
            // 更新 targetGroups
            if (groupNames.isNotEmpty()) {
                targetGroups.clear()
                targetGroups.addAll(groupNames)
                LogWrapper.logAppend("已更新目标群组列表")
            }
            
            // 返回上一页
            AssistsCore.back()
            return@next Step.get(StepTag.STEP_1004, delay = 2000)
        }

        //1004. 点击标签
        collector.next(StepTag.STEP_1004) { step ->
            setLastStep(StepTag.STEP_1004)
            LogWrapper.logAppend("STEP_1004: 开始执行 - 点击标签")
            
            val tagButton = findTextViewByIdAndText("com.tencent.mm:id/n9", "标签")
            
            if (tagButton != null) {
                LogWrapper.logAppend("已找到标签按钮，2秒后点击")
                delay(2000)
                tagButton.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1005, delay = 2000)
            }
            
            LogWrapper.logAppend("未找到标签按钮，重试")
            return@next Step.get(StepTag.STEP_1004, delay = 2000)
        }

        //1005. 点击转发
        collector.next(StepTag.STEP_1005) { step ->
            setLastStep(StepTag.STEP_1005)
            LogWrapper.logAppend("STEP_1005: 开始执行 - 点击转发")
            
            val forwardButton = findTextViewByIdAndText("com.tencent.mm:id/hs8", "转发")
            
            if (forwardButton != null) {
                LogWrapper.logAppend("已找到转发按钮，2秒后点击")
                delay(2000)
                forwardButton.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1006, delay = 2000)
            }
            
            LogWrapper.logAppend("未找到转发按钮，重试")
            return@next Step.get(StepTag.STEP_1005, delay = 2000)
        }

        //1006. 获取转发页面的联系人
        collector.next(StepTag.STEP_1006) { step ->
            setLastStep(StepTag.STEP_1006)
            LogWrapper.logAppend("STEP_1006: 开始执行 - 获取转发页面的联系人")
            
            val contactNodes = findAllTextViewById("com.tencent.mm:id/kbq")
            
            contactNodes.forEach { node ->
                node.text?.toString()?.let { name ->
                    if (name.isNotEmpty()) {
                        targetGroups.add(name)
                        LogWrapper.logAppend("添加联系人: $name")
                    }
                }
            }
            
            // 返回主页面
            if (checkBackToWechatMain()) {
                LogWrapper.logAppend("已返回微信主页面")
                return@next Step.get(StepTag.STEP_2, delay = 2000)
            }
            
            LogWrapper.logAppend("未能返回微信主页面，重试")
            return@next Step.get(StepTag.STEP_1006, delay = 2000)
        }

        //2. 点击聊天列表中的京东线报交流群
        collector.next(StepTag.STEP_2) { step ->
            setLastStep(StepTag.STEP_2)
            LogWrapper.logAppend("STEP_2: 开始执行 - 查找并点击京东线报交流群。")

            // 先判断是否在微信主页面
            if (!isWechatMainPage()) {
                LogWrapper.logAppend("当前不在微信主页面，尝试返回主页面。")
                if (checkBackToWechatMain()) {
                    LogWrapper.logAppend("已在微信主页面。")
                } else {
                    LogWrapper.logAppend("未能返回微信主页面，重新启动微信。")
                    return@next Step.get(StepTag.STEP_1, delay = 2000)
                }
            }

            // 双击底部Tab"微信"
            val tabNodes = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.TextView"
                        && it.viewIdResourceName == "com.tencent.mm:id/icon_tv"
                        && it.text?.toString() == "微信"
            }
            
            if (tabNodes.isNotEmpty()) {
                val wechatTab = tabNodes.first()
                wechatTab.findFirstParentClickable()?.let { parent ->
                    parent.click()
                    Thread.sleep(100)
                    parent.click()
                    LogWrapper.logAppend("已双击底部Tab微信")
                }
            }
            // 查找所有聊天行（每一行的 LinearLayout，id=cj0）
            val allRows = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.LinearLayout" && it.viewIdResourceName == "com.tencent.mm:id/cj0"
            }

            // 遍历每一行，递归查找 a_h（小红点） 和 kbq（群名）
            for (row in allRows) {
                val allDescendants = row.getNodes() // 递归获取所有后代节点
                val hasAh = allDescendants.any { it.viewIdResourceName == "com.tencent.mm:id/a_h" } // 小红点
                val kbqNode = allDescendants.find {
                    it.viewIdResourceName == "com.tencent.mm:id/kbq" && (it.text?.contains(ContactList.sourceGroupName) == true) // 群名
                }
                val ht5Node = allDescendants.find {
                    it.viewIdResourceName == "com.tencent.mm:id/ht5" && (it.text?.contains("关注的人") == true) // 关注的人
                }
                if (DEBUG && kbqNode != null) { //调试：不需要小红点
                    LogWrapper.logAppend("DEBUG 模式，跳过小红点")
                    kbqNode.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已找到并点击京东线报交流群")
                    return@next Step.get(StepTag.STEP_3, delay = 2000)
                } else if (hasAh && kbqNode != null && ht5Node != null) {
                    LogWrapper.logAppend("京东线报交流群有新消息且包含关注的人，2分钟后点击并进入执行。")
                    delay(120_000)
                    kbqNode.findFirstParentClickable()?.click()
                    return@next Step.get(StepTag.STEP_3)
                }
            }
            if (DEBUG) {
                LogWrapper.logAppend("DEBUG 模式，5秒钟后再检查。")
                return@next Step.get(StepTag.STEP_2, delay = 5000)
            }
            LogWrapper.logAppend("群里没有新消息, 一分钟后再检查。")
            return@next Step.get(StepTag.STEP_2, delay = 60_000)
        }

        //3. 获取最后一张图片
        collector.next(StepTag.STEP_3) { step ->
            isLastMsgText = false
            LogWrapper.logAppend("设置 isLastMsgText 为 false")
            setLastStep(StepTag.STEP_3)
            LogWrapper.logAppend("STEP_3: 开始执行 - 获取最后一张图片")

            // 1. 获取所有消息块
            val allMsgBlocks = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.RelativeLayout" && it.viewIdResourceName == "com.tencent.mm:id/bn1"
            }

            // 2. 查找线报员发送的最新图片消息
            var targetImageNode: android.view.accessibility.AccessibilityNodeInfo? = null
            var currentImageBounds: String? = null
            var currentMessageTime: String? = null

            // 倒序遍历，优先取最新
            for (msgBlock in allMsgBlocks.reversed()) {
                // 查找发送者节点
                LogWrapper.logAppend("查找线报员的图片消息")
                val senderNode = msgBlock.getNodes().find {
                    it.className == "android.widget.TextView"
                            && it.viewIdResourceName == "com.tencent.mm:id/brc"
                            && it.text?.toString()
                        ?.contains(ContactList.sourceRobotName) == true
                }

                // 如果找到线报员的消息，再查找图片节点和时间节点
                if (senderNode != null) {
                    // 查找时间节点
                    val timeNode = msgBlock.getNodes().find {
                        it.className == "android.widget.TextView"
                                && it.viewIdResourceName == "com.tencent.mm:id/br1"
                    }
                    currentMessageTime = timeNode?.text?.toString()

                    val imageNode = msgBlock.getNodes().find {
                        it.viewIdResourceName == "com.tencent.mm:id/bkg" && it.isClickable && it.isLongClickable
                    }
                    if (imageNode != null) {
                        targetImageNode = imageNode
                        currentImageBounds = imageNode.getBoundsInScreen().toShortString()
                        break
                    }
                }
            }

            if (targetImageNode == null) {
                LogWrapper.logAppend("未找到线报员的图片消息，返回。")
                if (checkBackToWechatMain()) {
                    LogWrapper.logAppend("返回微信主页面，30秒后重试。")
                    return@next Step.get(StepTag.STEP_2, delay = 30000)
                }
            }

            // 3. 检查时间戳是否发生变化
            if (!DEBUG && !checkMessageTime(currentMessageTime)) {
                LogWrapper.logAppend("消息时间未变化，无需转发。")
                if (checkBackToWechatMain()) {
                    LogWrapper.logAppend("返回微信主页面，30秒后重试。")
                    return@next Step.get(StepTag.STEP_2, delay = 30000)
                }
            }

            // 4. 点击图片
            if (targetImageNode?.isVisibleToUser!! && targetImageNode.isLongClickable && targetImageNode.isEnabled) {
                LogWrapper.logAppend("节点可交互。")
                if (targetImageNode.click()) {
                    LogWrapper.logAppend("点击一下，打开图片。")
                    LogWrapper.logAppend("延迟 3 秒，充分等待")
                    delay(3000)
                    LogWrapper.logAppend("点击底部的转发按钮。")
                    if (AssistsCore.gestureClick(646f, 2261f)) {
                        return@next Step.get(StepTag.STEP_6, delay = 3000)
                    }
                }
                LogWrapper.logAppend("延迟 2 秒")
                delay(2000)
                if (targetImageNode.longClick()) {
                    return@next Step.get(StepTag.STEP_4, delay = 3000)
                }
                return@next Step.get(StepTag.STEP_3, delay = 3000)
            } else {
                LogWrapper.logAppend("节点不可交互，isVisibleToUser=${targetImageNode.isVisibleToUser}, isLongClickable=${targetImageNode.isLongClickable}, isEnabled=${targetImageNode.isEnabled}")
                return@next Step.get(StepTag.STEP_3, delay = 2000)
            }
        }

        //4. 查找并点击"转发"按钮
        collector.next(StepTag.STEP_4) { step ->
            setLastStep(StepTag.STEP_4)
            LogWrapper.logAppend("STEP_4: 开始执行 - 查找并点击转发按钮")
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
                    LogWrapper.logAppend("已定位到转发按钮，2秒后点击。")
                    return@next Step.get(StepTag.STEP_5, delay = 2000)
                }
            }
            LogWrapper.logAppend("未找到转发按钮，重试")
            Thread.sleep(1500)
            lastImageBounds = null
            AssistsCore.back()
            return@next Step.get(StepTag.STEP_100, delay = 2000)
        }

        // STEP_5，真正执行点击
        collector.next(StepTag.STEP_5) { step ->
            setLastStep(StepTag.STEP_5)
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
                    LogWrapper.logAppend("延迟 1 秒。")
                    delay(1000)
                    clickableParent.click()
                    LogWrapper.logAppend("已点击转发按钮")
                    return@next Step.get(StepTag.STEP_6)
                }
            } else {
                LogWrapper.logAppend("未找到转发按钮，重试")
                Thread.sleep(1500)
                lastImageBounds = null
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_100, delay = 2000)
            }
            LogWrapper.logAppend("已点击转发按钮，下一步")
            return@next Step.get(StepTag.STEP_6, delay = 3000)
        }

        //6. 选择转发对象
        collector.next(StepTag.STEP_6) { step ->
            setLastStep(StepTag.STEP_6)
            LogWrapper.logAppend("STEP_6: 开始执行 - 选择转发对象")
            LogWrapper.logAppend("选择转发对象")
            // 1. 查找并点击"多选"按钮
            val multiSelectNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.TextView"
                        && it.text?.toString()?.contains("多选") == true
                        && it.isClickable
            }
            if (multiSelectNode != null) {
                LogWrapper.logAppend("已定位到多选按钮，2秒后点击。")
                delay(2000)
                multiSelectNode.click()
                LogWrapper.logAppend("已点击多选按钮")
                resetGroupIndex() // 重置群组索引
                return@next Step.get(StepTag.STEP_7, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到多选按钮，重试")
                AssistsCore.back() //返回到聊天窗口
                return@next Step.get(StepTag.STEP_3, delay = 2000)
            }
        }

        //7. 选择目标群组
        collector.next(StepTag.STEP_7) { step ->
            setLastStep(StepTag.STEP_7)
            LogWrapper.logAppend("STEP_7: 开始执行 - 选择目标群组")
            
            // 如果是 DEBUG 模式，只选择文件传输助手
            if (DEBUG) {
                LogWrapper.logAppend("DEBUG 模式，只选择文件传输助手")
                val fileTransferNode = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.TextView"
                            && it.text?.toString() == "文件传输助手"
                }
                
                if (fileTransferNode != null) {
                    LogWrapper.logAppend("已找到文件传输助手，2秒后点击")
                    delay(2000)
                    fileTransferNode.findFirstParentClickable()?.click()
                    LogWrapper.logAppend("已点击文件传输助手")
                    return@next Step.get(StepTag.STEP_9, delay = 2000)
                } else {
                    LogWrapper.logAppend("未找到文件传输助手，尝试滚动列表")
                    val listContainer = AssistsCore.getAllNodes().find {
                        it.className == "android.widget.ListView" &&
                                it.viewIdResourceName == "com.tencent.mm:id/i3y"
                    }

                    if (listContainer != null && listContainer.scrollForward()) {
                        LogWrapper.logAppend("已滚动列表，重试选择文件传输助手")
                        return@next Step.get(StepTag.STEP_7, delay = 2000)
                    }
                    
                    LogWrapper.logAppend("无法找到文件传输助手，重试")
                    return@next Step.get(StepTag.STEP_7, delay = 2000)
                }
            }

            // 如果已经处理完所有群组，进入下一步
            if (currentGroupIndex >= targetGroups.size) {
                LogWrapper.logAppend("所有群组已选择完成")
                return@next Step.get(StepTag.STEP_9, delay = 2000)
            }

            // 获取当前要选择的群组名称
            val currentGroup = targetGroups.elementAt(currentGroupIndex)
            LogWrapper.logAppend("正在选择群组: $currentGroup")

            // 查找目标群组节点
            val groupNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.TextView"
                        && it.text?.toString()?.contains(currentGroup) == true
            }

            if (groupNode != null) {
                LogWrapper.logAppend("已定位到群组 $currentGroup，2秒后点击。")
                delay(2000)
                groupNode.findFirstParentClickable()?.click()
                LogWrapper.logAppend("已点击群组 $currentGroup")
                currentGroupIndex++ // 移动到下一个群组
                return@next Step.get(StepTag.STEP_7, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到群组 $currentGroup，尝试滚动列表")
                val listContainer = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.ListView" &&
                            it.viewIdResourceName == "com.tencent.mm:id/i3y"
                }

                if (listContainer != null && listContainer.scrollForward()) {
                    LogWrapper.logAppend("已滚动列表，重试选择群组")
                    return@next Step.get(StepTag.STEP_7, delay = 2000)
                }
                
                LogWrapper.logAppend("无法找到群组 $currentGroup，跳过")
                currentGroupIndex++ // 移动到下一个群组
                return@next Step.get(StepTag.STEP_7, delay = 2000)
            }
        }

        //9. 点击"完成"按钮
        collector.next(StepTag.STEP_9) { step ->
            setLastStep(StepTag.STEP_9)
            LogWrapper.logAppend("STEP_9: 开始执行 - 查找并点击完成按钮")
            val finishBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                        && it.text?.toString()?.contains("完成") == true
                        && it.isClickable
            }
            if (finishBtn != null) {
                LogWrapper.logAppend("已定位到完成按钮，2秒后点击。")
                resetRetryCount()
                delay(2000)
                finishBtn.click()
                LogWrapper.logAppend("已点击完成按钮")
                return@next Step.get(StepTag.STEP_10, delay = 2000)
            } else {
                val currentRetry = incrementRetryCount()
                LogWrapper.logAppend("未找到完成按钮，第 $currentRetry 次重试")
                if (currentRetry >= 10) {
                    LogWrapper.logAppend("重试次数超过10次，尝试返回微信主页面")
                    resetRetryCount()
                    if (checkBackToWechatMain()) {
                        return@next Step.get(StepTag.STEP_2, delay = 2000)
                    }
                }
                return@next Step.get(StepTag.STEP_9, delay = 2000)
            }
        }

        //10. 点击"发送"按钮
        collector.next(StepTag.STEP_10) { step ->
            setLastStep(StepTag.STEP_10)
            LogWrapper.logAppend("STEP_10: 开始执行 - 查找并点击发送按钮")
            val sendBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                        && it.text?.toString()?.contains("发送") == true
                        && it.isClickable
            }
            if (sendBtn != null) {
                LogWrapper.logAppend("已定位到发送按钮，2秒后点击。")
                delay(2000)
                sendBtn.click()
                if (isLastMsgText == true) {
                    LogWrapper.logAppend("isLastMsgText 为 true。")
                    LogWrapper.logAppend("已点击发送按钮，准备查找最新图片消息")
                    if (checkBackToWechatMain()) {
                        return@next Step.get(StepTag.STEP_2, delay = 3000)
                    }
                    LogWrapper.logAppend("没有到微信主页面。")
                    return@next Step.get(StepTag.STEP_100, delay = 3000)
                } else {
                    LogWrapper.logAppend("已点击发送按钮，准备查找最新文字消息")
                    delay(5000)
                    AssistsCore.back()
                    return@next Step.get(StepTag.STEP_11, delay = 2000)
                }
            } else {
                LogWrapper.logAppend("未找到发送按钮，重试")
                return@next Step.get(StepTag.STEP_10, delay = 2000)
            }
        }

        // 11. 查找线报员发送的最新一条文字消息，并log输出
        collector.next(StepTag.STEP_11) { step ->
            setLastStep(StepTag.STEP_11)
            LogWrapper.logAppend("STEP_11: 开始执行 - 查找线报员最新文字消息")
            val allMsgBlocks = AssistsCore.getAllNodes().filter {
                it.className == "android.widget.RelativeLayout" && it.viewIdResourceName == "com.tencent.mm:id/bn1"
            }

            var latestMsg: String? = null
            var latestMsgNode: android.view.accessibility.AccessibilityNodeInfo? = null
            var latestMsgIndex = -1
            var latestImageIndex = -1

            // 倒序遍历，优先取最新
            for ((i, msgBlock) in allMsgBlocks.withIndex().reversed()) {
                // 1. 查找发送者节点
                val senderNode = msgBlock.getNodes().find {
                    it.className == "android.widget.TextView"
                            && it.viewIdResourceName == "com.tencent.mm:id/brc"
                            && it.text?.toString()
                        ?.contains(ContactList.sourceRobotName) == true
                }

                // 2. 查找图片节点
                val imageNode = msgBlock.getNodes().find {
                    it.viewIdResourceName == "com.tencent.mm:id/bkg"
                }
                if (latestImageIndex == -1 && imageNode != null) {
                    latestImageIndex = i
                }

                // 3. 如果找到线报员的消息，再查找文字内容节点
                if (senderNode != null) {
                    val contentNode = msgBlock.getNodes().find {
                        it.className == "android.widget.TextView"
                                && it.viewIdResourceName == "com.tencent.mm:id/bkl"
                                && !it.text.isNullOrBlank()
                    }
                    if (latestMsgIndex == -1 && contentNode != null) {
                        latestMsg = contentNode.text?.toString()
                        latestMsgNode = contentNode
                        latestMsgIndex = i
                    }
                }
            }

            // debug
            if (DEBUG) {
                LogWrapper.logAppend("DEBUG模式，设置 lastTextMsg 为 null")
                lastTextMsg = null
            }

            // 判断是否需要back
            if (latestMsg == null || latestMsg == lastTextMsg || (latestMsgIndex < latestImageIndex && latestImageIndex != -1)) {
                LogWrapper.logAppend("无有效文字消息，返回。")
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 2000)
            }

            // 内容有变化，复制内容并处理
            lastTextMsg = latestMsg
            ProcessedMsgText = processAtangText(latestMsg)

            if (checkBackToWechatMain()) {
                return@next Step.get(StepTag.STEP_12, delay = 3000)
            }
            return@next Step.get(StepTag.STEP_12, delay = 2000)
        }

        //12. 进入京粉并自动发消息
        collector.next(StepTag.STEP_12) { step ->
            setLastStep(StepTag.STEP_12)
            LogWrapper.logAppend("STEP_12: 开始执行 - 查找并进入京粉")
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
                    LogWrapper.logAppend("已找到并定位到京粉，2秒后点击。")
                    delay(2000)
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
            setLastStep(StepTag.STEP_13)
            LogWrapper.logAppend("STEP_13: 开始执行 - 切换到发消息")
            val switchMsgNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.ImageView"
                        && it.viewIdResourceName == "com.tencent.mm:id/blp"
                        && it.isClickable
                        && it.contentDescription?.contains("切换到发消息") == true
            }
            if (switchMsgNode != null) {
                LogWrapper.logAppend("已定位到切换到发消息按钮，2秒后点击。")
                delay(2000)
                switchMsgNode.click()
                LogWrapper.logAppend("已点击切换到发消息")
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到切换到发消息按钮，重试")
                return@next Step.get(StepTag.STEP_13, delay = 2000)
            }
        }

        //14. 点击输入框并设置文本内容
        collector.next(StepTag.STEP_14) { step ->
            setLastStep(StepTag.STEP_14)
            LogWrapper.logAppend("STEP_14: 开始执行 - 点击输入框并设置文本内容")
            if (lastStep == StepTag.STEP_15) {
                val editTextNode = AssistsCore.getAllNodes().find {
                    it.className == "android.widget.EditText"
                            && it.viewIdResourceName == "com.tencent.mm:id/bkk"
                            && it.isClickable && it.isEnabled && it.isFocusable
                }

                if (editTextNode != null) {
                    if (!ProcessedMsgText.isNullOrBlank()) {
                        editTextNode.setNodeText(ProcessedMsgText)
                        LogWrapper.logAppend("已设置文本内容")
                        return@next Step.get(StepTag.STEP_15, delay = 2000)
                    }
                    LogWrapper.logAppend("文本内容为空，重试")
                    return@next Step.get(StepTag.STEP_14, delay = 2000)
                }
                LogWrapper.logAppend("未找到输入框，重试")
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            }

            // 查找输入框
            val editTextNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.EditText"
                        && it.viewIdResourceName == "com.tencent.mm:id/bkk"
                        && it.isClickable && it.isEnabled && it.isFocusable
            }

            if (editTextNode != null) {
                LogWrapper.logAppend("已定位到输入框，2秒后点击。")
                delay(2000)
                editTextNode.click()
                delay(1000)

                if (!ProcessedMsgText.isNullOrBlank()) {
                    editTextNode.setNodeText(ProcessedMsgText)
                    LogWrapper.logAppend("已设置文本内容")
                    return@next Step.get(StepTag.STEP_15, delay = 2000)
                }
                LogWrapper.logAppend("文本内容为空，重试")
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            }

            LogWrapper.logAppend("未找到输入框，重试")
            return@next Step.get(StepTag.STEP_14, delay = 2000)
        }

        //15. 点击发送按钮
        collector.next(StepTag.STEP_15) { step ->
            setLastStep(StepTag.STEP_15)
            LogWrapper.logAppend("STEP_15: 开始执行 - 点击发送按钮")
            LogWrapper.logAppend("延迟 2 秒让节点加载")
            delay(2000) //延迟 2 秒让节点加载
            val sendBtn = AssistsCore.getAllNodes().find {
                (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                        && it.text?.contains("发送") == true && it.isClickable
            }
            if (sendBtn != null) {
                LogWrapper.logAppend("已定位到发送按钮，2秒后点击。")
                delay(2000)
                sendBtn.click()
                LogWrapper.logAppend("已点击发送按钮，进入下一步")
                return@next Step.get(StepTag.STEP_16, delay = 5000)
            } else {
                LogWrapper.logAppend("未找到发送按钮，重试")
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            }
        }

        collector.next(StepTag.STEP_16) { step ->
            setLastStep(StepTag.STEP_16)
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
                // 3. 处理消息内容,复制到全局变量
                val processedMsg = processJingfenText(finalLatestMsg)
                if (processedMsg.isNotEmpty()) {
                    ProcessedMsgText = processedMsg
                    LogWrapper.logAppend("复制到全局变量ProcessedMsgText。")
                } else {
                    LogWrapper.logAppend("消息内容不包含jd.com链接，跳过处理")
                }
                if (checkBackToWechatMain()) {
                    return@next Step.get(StepTag.STEP_17, delay = 3000)
                }
                return@next Step.get(StepTag.STEP_17, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到京粉的文字消息，重试")
                return@next Step.get(StepTag.STEP_16, delay = 2000)
            }
        }

        //17. 双击顶部微信并进入文件传输助手
        collector.next(StepTag.STEP_17) { step ->
            setLastStep(StepTag.STEP_17)
            LogWrapper.logAppend("STEP_17: 开始执行 - 双击顶部微信并进入文件传输助手")
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
                LogWrapper.logAppend("已定位到文件传输助手，2秒后点击。")
                delay(2000)
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
            setLastStep(StepTag.STEP_18)
            LogWrapper.logAppend("STEP_18: 开始执行 - 在文件传输助手中粘贴内容并发送")
            // 1. 查找输入框
            val editTextNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.EditText"
                        && it.viewIdResourceName == "com.tencent.mm:id/bkk"
                        && it.isClickable && it.isEnabled && it.isFocusable
            }

            if (editTextNode != null) {
                if (!ProcessedMsgText.isNullOrBlank()) {
                    editTextNode.setNodeText(ProcessedMsgText)
                    LogWrapper.logAppend("已设置文本内容。")
                    delay(2000)
                }
                    // 4. 查找并点击发送按钮
                    val sendBtn = AssistsCore.getAllNodes().find {
                        (it.className == "android.widget.Button" || it.className == "android.widget.TextView")
                                && it.text?.contains("发送") == true && it.isClickable
                    }
                    if (sendBtn != null) {
                        LogWrapper.logAppend("已定位到发送按钮，2秒后点击。")
                        delay(2000)
                        sendBtn.click()
                        LogWrapper.logAppend("已点击发送按钮，完成所有步骤")
                        return@next Step.get(StepTag.STEP_19, delay = 2000)
                    } else {
                        LogWrapper.logAppend("未找到发送按钮，重试")
                        return@next Step.get(StepTag.STEP_18, delay = 2000)
                    }
            } else {
                LogWrapper.logAppend("未找到输入框，重试")
                return@next Step.get(StepTag.STEP_18, delay = 2000)
            }
        }

        //19. 查找最新消息并转发
        collector.next(StepTag.STEP_19) { step ->
            setLastStep(StepTag.STEP_19)
            LogWrapper.logAppend("STEP_19: 开始执行 - 展开消息全屏并转发")
            // 3. 双击消息
            LogWrapper.logAppend("延迟 3 秒")
            delay(3000)
            LogWrapper.logAppend("双击右下角，展开消息全屏，开始分享")
            AssistsCore.gestureClick(800f, 2040f)
            delay(40)
            AssistsCore.gestureClick(800f, 2040f)
            delay(5000)

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
                ProcessedMsgText = null
                LogWrapper.logAppend("设置 ProcessedMsgText 为 null")
                return@next Step.get(StepTag.STEP_6, delay = 2000)
            } else {
                LogWrapper.logAppend("未找到分享按钮，尝试返回重试。")
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_19, delay = 1000)
            }
        }

        //100. 恢复到微信主页面
        collector.next(StepTag.STEP_100) { step ->
            setLastStep(StepTag.STEP_100)
            LogWrapper.logAppend("STEP_100: 开始执行 - 恢复到微信主页面")

            // 1. 查找顶部的"微信"文本
            val wechatNode = AssistsCore.getAllNodes().find {
                it.className == "android.widget.TextView"
                        && it.viewIdResourceName == "android:id/text1"
                        && it.text?.toString() == "微信"
            }

            wechatNode?.findFirstParentClickable()?.let { parent ->
                parent.click()
                delay(100)
                parent.click()
                LogWrapper.logAppend("已双击顶部微信，成功返回主页面")
                return@next Step.get(StepTag.STEP_2, delay = 2000)
            }

            // 2. 如果没找到顶部微信，尝试多次返回
            if (checkBackToWechatMain(10)) {
                return@next Step.get(StepTag.STEP_2, delay = 2000)
            }

            LogWrapper.logAppend("未能返回微信主页面，重新启动微信")
            return@next Step.get(StepTag.STEP_1, delay = 2000)
        }
    }
}