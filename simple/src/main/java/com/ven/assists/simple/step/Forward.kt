package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import android.util.Log
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
import com.ven.assists.log.logAppend
import com.ven.assists.simple.constants.WechatResourceIds
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
        private var pendingMessageTime: String? = null // 待确认的消息时间，转发成功后再写入 lastMessageTime
        private var retryCount: Int = 0 // 记录重试次数
        private var step11EmptyBlockRetry: Int = 0 // STEP_11 消息块为空时的重试次数
        private const val MAX_RETRY_FIND_INPUT = 20 // 未找到输入框/文本为空时的最大重试次数
        private var currentGroupIndex: Int = 0 // 当前处理的群组索引
        private val targetGroups = mutableSetOf(
            "文件传输助手"
        )

        private fun setLastStep(step: Int) {
            lastStep = step
            ("当前执行步骤: $step").logAppend()
        }

        fun toggleDebug() {
            DEBUG = !DEBUG
            ("Debug模式已${if (DEBUG) "开启" else "关闭"}").logAppend()
            // 通知按钮颜色变化
            OverlayLog.updateDebugButtonColor(DEBUG)
        }

        private fun resetRetryCount() {
            retryCount = 0
        }

        private fun resetStep11EmptyRetry() {
            step11EmptyBlockRetry = 0
        }

        private fun incrementRetryCount(): Int {
            return ++retryCount
        }

        private fun resetGroupIndex() {
            currentGroupIndex = 0
        }

        private fun confirmMessageTime() {
            pendingMessageTime?.let { lastMessageTime = it }
            pendingMessageTime = null
        }
    }

    /**
     * 查找指定 ID 和文本的 TextView 节点
     */
    private fun findTextViewByIdAndText(viewId: String, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == WechatResourceIds.NodeClasses.TEXT_VIEW &&
            it.viewIdResourceName == viewId &&
            it.text?.toString() == text
        }
    }

    /**
     * 查找指定 ID 的 TextView 节点
     */
    private fun findTextViewById(viewId: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == WechatResourceIds.NodeClasses.TEXT_VIEW &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 查找指定 ID 的 TextView 节点列表
     */
    private fun findAllTextViewById(viewId: String): List<android.view.accessibility.AccessibilityNodeInfo> {
        return AssistsCore.getAllNodes().filter {
            it.className == WechatResourceIds.NodeClasses.TEXT_VIEW &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 查找指定 ID 的 LinearLayout 节点
     */
    private fun findLinearLayoutById(viewId: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == WechatResourceIds.NodeClasses.LINEAR_LAYOUT &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 查找指定 ID 的 ListView 节点
     */
    private fun findListViewById(viewId: String): android.view.accessibility.AccessibilityNodeInfo? {
        return AssistsCore.getAllNodes().find {
            it.className == WechatResourceIds.NodeClasses.LIST_VIEW &&
            it.viewIdResourceName == viewId
        }
    }

    /**
     * 带延迟重试的输入框查找，应对界面未加载完或节点树快照未刷新的情况。
     * @param maxAttempts 最多尝试次数（含首次）
     * @param delayMs 每次尝试间隔毫秒数
     */
    private suspend fun findInputBoxWithRetry(
        maxAttempts: Int = 3,
        delayMs: Long = 500
    ): android.view.accessibility.AccessibilityNodeInfo? {
        repeat(maxAttempts) { attempt ->
            val node = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.EDIT_TEXT
                    && it.viewIdResourceName == WechatResourceIds.BKK
                    && it.isClickable && it.isEnabled && it.isFocusable
            }
            if (node != null) return node
            if (attempt < maxAttempts - 1) delay(delayMs)
        }
        return null
    }

    /**
     * 检查消息时间戳是否发生变化
     * @param currentTime 当前消息的时间戳
     * @return 如果时间戳发生变化返回true，否则返回false
     */
    private fun checkMessageTime(currentTime: String?): Boolean {
        ("当前消息时间: $currentTime，历史时间: $lastMessageTime").logAppend()
        
        // 如果当前时间为null，当做是新消息来处理
        if (currentTime == null) {
            ("当前消息时间为 null，可能是界面没有时间的节点信息。").logAppend()
            ("当做是新消息来处理。").logAppend()
            return true
        }
        
        // 如果时间戳相同，表示没有新消息
        if (currentTime == lastMessageTime) {
            ("消息时间未变化，无需转发").logAppend()
            return false
        }
        
        // 时间戳不同，表示有新消息，返回true（不在此处更新 lastMessageTime，由 confirmMessageTime 在发送成功后更新）
        ("消息时间已变化").logAppend()
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
     * 标准化文本，将全角字符转换为半角字符
     * @param text 原始文本
     * @return 标准化后的文本
     */
    private fun normalizeText(text: String): String {
        // 将全角 @ (＠) 转换为半角 @
        return text.replace("＠", "@")
    }

    /** 排除气泡内时间标签等短串，避免被当成昵称/正文 */
    private fun isLikelyChatTimeLabel(text: String): Boolean {
        val t = text.trim()
        if (t.length > 12) return false
        if (t.matches(Regex("^(上午|下午|昨天|星期|周)[^\\n]{0,8}$"))) return true
        return t.matches(Regex("^[\\d:：\\s上午下午晚早中-]+$"))
    }

    /**
     * 在单条消息块内查找线报员发送者节点。优先 `brc`；部分微信版本昵称行资源 id 会变化，则回退为「含昵称且较短」的 TextView。
     */
    private fun findLineReporterSenderInBlock(
        msgBlock: android.view.accessibility.AccessibilityNodeInfo
    ): android.view.accessibility.AccessibilityNodeInfo? {
        val nodes = msgBlock.getNodes()
        val strict = nodes.find { node ->
            node.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                && node.viewIdResourceName == WechatResourceIds.BRC
                && ContactList.sourceRobotNames.any { robotName ->
                    val nt = node.text?.toString()?.let { normalizeText(it) } ?: ""
                    nt.contains(normalizeText(robotName))
                }
        }
        if (strict != null) return strict

        val loose = nodes
            .filter {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                    && !it.text.isNullOrBlank()
                    && it.viewIdResourceName != WechatResourceIds.BR1
            }
            .mapNotNull { node ->
                val raw = node.text?.toString()?.trim().orEmpty()
                if (isLikelyChatTimeLabel(raw)) return@mapNotNull null
                val nt = normalizeText(raw)
                if (ContactList.sourceRobotNames.none { rn -> nt.contains(normalizeText(rn)) }) return@mapNotNull null
                node to raw.length
            }
        // 昵称行通常比正文短；取最短匹配减少误把正文当头衔
        return loose.minByOrNull { it.second }?.first
    }

    /**
     * 在线报员消息块中解析正文 TextView。优先 [WechatResourceIds.BKL]；其次可长按正文（与 STEP_19 一致）；再回退为最长正文候选。
     */
    private fun findRobotMessageBodyText(
        msgBlock: android.view.accessibility.AccessibilityNodeInfo,
        senderNode: android.view.accessibility.AccessibilityNodeInfo?
    ): android.view.accessibility.AccessibilityNodeInfo? {
        val nodes = msgBlock.getNodes()
        nodes.find {
            it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                && it.viewIdResourceName == WechatResourceIds.BKL
                && !it.text.isNullOrBlank()
        }?.let { return it }

        val senderNorm = senderNode?.text?.toString()?.let { normalizeText(it.trim()) }.orEmpty()
        val excludedIds = setOf(
            WechatResourceIds.BRC,
            WechatResourceIds.BR1,
            WechatResourceIds.HT5,
            WechatResourceIds.A_H,
        )

        fun isBodyCandidate(cand: android.view.accessibility.AccessibilityNodeInfo): Boolean {
            if (cand.className != WechatResourceIds.NodeClasses.TEXT_VIEW) return false
            if (cand.viewIdResourceName in excludedIds) return false
            val raw = cand.text?.toString()?.trim().orEmpty()
            if (raw.length < 2) return false
            if (isLikelyChatTimeLabel(raw)) return false
            val norm = normalizeText(raw)
            if (senderNorm.isNotEmpty() && norm == senderNorm) return false
            return true
        }

        // 微信里气泡正文常为可长按 TextView（与 STEP_19 长按转发一致）
        val longClickBodies = nodes.filter { it.isLongClickable && isBodyCandidate(it) }
        longClickBodies.maxByOrNull { it.text?.length ?: 0 }?.let { return it }

        val scored = nodes.mapNotNull { cand ->
            if (!isBodyCandidate(cand)) return@mapNotNull null
            val raw = cand.text?.toString()?.trim().orEmpty()
            cand to raw.length
        }
        return scored.maxByOrNull { it.second }?.first
    }

    /**
     * 判断当前是否在微信主页面
     * @return 是否在微信主页面
     */
    private fun isWechatMainPage(): Boolean {
        val nodes = AssistsCore.getAllNodes()
        // 检查是否在通讯录页面
        val isInContactPage = findTextViewByIdAndText(WechatResourceIds.ICON_TV, WechatResourceIds.ButtonTexts.CONTACTS) != null
        
        if (isInContactPage) {
            // 如果在通讯录页面，点击微信切换到主页面
            val wechatTab = findTextViewByIdAndText(WechatResourceIds.ICON_TV, WechatResourceIds.ButtonTexts.WECHAT)
            wechatTab?.findFirstParentClickable()?.click()
        }
        
        // 检查是否在微信主页面
        return findTextViewByIdAndText(WechatResourceIds.TEXT1, WechatResourceIds.ButtonTexts.WECHAT) != null
    }

    /**
     * 检查是否回到微信主页面
     * @param maxAttempts 最大尝试次数
     * @param delayMs 每次尝试之间的延迟时间（毫秒）
     * @return 是否成功回到微信主页面
     */
    private suspend fun checkBackToWechatMain(maxAttempts: Int = 5, delayMs: Long = 1000): Boolean {
        repeat(maxAttempts) { attempt ->
            if (AssistsCore.back()) {
                ("返回第 ${attempt + 1} 次").logAppend()
            }
            delay(delayMs)
            if (isWechatMainPage()) {
                ("到了微信主页面。").logAppend()
                return true
            }
        }
        ("未能返回微信主页面，重新启动微信").logAppend()
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
            ("STEP_1: 开始执行 - 启动微信").logAppend()
            ("启动微信").logAppend()
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.instance?.startActivity(this)
            }
            return@next Step.get(StepTag.STEP_1001, delay = 1000)
        }

        //1001. 获取联系人列表
        collector.next(StepTag.STEP_1001) { step ->
            setLastStep(StepTag.STEP_1001)
            ("STEP_1001: 开始执行 - 获取联系人列表").logAppend()
            
            // 1. 点击通讯录
            val contactTab = findTextViewByIdAndText(WechatResourceIds.ICON_TV, WechatResourceIds.ButtonTexts.CONTACTS)
            
            if (contactTab != null) {
                ("已找到通讯录按钮，1秒后点击").logAppend()
                delay(1000)
                contactTab.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1002, delay = 1000)
            }
            
            ("未找到通讯录按钮，重试").logAppend()
            return@next Step.get(StepTag.STEP_1001, delay = 1000)
        }

        //1002. 点击群聊
        collector.next(StepTag.STEP_1002) { step ->
            setLastStep(StepTag.STEP_1002)
            ("STEP_1002: 开始执行 - 点击群聊").logAppend()
            
            val groupChat = findTextViewByIdAndText(WechatResourceIds.N9, WechatResourceIds.ButtonTexts.GROUP_CHAT)
            
            if (groupChat != null) {
                ("已找到群聊按钮，1秒后点击").logAppend()
                delay(1000)
                groupChat.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1003, delay = 1000)
            }
            
            ("未找到群聊按钮，重试").logAppend()
            return@next Step.get(StepTag.STEP_1002, delay = 1000)
        }

        //1003. 遍历群聊列表
        collector.next(StepTag.STEP_1003) { step ->
            setLastStep(StepTag.STEP_1003)
            ("STEP_1003: 开始执行 - 遍历群聊列表").logAppend()
            
            val groupNames = mutableSetOf<String>()
            val groupNodes = findAllTextViewById(WechatResourceIds.CG1)
            
            groupNodes.forEach { node ->
                node.text?.toString()?.let { name ->
                    if (name.isNotEmpty()) {
                        groupNames.add(name)
                        ("找到群聊: $name").logAppend()
                    }
                }
            }
            
            // 更新 targetGroups
            if (groupNames.isNotEmpty()) {
                targetGroups.clear()
                targetGroups.addAll(groupNames)
                ("已更新目标群组列表").logAppend()
            }
            
            // 返回上一页
            AssistsCore.back()
            return@next Step.get(StepTag.STEP_1004, delay = 1000)
        }

        //1004. 点击标签
        collector.next(StepTag.STEP_1004) { step ->
            setLastStep(StepTag.STEP_1004)
            ("STEP_1004: 开始执行 - 点击标签").logAppend()
            
            val tagButton = findTextViewByIdAndText(WechatResourceIds.N9, WechatResourceIds.ButtonTexts.TAG)
            
            if (tagButton != null) {
                ("已找到标签按钮，1秒后点击").logAppend()
                delay(1000)
                tagButton.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1005, delay = 1000)
            }
            
            ("未找到标签按钮，重试").logAppend()
            return@next Step.get(StepTag.STEP_1004, delay = 1000)
        }

        //1005. 点击转发
        collector.next(StepTag.STEP_1005) { step ->
            setLastStep(StepTag.STEP_1005)
            ("STEP_1005: 开始执行 - 点击转发").logAppend()
            
            val forwardButton = findTextViewByIdAndText(WechatResourceIds.HS8, WechatResourceIds.ButtonTexts.FORWARD)
            
            if (forwardButton != null) {
                ("已找到转发按钮，1秒后点击").logAppend()
                delay(1000)
                forwardButton.findFirstParentClickable()?.click()
                return@next Step.get(StepTag.STEP_1006, delay = 1000)
            }
            
            ("未找到转发按钮，重试").logAppend()
            return@next Step.get(StepTag.STEP_1005, delay = 1000)
        }

        //1006. 获取转发页面的联系人
        collector.next(StepTag.STEP_1006) { step ->
            setLastStep(StepTag.STEP_1006)
            ("STEP_1006: 开始执行 - 获取转发页面的联系人").logAppend()
            
            val contactNodes = findAllTextViewById(WechatResourceIds.KBQ)
            
            contactNodes.forEach { node ->
                node.text?.toString()?.let { name ->
                    if (name.isNotEmpty()) {
                        targetGroups.add(name)
                        ("添加联系人: $name").logAppend()
                    }
                }
            }
            
            // 返回主页面
            if (checkBackToWechatMain()) {
                ("已返回微信主页面").logAppend()
                return@next Step.get(StepTag.STEP_2, delay = 1000)
            }
            
            ("未能返回微信主页面，重试").logAppend()
            return@next Step.get(StepTag.STEP_1006, delay = 1000)
        }

        //2. 点击聊天列表中的京东线报交流群
        collector.next(StepTag.STEP_2) { step ->
            setLastStep(StepTag.STEP_2)
            ("STEP_2: 开始执行 - 查找并点击京东线报交流群。").logAppend()

            // 先判断是否在微信主页面
            if (!isWechatMainPage()) {
                ("当前不在微信主页面，尝试返回主页面。").logAppend()
                if (checkBackToWechatMain()) {
                    ("已在微信主页面。").logAppend()
                } else {
                    ("未能返回微信主页面，重新启动微信。").logAppend()
                    return@next Step.get(StepTag.STEP_1, delay = 1000)
                }
            }

            // 双击底部Tab"微信"
            val tabNodes = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.viewIdResourceName == WechatResourceIds.ICON_TV
                        && it.text?.toString() == WechatResourceIds.ButtonTexts.WECHAT
            }
            
            if (tabNodes.isNotEmpty()) {
                val wechatTab = tabNodes.first()
                wechatTab.findFirstParentClickable()?.let { parent ->
                    parent.click()
                    Thread.sleep(100)
                    parent.click()
                    ("已双击底部Tab微信").logAppend()
                }
            }
            // 查找所有聊天行（每一行的 LinearLayout，id=cj0）
            val allRows = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.LINEAR_LAYOUT && it.viewIdResourceName == WechatResourceIds.CJ0
            }

            // 遍历每一行，递归查找 a_h（小红点） 和 kbq（群名）
            for (row in allRows) {
                val allDescendants = row.getNodes() // 递归获取所有后代节点
                val hasAh = allDescendants.any { it.viewIdResourceName == WechatResourceIds.A_H } // 小红点
                val kbqNode = allDescendants.find {
                    it.viewIdResourceName == WechatResourceIds.KBQ && (it.text?.contains(ContactList.sourceGroupName) == true) // 群名
                }
                val ht5Node = allDescendants.find {
                    it.viewIdResourceName == WechatResourceIds.HT5 && (it.text?.contains(WechatResourceIds.ButtonTexts.FOLLOWED_PEOPLE) == true) // 关注的人
                }
                if (DEBUG && kbqNode != null) { //调试：不需要小红点
                    ("DEBUG 模式，跳过小红点").logAppend()
                    kbqNode.findFirstParentClickable()?.click()
                    ("已找到并点击京东线报交流群").logAppend()
                    return@next Step.get(StepTag.STEP_3, delay = 1000)
                } else if (hasAh && kbqNode != null && ht5Node != null) {
                    ("京东线报交流群有新消息且包含关注的人，1分钟后点击并进入执行。").logAppend()
                    // 倒计时刷新
                    for (secondsLeft in 60 downTo 1) {
                        ("距离点击还有 $secondsLeft 秒...").logAppend()
                        delay(1000)
                    }
                    kbqNode.findFirstParentClickable()?.click()
                    return@next Step.get(StepTag.STEP_3)
                }
            }
            if (DEBUG) {
                ("DEBUG 模式，5秒钟后再检查。").logAppend()
                return@next Step.get(StepTag.STEP_2, delay = 5000)
            }
            // 倒计时刷新，加入提示每剩余多少秒
            for (secondsLeft in 30 downTo 1) {
                ("群里没有新消息, 距离下次检查还有 $secondsLeft 秒...").logAppend()
                delay(1000)
            }
            return@next Step.get(StepTag.STEP_2)
        }

        //3. 获取最后一张图片
        collector.next(StepTag.STEP_3) { step ->
            isLastMsgText = false
            ("设置 isLastMsgText 为 false").logAppend()
            setLastStep(StepTag.STEP_3)
            ("STEP_3: 开始执行 - 获取最后一张图片").logAppend()

            // 1. 获取所有消息块
            val allMsgBlocks = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.RELATIVE_LAYOUT && it.viewIdResourceName == WechatResourceIds.BN1
            }
            
            Log.d("Forward", "找到消息块数量: ${allMsgBlocks.size}")
            allMsgBlocks.forEachIndexed { index, block ->
                Log.d("Forward", "消息块[$index]: bounds=${block.getBoundsInScreen()}")
            }

            // 2. 查找线报员发送的最新图片消息
            var targetImageNode: android.view.accessibility.AccessibilityNodeInfo? = null
            var currentImageBounds: String? = null
            var currentMessageTime: String? = null

            // 倒序遍历，优先取最新
            Log.d("Forward", "要匹配的机器人名称列表: ${ContactList.sourceRobotNames.joinToString(", ")}")
            for (msgBlock in allMsgBlocks.reversed()) {
                // 查找发送者节点
                Log.d("Forward", "查找线报员的图片消息")
                val senderNode = msgBlock.getNodes().find { node ->
                    val nodeText = node.text?.toString()
                    val normalizedNodeText = nodeText?.let { normalizeText(it) }
                    val containsRobotName = ContactList.sourceRobotNames.any { robotName -> 
                        val normalizedRobotName = normalizeText(robotName)
                        val matched = normalizedNodeText?.contains(normalizedRobotName) == true
                        if (node.className == WechatResourceIds.NodeClasses.TEXT_VIEW && node.viewIdResourceName == WechatResourceIds.BRC) {
                            Log.d("Forward", "检查节点: text=$nodeText, normalizedText=$normalizedNodeText, robotName=$robotName, normalizedRobotName=$normalizedRobotName, matched=$matched")
                        }
                        matched
                    }
                    if (containsRobotName) {
                        val matchedName = ContactList.sourceRobotNames.find { robotName -> 
                            val normalizedRobotName = normalizeText(robotName)
                            normalizedNodeText?.contains(normalizedRobotName) == true
                        }
                        Log.d(
                            "Forward",
                            "找到匹配的线报员节点: className=${node.className}, viewIdResourceName=${node.viewIdResourceName}, text=$nodeText, robotName=$matchedName"
                        )
                    }
                    node.className == WechatResourceIds.NodeClasses.TEXT_VIEW &&
                            node.viewIdResourceName == WechatResourceIds.BRC &&
                            containsRobotName
                }

                // 如果找到线报员的消息，再查找图片节点和时间节点
                if (senderNode != null) {
                    Log.d("Forward", "找到线报员的消息")
                    
                    // 打印所有子节点信息用于调试
                    val allChildNodes = msgBlock.getNodes()
                    Log.d("Forward", "消息块子节点数量: ${allChildNodes.size}")
                    
                    // 查找所有BR1节点（时间）
                    val timeNodes = allChildNodes.filter { it.viewIdResourceName == WechatResourceIds.BR1 }
                    Log.d("Forward", "BR1节点数量: ${timeNodes.size}")
                    timeNodes.forEachIndexed { index, node ->
                        Log.d("Forward", "BR1节点[$index]: text=${node.text}, className=${node.className}")
                    }
                    
                    // 查找所有BKO节点（图片）
                    val imageNodes = allChildNodes.filter { it.viewIdResourceName == WechatResourceIds.BKO }
                    Log.d("Forward", "BKO节点数量: ${imageNodes.size}")
                    imageNodes.forEachIndexed { index, node ->
                        Log.d("Forward", "BKO节点[$index]: className=${node.className}, clickable=${node.isClickable}, longClickable=${node.isLongClickable}")
                    }
                    
                    // 查找所有BKG节点（图片，可长按）
                    val bkgNodes = allChildNodes.filter { it.viewIdResourceName == WechatResourceIds.BKG }
                    Log.d("Forward", "BKG节点数量: ${bkgNodes.size}")
                    bkgNodes.forEachIndexed { index, node ->
                        Log.d("Forward", "BKG节点[$index]: className=${node.className}, clickable=${node.isClickable}, longClickable=${node.isLongClickable}")
                    }
                    
                    // 查找时间节点
                    val timeNode = allChildNodes.find {
                        it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                                && it.viewIdResourceName == WechatResourceIds.BR1
                    }
                    currentMessageTime = timeNode?.text?.toString()
                    Log.d("Forward", "currentMessageTime: $currentMessageTime")

                    // 查找图片节点 - 优先查找BKG节点（可长按），然后查找BKO节点
                    val imageNode = allChildNodes.find {
                        it.viewIdResourceName == WechatResourceIds.BKG
                    } ?: allChildNodes.find {
                        it.viewIdResourceName == WechatResourceIds.BKO
                    }
                    Log.d("Forward", "imageNode: $imageNode")
                    
                    if (imageNode != null) {
                        targetImageNode = imageNode
                        currentImageBounds = imageNode.getBoundsInScreen().toShortString()
                        Log.d("Forward", "currentImageBounds: $currentImageBounds")
                        break
                    } else {
                        Log.d("Forward", "当前消息块中没有找到图片节点，继续查找下一个消息块")
                    }
                } else {
                    Log.d("Forward", "当前消息块不是线报员发送的")
                }
            }

            if (targetImageNode == null) {
                ("未找到线报员的图片消息，返回。").logAppend()
                if (checkBackToWechatMain()) {
                    ("返回微信主页面，30秒后重试。").logAppend()
                    return@next Step.get(StepTag.STEP_2, delay = 30000)
                } else {
                    return@next Step.get(StepTag.STEP_2, delay = 30000)
                }
            }

            // 3. 检查时间戳是否发生变化
            if (!DEBUG && !checkMessageTime(currentMessageTime)) {
                ("消息时间未变化，无需转发。").logAppend()
                if (checkBackToWechatMain()) {
                    ("返回微信主页面，30秒后重试。").logAppend()
                    return@next Step.get(StepTag.STEP_2, delay = 30000)
                } else {
                    return@next Step.get(StepTag.STEP_2, delay = 30000)
                }
            }

            pendingMessageTime = currentMessageTime

            // 4. 点击图片
            if (targetImageNode?.isVisibleToUser!! && targetImageNode.isLongClickable && targetImageNode.isEnabled) {
                ("节点可交互。").logAppend()
                if (targetImageNode.click()) {
                    ("点击一下，打开图片。").logAppend()
                    ("延迟 3 秒，充分等待").logAppend()
                    delay(3000)
                    ("点击底部的转发按钮。").logAppend()
                    if (AssistsCore.gestureClick(646f, 2261f)) {
                        return@next Step.get(StepTag.STEP_6, delay = 3000)
                    }
                }
                ("延迟 1 秒").logAppend()
                delay(1000)
                if (targetImageNode.longClick()) {
                    return@next Step.get(StepTag.STEP_4, delay = 3000)
                }
                return@next Step.get(StepTag.STEP_3, delay = 3000)
            } else {
                ("节点不可交互，isVisibleToUser=${targetImageNode.isVisibleToUser}, isLongClickable=${targetImageNode.isLongClickable}, isEnabled=${targetImageNode.isEnabled}").logAppend()
                return@next Step.get(StepTag.STEP_3, delay = 1000)
            }
        }

        //4. 查找并点击"转发"按钮
        collector.next(StepTag.STEP_4) { step ->
            setLastStep(StepTag.STEP_4)
            ("STEP_4: 开始执行 - 查找并点击转发按钮").logAppend()
            // 1. 查找所有 text=转发 且 resource-id=obc 的 TextView
            val forwardTextNodes = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.viewIdResourceName == WechatResourceIds.OBC
                        && it.text?.toString() == WechatResourceIds.ButtonTexts.FORWARD
            }
            if (forwardTextNodes.isNotEmpty()) {
                val forwardTextNode = forwardTextNodes.first()
                // 2. 找到可点击的父 LinearLayout
                val clickableParent = forwardTextNode.findFirstParentClickable()
                if (clickableParent != null) {
                    ("已定位到转发按钮，1秒后点击。").logAppend()
                    return@next Step.get(StepTag.STEP_5, delay = 1000)
                }
            }
            ("未找到转发按钮，重试").logAppend()
            Thread.sleep(1500)
            lastImageBounds = null
            AssistsCore.back()
            return@next Step.get(StepTag.STEP_100, delay = 1000)
        }

        // STEP_5，真正执行点击
        collector.next(StepTag.STEP_5) { step ->
            setLastStep(StepTag.STEP_5)
            ("STEP_5: 开始执行 - 点击转发按钮").logAppend()
            val forwardTextNodes = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.viewIdResourceName == WechatResourceIds.OBC
                        && it.text?.toString() == WechatResourceIds.ButtonTexts.FORWARD
            }
            if (forwardTextNodes.isNotEmpty()) {
                val forwardTextNode = forwardTextNodes.first()
                val clickableParent = forwardTextNode.findFirstParentClickable()
                if (clickableParent != null) {
                    ("延迟 1 秒。").logAppend()
                    delay(1000)
                    clickableParent.click()
                    ("已点击转发按钮").logAppend()
                    return@next Step.get(StepTag.STEP_6)
                }
            } else {
                ("未找到转发按钮，重试").logAppend()
                Thread.sleep(1500)
                lastImageBounds = null
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_100, delay = 1000)
            }
            ("已点击转发按钮，下一步").logAppend()
            return@next Step.get(StepTag.STEP_6, delay = 3000)
        }

        //6. 选择转发对象
        collector.next(StepTag.STEP_6) { step ->
            setLastStep(StepTag.STEP_6)
            ("STEP_6: 开始执行 - 选择转发对象").logAppend()
            ("选择转发对象").logAppend()
            // 1. 查找并点击"多选"按钮
            val multiSelectNode = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.text?.toString()?.contains(WechatResourceIds.ButtonTexts.MULTI_SELECT) == true
                        && it.isClickable
            }
            if (multiSelectNode != null) {
                ("已定位到多选按钮，1秒后点击。").logAppend()
                delay(1000)
                multiSelectNode.click()
                ("已点击多选按钮").logAppend()
                resetGroupIndex() // 重置群组索引
                return@next Step.get(StepTag.STEP_7, delay = 1000)
            } else {
                ("未找到多选按钮，重试").logAppend()
                AssistsCore.back() //返回到聊天窗口
                return@next Step.get(StepTag.STEP_3, delay = 1000)
            }
        }

        //7. 选择目标群组
        collector.next(StepTag.STEP_7) { step ->
            setLastStep(StepTag.STEP_7)
            ("STEP_7: 开始执行 - 选择目标群组").logAppend()
            
            // 如果是 DEBUG 模式，只选择文件传输助手
            if (DEBUG) {
                ("DEBUG 模式，只选择文件传输助手").logAppend()
                val fileTransferNode = AssistsCore.getAllNodes().find {
                    it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                            && it.text?.toString() == WechatResourceIds.ButtonTexts.FILE_TRANSFER
                }
                
                if (fileTransferNode != null) {
                    ("已找到文件传输助手，1秒后点击").logAppend()
                    delay(1000)
                    fileTransferNode.findFirstParentClickable()?.click()
                    ("已点击文件传输助手").logAppend()
                    return@next Step.get(StepTag.STEP_9, delay = 1000)
                } else {
                    ("未找到文件传输助手，尝试滚动列表").logAppend()
                    val listContainer = AssistsCore.getAllNodes().find {
                        it.className == WechatResourceIds.NodeClasses.LIST_VIEW &&
                                it.viewIdResourceName == WechatResourceIds.I3Y
                    }

                    if (listContainer != null && listContainer.scrollForward()) {
                        ("已滚动列表，重试选择文件传输助手").logAppend()
                        return@next Step.get(StepTag.STEP_7, delay = 1000)
                    }
                    
                    ("无法找到文件传输助手，重试").logAppend()
                    return@next Step.get(StepTag.STEP_7, delay = 1000)
                }
            }

            // 如果已经处理完所有群组，进入下一步
            if (currentGroupIndex >= targetGroups.size) {
                ("所有群组已选择完成").logAppend()
                return@next Step.get(StepTag.STEP_9, delay = 1000)
            }

            // 获取当前要选择的群组名称
            val currentGroup = targetGroups.elementAt(currentGroupIndex)
            ("正在选择群组: $currentGroup").logAppend()

            // 查找目标群组节点
            val groupNode = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.text?.toString()?.contains(currentGroup) == true
            }

            if (groupNode != null) {
                ("已定位到群组 $currentGroup，1秒后点击。").logAppend()
                delay(1000)
                groupNode.findFirstParentClickable()?.click()
                ("已点击群组 $currentGroup").logAppend()
                currentGroupIndex++ // 移动到下一个群组
                return@next Step.get(StepTag.STEP_7, delay = 1000)
            } else {
                ("未找到群组 $currentGroup，尝试滚动列表").logAppend()
                val listContainer = AssistsCore.getAllNodes().find {
                    it.className == WechatResourceIds.NodeClasses.LIST_VIEW &&
                            it.viewIdResourceName == WechatResourceIds.I3Y
                }

                if (listContainer != null && listContainer.scrollForward()) {
                    ("已滚动列表，重试选择群组").logAppend()
                    return@next Step.get(StepTag.STEP_7, delay = 1000)
                }
                
                ("无法找到群组 $currentGroup，跳过").logAppend()
                currentGroupIndex++ // 移动到下一个群组
                return@next Step.get(StepTag.STEP_7, delay = 1000)
            }
        }

        //9. 点击"完成"按钮
        collector.next(StepTag.STEP_9) { step ->
            setLastStep(StepTag.STEP_9)
            ("STEP_9: 开始执行 - 查找并点击完成按钮").logAppend()
            val finishBtn = AssistsCore.getAllNodes().find {
                (it.className == WechatResourceIds.NodeClasses.BUTTON || it.className == WechatResourceIds.NodeClasses.TEXT_VIEW)
                        && it.text?.toString()?.contains(WechatResourceIds.ButtonTexts.FINISH) == true
                        && it.isClickable
            }
            if (finishBtn != null) {
                ("已定位到完成按钮，1秒后点击。").logAppend()
                resetRetryCount()
                delay(1000)
                finishBtn.click()
                ("已点击完成按钮").logAppend()
                return@next Step.get(StepTag.STEP_10, delay = 1000)
            } else {
                val currentRetry = incrementRetryCount()
                ("未找到完成按钮，第 $currentRetry 次重试").logAppend()
                if (currentRetry >= 10) {
                    ("重试次数超过10次，尝试返回微信主页面").logAppend()
                    resetRetryCount()
                    if (checkBackToWechatMain()) {
                        return@next Step.get(StepTag.STEP_2, delay = 1000)
                    }
                }
                return@next Step.get(StepTag.STEP_9, delay = 1000)
            }
        }

        //10. 点击"发送"按钮
        collector.next(StepTag.STEP_10) { step ->
            setLastStep(StepTag.STEP_10)
            ("STEP_10: 开始执行 - 查找并点击发送按钮").logAppend()
            val sendBtn = AssistsCore.getAllNodes().find {
                (it.className == WechatResourceIds.NodeClasses.BUTTON || it.className == WechatResourceIds.NodeClasses.TEXT_VIEW)
                        && it.text?.toString()?.contains(WechatResourceIds.ButtonTexts.SEND) == true
                        && it.isClickable
            }
            if (sendBtn != null) {
                ("已定位到发送按钮，1秒后点击。").logAppend()
                delay(1000)
                sendBtn.click()
                confirmMessageTime()
                if (isLastMsgText == true) {
                    ("isLastMsgText 为 true。").logAppend()
                    ("已点击发送按钮，准备查找最新图片消息").logAppend()
                    if (checkBackToWechatMain()) {
                        return@next Step.get(StepTag.STEP_2, delay = 3000)
                    }
                    ("没有到微信主页面。").logAppend()
                    return@next Step.get(StepTag.STEP_100, delay = 3000)
                } else {
                    ("已点击发送按钮，准备查找最新文字消息").logAppend()
                    delay(5000)
                    AssistsCore.back()
                    return@next Step.get(StepTag.STEP_11, delay = 1000)
                }
            } else {
                ("未找到发送按钮，重试").logAppend()
                return@next Step.get(StepTag.STEP_10, delay = 1000)
            }
        }

        // 11. 查找线报员发送的最新一条文字消息，并log输出
        collector.next(StepTag.STEP_11) { step ->
            setLastStep(StepTag.STEP_11)
            ("STEP_11: 开始执行 - 查找线报员最新文字消息").logAppend()
            fun collectMsgBlocks(): List<android.view.accessibility.AccessibilityNodeInfo> {
                return AssistsCore.getAllNodes().filter {
                    it.className == WechatResourceIds.NodeClasses.RELATIVE_LAYOUT && it.viewIdResourceName == WechatResourceIds.BN1
                }
            }
            fun tryEnterSourceChatFromChatList(): Boolean {
                // 会话列表每行 LinearLayout(cj0) 下通常有群名 TextView(kbq)
                val rows = AssistsCore.getAllNodes().filter {
                    it.className == WechatResourceIds.NodeClasses.LINEAR_LAYOUT && it.viewIdResourceName == WechatResourceIds.CJ0
                }
                if (rows.isEmpty()) return false
                val targetName = ContactList.sourceGroupName
                for (row in rows) {
                    val kbq = row.getNodes().find { it.viewIdResourceName == WechatResourceIds.KBQ }
                    if (kbq?.text?.toString()?.contains(targetName) == true) {
                        ("STEP_11: 在会话列表找到线报群「$targetName」，点击进入").logAppend()
                        kbq.findFirstParentClickable()?.click()
                        return true
                    }
                }
                return false
            }

            var allMsgBlocks = collectMsgBlocks()
            var waitRound = 0
            while (allMsgBlocks.isEmpty() && waitRound < 6) {
                waitRound++
                ("STEP_11: 未拿到消息块，等待聊天页就绪 ($waitRound/6)").logAppend()
                delay(700)
                allMsgBlocks = collectMsgBlocks()
            }

            if (allMsgBlocks.isEmpty()) {
                // 关键兜底：此时多半不在聊天消息页（刚从转发/发送返回），需要 back 或重新进群再解析
                step11EmptyBlockRetry++
                val pkg = AssistsCore.getPackageName()
                Log.d("Forward", "STEP_11: 消息块仍为空，package=$pkg, retry=$step11EmptyBlockRetry")

                // 如果不在微信，说明返回键把微信退到桌面/其它App了，直接重启微信主流程
                if (pkg.isNotBlank() && pkg != "com.tencent.mm") {
                    ("STEP_11: 当前不在微信（$pkg），重新启动微信").logAppend()
                    resetStep11EmptyRetry()
                    return@next Step.get(StepTag.STEP_1, delay = 1500)
                }

                // 在微信内但列表为空：优先尝试回到会话列表并进入线报群，再读取消息块
                ("STEP_11: 微信内未拿到消息列表，尝试回到线报群后重试（$step11EmptyBlockRetry）").logAppend()
                if (tryEnterSourceChatFromChatList()) {
                    delay(1200)
                    return@next Step.get(StepTag.STEP_11, delay = 1000)
                }
                // 如果不在会话列表，轻量 back 一次（只一次）尝试回到会话列表
                if (step11EmptyBlockRetry <= 2) {
                    AssistsCore.back()
                    return@next Step.get(StepTag.STEP_11, delay = 1600)
                }

                // 多次失败后，回到微信主页面重新进入群聊再走后续流程
                resetStep11EmptyRetry()
                if (checkBackToWechatMain()) {
                    return@next Step.get(StepTag.STEP_2, delay = 1500)
                }
                return@next Step.get(StepTag.STEP_1, delay = 1500)
            } else {
                // 一旦拿到消息块，重置兜底计数
                resetStep11EmptyRetry()
            }
            
            Log.d("Forward", "STEP_11: 找到消息块数量: ${allMsgBlocks.size}")
            Log.d("Forward", "STEP_11: 查找线报员名称: ${ContactList.sourceRobotNames.joinToString(", ")}")

            var latestMsg: String? = null
            var latestMsgNode: android.view.accessibility.AccessibilityNodeInfo? = null
            var latestMsgIndex = -1
            /** 仅统计线报员消息块内的图片，避免群内其他人发图导致误判「文字比图片旧」 */
            var latestRobotImageIndex = -1

            // 倒序遍历，优先取最新
            for ((i, msgBlock) in allMsgBlocks.withIndex().reversed()) {
                Log.d("Forward", "STEP_11: 检查消息块[$i]")
                
                // 1. 发送者：brc 或昵称资源 id 变化时的回退
                val senderNode = findLineReporterSenderInBlock(msgBlock)
                
                if (senderNode != null) {
                    Log.d("Forward", "STEP_11: 找到线报员消息，发送者: ${senderNode.text}")

                    // 2. 仅线报员块内的图片（与 STEP_3 一致：BKG 优先于 BKO）
                    val imageNode = msgBlock.getNodes().find {
                        it.viewIdResourceName == WechatResourceIds.BKG
                    } ?: msgBlock.getNodes().find {
                        it.viewIdResourceName == WechatResourceIds.BKO
                    }
                    if (latestRobotImageIndex == -1 && imageNode != null) {
                        latestRobotImageIndex = i
                        Log.d("Forward", "STEP_11: 线报员图片消息，索引: $i")
                    }

                    // 3. 正文：BKL 或资源 id 变更时的回退解析
                    Log.d("Forward", "STEP_11: 在线报员消息中查找文字内容")
                    val contentNode = findRobotMessageBodyText(msgBlock, senderNode)
                    if (contentNode != null) {
                        Log.d("Forward", "STEP_11: 找到文字内容: ${contentNode.text}")
                        if (latestMsgIndex == -1) {
                            latestMsg = contentNode.text?.toString()
                            latestMsgNode = contentNode
                            latestMsgIndex = i
                            Log.d("Forward", "STEP_11: 设置最新消息: $latestMsg")
                        }
                    } else {
                        Log.d("Forward", "STEP_11: 未找到文字内容节点")
                    }
                }
            }

            // debug
            if (DEBUG) {
                ("DEBUG模式，设置 lastTextMsg 为 null").logAppend()
                lastTextMsg = null
            }

            // 判断是否需要back
            Log.d("Forward", "STEP_11判断条件:")
            Log.d("Forward", "  latestMsg: $latestMsg")
            Log.d("Forward", "  lastTextMsg: $lastTextMsg")
            Log.d("Forward", "  latestMsgIndex: $latestMsgIndex")
            Log.d("Forward", "  latestRobotImageIndex: $latestRobotImageIndex")
            Log.d("Forward", "  latestMsg == null: ${latestMsg == null}")
            Log.d("Forward", "  latestMsg == lastTextMsg: ${latestMsg == lastTextMsg}")
            Log.d("Forward", "  latestMsgIndex < latestRobotImageIndex: ${latestMsgIndex < latestRobotImageIndex}")
            Log.d("Forward", "  latestRobotImageIndex != -1: ${latestRobotImageIndex != -1}")
            Log.d("Forward", "  (latestMsgIndex < latestRobotImageIndex && latestRobotImageIndex != -1): ${latestMsgIndex < latestRobotImageIndex && latestRobotImageIndex != -1}")
            
            if (latestMsg == null) {
                Log.d("Forward", "STEP_11: 返回原因 - latestMsg为null")
                ("未解析到线报员文字正文，返回。").logAppend()
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 1000)
            } else if (latestMsg == lastTextMsg) {
                Log.d("Forward", "STEP_11: 返回原因 - 消息内容未变化")
                ("文字消息未更新（与上次相同），返回。").logAppend()
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 1000)
            } else if (latestMsgIndex < latestRobotImageIndex && latestRobotImageIndex != -1) {
                Log.d("Forward", "STEP_11: 返回原因 - 线报员文字早于其最新图片（应先处理图片流程）")
                ("线报员最新一条为图片，文字为旧消息，返回。").logAppend()
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, delay = 1000)
            }

            // 内容有变化，复制内容并处理
            lastTextMsg = latestMsg
            ProcessedMsgText = processAtangText(latestMsg)

            if (checkBackToWechatMain()) {
                return@next Step.get(StepTag.STEP_12, delay = 3000)
            }
            return@next Step.get(StepTag.STEP_12, delay = 1000)
        }

        //12. 进入京粉并自动发消息
        collector.next(StepTag.STEP_12) { step ->
            setLastStep(StepTag.STEP_12)
            ("STEP_12: 开始执行 - 查找并进入京粉").logAppend()
            // 1. 查找所有聊天行（每一行的 LinearLayout，id=cj0）
            val allRows = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.LINEAR_LAYOUT && it.viewIdResourceName == WechatResourceIds.CJ0
            }

            // 2. 遍历每一行，查找 kbq（群名）
            for (row in allRows) {
                val allDescendants = row.getNodes() // 递归获取所有后代节点
                val kbqNode = allDescendants.find {
                    it.viewIdResourceName == WechatResourceIds.KBQ && (it.text?.contains("京粉") == true)
                }
                if (kbqNode != null) {
                    ("已找到并定位到京粉，1秒后点击。").logAppend()
                    delay(1000)
                    kbqNode.findFirstParentClickable()?.click()
                    ("已找到并点击京粉").logAppend()
                    return@next Step.get(StepTag.STEP_13)
                }
            }

            // 3. 如果没找到，尝试滚动列表
            val listContainer = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.LIST_VIEW &&
                        it.viewIdResourceName == WechatResourceIds.I3Y
            }

            if (listContainer != null && listContainer.scrollForward()) {
                ("未找到京粉，向下滚动后重试").logAppend()
                return@next Step.get(StepTag.STEP_12, delay = 1000)
            }
            return@next Step.get(StepTag.STEP_13, delay = 1000)

        }

        //13. 切换到发消息并粘贴内容
        collector.next(StepTag.STEP_13) { step ->
            setLastStep(StepTag.STEP_13)
            ("STEP_13: 开始执行 - 切换到发消息").logAppend()
            val switchMsgNode = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.IMAGE_VIEW
                        && it.viewIdResourceName == WechatResourceIds.BLP
                        && it.isClickable
                        && it.contentDescription?.contains(WechatResourceIds.ButtonTexts.SWITCH_TO_MESSAGE) == true
            }
            if (switchMsgNode != null) {
                ("已定位到切换到发消息按钮，1秒后点击。").logAppend()
                delay(1000)
                switchMsgNode.click()
                ("已点击切换到发消息").logAppend()
                return@next Step.get(StepTag.STEP_14, delay = 1000)
            } else {
                ("未找到切换到发消息按钮，重试").logAppend()
                return@next Step.get(StepTag.STEP_13, delay = 1000)
            }
        }

        //14. 点击输入框并设置文本内容
        collector.next(StepTag.STEP_14) { step ->
            if (lastStep != StepTag.STEP_14) resetRetryCount()
            setLastStep(StepTag.STEP_14)
            ("STEP_14: 开始执行 - 点击输入框并设置文本内容").logAppend()
            if (lastStep == StepTag.STEP_15) {
                val editTextNode = findInputBoxWithRetry()

                if (editTextNode != null) {
                    if (!ProcessedMsgText.isNullOrBlank()) {
                        editTextNode.setNodeText(ProcessedMsgText)
                        ("已设置文本内容").logAppend()
                        return@next Step.get(StepTag.STEP_15, delay = 1000)
                    }
                    ("文本内容为空，重试").logAppend()
                    incrementRetryCount()
                    if (retryCount >= MAX_RETRY_FIND_INPUT) {
                        ("STEP_14 未找到输入框重试超过${MAX_RETRY_FIND_INPUT}次，返回主流程").logAppend()
                        resetRetryCount()
                        if (checkBackToWechatMain()) return@next Step.get(StepTag.STEP_2, delay = 2000)
                        else return@next Step.get(StepTag.STEP_1, delay = 2000)
                    }
                    return@next Step.get(StepTag.STEP_14, delay = 2000)
                }
                ("未找到输入框，重试").logAppend()
                incrementRetryCount()
                if (retryCount >= MAX_RETRY_FIND_INPUT) {
                    ("STEP_14 未找到输入框重试超过${MAX_RETRY_FIND_INPUT}次，返回主流程").logAppend()
                    resetRetryCount()
                    if (checkBackToWechatMain()) return@next Step.get(StepTag.STEP_2, delay = 2000)
                    else return@next Step.get(StepTag.STEP_1, delay = 2000)
                }
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            }

            // 查找输入框（带延迟重试，应对界面未加载或节点树未刷新）
            val editTextNode = findInputBoxWithRetry()

            if (editTextNode != null) {
                ("已定位到输入框。").logAppend()

                if (!ProcessedMsgText.isNullOrBlank()) {
                    editTextNode.setNodeText(ProcessedMsgText)
                    ("已设置文本内容").logAppend()
                    return@next Step.get(StepTag.STEP_15, delay = 1000)
                }
                ("文本内容为空，重试").logAppend()
                incrementRetryCount()
                if (retryCount >= MAX_RETRY_FIND_INPUT) {
                    ("STEP_14 未找到输入框重试超过${MAX_RETRY_FIND_INPUT}次，返回主流程").logAppend()
                    resetRetryCount()
                    if (checkBackToWechatMain()) return@next Step.get(StepTag.STEP_2, delay = 2000)
                    else return@next Step.get(StepTag.STEP_1, delay = 2000)
                }
                return@next Step.get(StepTag.STEP_14, delay = 2000)
            }

            ("未找到输入框，重试").logAppend()
            incrementRetryCount()
            if (retryCount >= MAX_RETRY_FIND_INPUT) {
                ("STEP_14 未找到输入框重试超过${MAX_RETRY_FIND_INPUT}次，返回主流程").logAppend()
                resetRetryCount()
                if (checkBackToWechatMain()) return@next Step.get(StepTag.STEP_2, delay = 2000)
                else return@next Step.get(StepTag.STEP_1, delay = 2000)
            }
            return@next Step.get(StepTag.STEP_14, delay = 2000)
        }

        //15. 点击发送按钮
        collector.next(StepTag.STEP_15) { step ->
            setLastStep(StepTag.STEP_15)
            ("STEP_15: 开始执行 - 点击发送按钮").logAppend()
            ("延迟 1 秒让节点加载").logAppend()
            delay(1000) //延迟 1 秒让节点加载
            val sendBtn = AssistsCore.getAllNodes().find {
                (it.className == WechatResourceIds.NodeClasses.BUTTON || it.className == WechatResourceIds.NodeClasses.TEXT_VIEW)
                        && it.text?.contains(WechatResourceIds.ButtonTexts.SEND) == true && it.isClickable
            }
            if (sendBtn != null) {
                ("已定位到发送按钮，1秒后点击。").logAppend()
                delay(1000)
                sendBtn.click()
                ("已点击发送按钮，进入下一步").logAppend()
                return@next Step.get(StepTag.STEP_16, delay = 5000)
            } else {
                ("未找到发送按钮，重试").logAppend()
                return@next Step.get(StepTag.STEP_14, delay = 1000)
            }
        }

        collector.next(StepTag.STEP_16) { step ->
            setLastStep(StepTag.STEP_16)
            ("STEP_16: 开始执行 - 查找京粉最新文字消息").logAppend()

            // 1. 获取所有消息块
            val allMsgBlocks = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.RELATIVE_LAYOUT && it.viewIdResourceName == WechatResourceIds.BN1
            }

            // 2. 查找京粉发送的最新文字消息
            var latestMsg: String? = null
            var latestMsgNode: android.view.accessibility.AccessibilityNodeInfo? = null

            // 倒序遍历，优先取最新
            for (msgBlock in allMsgBlocks.reversed()) {
                // 查找消息内容节点
                val contentNode = msgBlock.getNodes().find {
                    it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                            && it.viewIdResourceName == WechatResourceIds.BKL
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
                    ("复制到全局变量ProcessedMsgText。").logAppend()
                } else {
                    ("消息内容不包含jd.com链接，跳过处理").logAppend()
                }
                if (checkBackToWechatMain()) {
                    return@next Step.get(StepTag.STEP_17, delay = 3000)
                }
                return@next Step.get(StepTag.STEP_17, delay = 1000)
            } else {
                ("未找到京粉的文字消息，重试").logAppend()
                return@next Step.get(StepTag.STEP_16, delay = 1000)
            }
        }

        //17. 双击顶部微信并进入文件传输助手
        collector.next(StepTag.STEP_17) { step ->
            setLastStep(StepTag.STEP_17)
            ("STEP_17: 开始执行 - 双击顶部微信并进入文件传输助手").logAppend()
            // 1. 查找顶部的"微信"文本
            val wechatNode = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.viewIdResourceName == WechatResourceIds.TEXT1
                        && it.text?.toString() == WechatResourceIds.ButtonTexts.WECHAT
            }

            if (wechatNode != null) {
                // 双击"微信"
                wechatNode.findFirstParentClickable()?.let { parent ->
                    parent.click()
                    delay(100)
                    parent.click()
                    ("已双击顶部微信").logAppend()
                }
            } else {
                ("未找到顶部微信，重试").logAppend()
                return@next Step.get(StepTag.STEP_17, delay = 1000)
            }

            // 2. 查找并点击"文件传输助手"
            val fileTransferNode = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.VIEW
                        && it.viewIdResourceName == WechatResourceIds.KBQ
                        && it.text?.toString() == WechatResourceIds.ButtonTexts.FILE_TRANSFER
            }

            if (fileTransferNode != null) {
                ("已定位到文件传输助手，1秒后点击。").logAppend()
                delay(1000)
                fileTransferNode.findFirstParentClickable()?.click()
                ("已点击文件传输助手").logAppend()
                return@next Step.get(StepTag.STEP_18, delay = 1000)
            } else {
                ("未找到文件传输助手，重试").logAppend()
                return@next Step.get(StepTag.STEP_17, delay = 1000)
            }
        }

        //18. 在文件传输助手中粘贴内容并发送
        collector.next(StepTag.STEP_18) { step ->
            if (lastStep != StepTag.STEP_18) resetRetryCount()
            setLastStep(StepTag.STEP_18)
            ("STEP_18: 开始执行 - 在文件传输助手中粘贴内容并发送").logAppend()
            // 1. 查找输入框（带延迟重试，应对界面未加载或节点树未刷新）
            val editTextNode = findInputBoxWithRetry()

            if (editTextNode != null) {
                if (!ProcessedMsgText.isNullOrBlank()) {
                    editTextNode.setNodeText(ProcessedMsgText)
                    ("已设置文本内容。").logAppend()
                    delay(1000)
                }
                    // 4. 查找并点击发送按钮
                    val sendBtn = AssistsCore.getAllNodes().find {
                        (it.className == WechatResourceIds.NodeClasses.BUTTON || it.className == WechatResourceIds.NodeClasses.TEXT_VIEW)
                                && it.text?.contains(WechatResourceIds.ButtonTexts.SEND) == true && it.isClickable
                    }
                    if (sendBtn != null) {
                        ("已定位到发送按钮，1秒后点击。").logAppend()
                        delay(1000)
                        sendBtn.click()
                        ("已点击发送按钮，完成所有步骤").logAppend()
                        return@next Step.get(StepTag.STEP_19, delay = 1000)
                    } else {
                        ("未找到发送按钮，重试").logAppend()
                        return@next Step.get(StepTag.STEP_18, delay = 1000)
                    }
            } else {
                ("未找到输入框，重试").logAppend()
                incrementRetryCount()
                if (retryCount >= MAX_RETRY_FIND_INPUT) {
                    ("STEP_18 未找到输入框重试超过${MAX_RETRY_FIND_INPUT}次，返回主流程").logAppend()
                    resetRetryCount()
                    if (checkBackToWechatMain()) return@next Step.get(StepTag.STEP_2, delay = 2000)
                    else return@next Step.get(StepTag.STEP_1, delay = 2000)
                }
                return@next Step.get(StepTag.STEP_18, delay = 2000)
            }
        }

        //19. 长按文字区域并查找转发按钮
        collector.next(StepTag.STEP_19) { step ->
            setLastStep(StepTag.STEP_19)
            ("STEP_19: 开始执行 - 长按文字区域并查找转发按钮").logAppend()
            // 增加重试计数
            if (retryCount >= MAX_RETRY_FIND_INPUT) {
                ("STEP_19重试超过${MAX_RETRY_FIND_INPUT}次，返回微信主页面并重置计数").logAppend()
                resetRetryCount()
                if (checkBackToWechatMain()) {
                    return@next Step.get(StepTag.STEP_2, delay = 1000)
                } else {
                    return@next Step.get(StepTag.STEP_1, delay = 1000)
                }
            }
            retryCount++
            
            // 1. 长按文字区域（com.tencent.mm:id/bkl并且long-clickable为true），等待弹出菜单
            val longClickableTextNodes = AssistsCore.getAllNodes().filter {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.viewIdResourceName == WechatResourceIds.BKL
                        && it.isLongClickable
                        && it.isVisibleToUser
                        && !it.text.isNullOrBlank()
            }
            
            Log.d("Forward", "STEP_19: 找到可长按的文字节点数量: ${longClickableTextNodes.size}")
            
            if (longClickableTextNodes.isEmpty()) {
                ("未找到可长按的文字区域，重试").logAppend()
                return@next Step.get(StepTag.STEP_19, delay = 1000)
            }
            
            // 长按最后一个文字节点（最新的消息）
            val targetTextNode = longClickableTextNodes.last()
            Log.d("Forward", "STEP_19: 长按文字节点，内容: ${targetTextNode.text}")
            
            // 获取文字节点的边界，计算右下角靠近边缘10个点的位置
            val bounds = targetTextNode.getBoundsInScreen()
            if (bounds != null) {
                val rightX = bounds.right - 10f  // 距离右边缘10个点
                val bottomY = bounds.bottom - 10f  // 距离下边缘10个点
                Log.d("Forward", "STEP_19: 文字节点右下角坐标: ($rightX, $bottomY)")
                
                // 1. 双击右下角靠近边缘10个点的位置，此时会将文字信息全屏
                if (AssistsCore.gestureClick(rightX, bottomY)) {
                    delay(100) // 短暂延迟
                    if (AssistsCore.gestureClick(rightX, bottomY)) {
                        ("已双击文字区域，等待全屏显示").logAppend()
                        delay(3000) // 等待全屏显示
                        
                        // 2. 查找android.widget.ImageButton并且contentDescription?.toString()是'分享'
                        ("开始查找分享按钮").logAppend()
                        
                        val shareButton = AssistsCore.getAllNodes().find {
                            it.className == WechatResourceIds.NodeClasses.IMAGE_BUTTON
                                    && it.contentDescription?.toString() == WechatResourceIds.ButtonTexts.SHARE
                                    && it.isClickable
                                    && it.isVisibleToUser
                        }
                        
                        Log.d("Forward", "STEP_19: 找到分享按钮: $shareButton")
                        
                        // 3. 点击该按钮，然后就设置isLastMsgText = true，ProcessedMsgText = null，resetRetryCount()，跳转到STEP6
                        if (shareButton != null) {
                            Log.d("Forward", "STEP_19: 分享按钮bounds: ${shareButton.getBoundsInScreen()}")
                            if (shareButton.click()) {
                                ("成功点击分享按钮").logAppend()
                                isLastMsgText = true
                                ("设置 isLastMsgText 为 true").logAppend()
                                ProcessedMsgText = null
                                ("设置 ProcessedMsgText 为 null").logAppend()
                                resetRetryCount()
                                return@next Step.get(StepTag.STEP_6, delay = 1000)
                            } else {
                                ("点击分享按钮失败").logAppend()
                            }
                        } else {
                            ("未找到分享按钮").logAppend()
                        }
                        
                        // 否则重试
                        ("查找分享按钮失败，重试").logAppend()
                        // 点击返回按钮或空白区域退出全屏
                        AssistsCore.gestureClick(50f, 155f)
                        return@next Step.get(StepTag.STEP_19, delay = 1000)
                    } else {
                        ("第二次点击失败，重试").logAppend()
                        return@next Step.get(StepTag.STEP_19, delay = 1000)
                    }
                } else {
                    ("第一次点击失败，重试").logAppend()
                    return@next Step.get(StepTag.STEP_19, delay = 1000)
                }
            } else {
                ("无法获取文字节点边界，重试").logAppend()
                return@next Step.get(StepTag.STEP_19, delay = 1000)
            }
        }

        //100. 恢复到微信主页面
        collector.next(StepTag.STEP_100) { step ->
            setLastStep(StepTag.STEP_100)
            ("STEP_100: 开始执行 - 恢复到微信主页面").logAppend()

            // 1. 查找顶部的"微信"文本
            val wechatNode = AssistsCore.getAllNodes().find {
                it.className == WechatResourceIds.NodeClasses.TEXT_VIEW
                        && it.viewIdResourceName == WechatResourceIds.TEXT1
                        && it.text?.toString() == WechatResourceIds.ButtonTexts.WECHAT
            }

            wechatNode?.findFirstParentClickable()?.let { parent ->
                parent.click()
                delay(100)
                parent.click()
                ("已双击顶部微信，成功返回主页面").logAppend()
                return@next Step.get(StepTag.STEP_2, delay = 1000)
            }

            // 2. 如果没找到顶部微信，尝试多次返回
            if (checkBackToWechatMain(10)) {
                return@next Step.get(StepTag.STEP_2, delay = 1000)
            }

            ("未能返回微信主页面，重新启动微信").logAppend()
            return@next Step.get(StepTag.STEP_1, delay = 1000)
        }
    }
}