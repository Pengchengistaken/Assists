package com.ven.assists.simple.overlays

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.containsText
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.getNodes
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowWrapper
import com.ven.assists.simple.common.LogWrapper.logAppend
import com.ven.assists.simple.databinding.AdvancedOverlayBinding
import com.ven.assists.simple.step.AntForestEnergy
import com.ven.assists.simple.step.Forward
import com.ven.assists.simple.step.GestureBottomTab
import com.ven.assists.simple.step.GestureScrollSocial
import com.ven.assists.simple.step.OpenWechatSocial
import com.ven.assists.simple.step.PublishSocial
import com.ven.assists.simple.step.ScrollContacts
import com.ven.assists.simple.step.StepTag
import com.ven.assists.stepper.StepManager
import com.ven.assists.utils.CoroutineWrapper

@SuppressLint("StaticFieldLeak")
object OverlayAdvanced : AssistsServiceListener {


    var viewBinding: AdvancedOverlayBinding? = null
        private set
        get() {
            if (field == null) {
                field = AdvancedOverlayBinding.inflate(LayoutInflater.from(AssistsService.instance)).apply {
                    btnForward.setOnClickListener {
                        OverlayLog.show()
                        StepManager.execute(Forward::class.java, StepTag.STEP_1, begin = true)
                    }
                }
            }
            return field
        }

    var onClose: ((parent: View) -> Unit)? = null

    var showed = false
        private set
        get() {
            assistWindowWrapper?.let {
                return AssistsWindowManager.isVisible(it.getView())
            } ?: return false
        }

    var assistWindowWrapper: AssistsWindowWrapper? = null
        private set
        get() {
            viewBinding?.let {
                if (field == null) {
                    field = AssistsWindowWrapper(it.root, wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                        width = (ScreenUtils.getScreenWidth() * 0.8).toInt()
                        height = (ScreenUtils.getScreenHeight() * 0.5).toInt()
                    }, onClose = this.onClose).apply {
                        minWidth = (ScreenUtils.getScreenWidth() * 0.6).toInt()
                        minHeight = (ScreenUtils.getScreenHeight() * 0.4).toInt()
                        initialCenter = true
                        viewBinding.tvTitle.text = "高级示例"
                    }
                }
            }
            return field
        }

    fun show() {
        if (!AssistsService.listeners.contains(this)) {
            AssistsService.listeners.add(this)
        }
        AssistsWindowManager.add(assistWindowWrapper)
    }

    fun hide() {
        AssistsWindowManager.removeView(assistWindowWrapper?.getView())
    }

    override fun onUnbind() {
        viewBinding = null
        assistWindowWrapper = null
    }
}