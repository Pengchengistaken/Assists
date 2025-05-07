package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.containsText
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.App
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
        collector.next(StepTag.STEP_2) {
            // 打印所有节点文本，调试用
//            AssistsCore.getAllNodes().forEach {
//                LogWrapper.logAppend("节点文本: ${it.text}")
//            }
            // 尝试模糊查找
            AssistsCore.getAllNodes().find { it.containsText("京东线报交流群") }?.let {
                LogWrapper.logAppend("找到京东线报相关群，点击进入")
                it.findFirstParentClickable()?.let { parent ->
                    parent.click()
                    return@next Step.get(StepTag.STEP_3)
                }
            }
            if (AssistsCore.getPackageName() == App.TARGET_PACKAGE_NAME) {
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2)
            }
            if (it.repeatCount == 5) {
                return@next Step.get(StepTag.STEP_1)
            }
            return@next Step.repeat
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