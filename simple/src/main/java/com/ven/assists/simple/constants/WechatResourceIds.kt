package com.ven.assists.simple.constants

/**
 * 微信应用资源ID常量类
 * 用于统一管理微信相关的资源ID，提高代码可维护性
 */
object WechatResourceIds {
    
    // 底部导航栏相关
    const val ICON_TV = "com.tencent.mm:id/icon_tv" //底部导航栏
    const val TEXT1 = "android:id/text1" //底部导航栏
    
    // 通讯录相关
    const val N9 = "com.tencent.mm:id/n9" //通讯录
    const val CG1 = "com.tencent.mm:id/cg1" //要转发的群组名
    
    // 聊天列表相关
    const val CJ0 = "com.tencent.mm:id/cj0" //所有聊天行（每一行的 LinearLayout，id=cj0）
    const val KBQ = "com.tencent.mm:id/kbq" //转发页面的联系人
    const val A_H = "com.tencent.mm:id/a_h" //小红点
    const val HT5 = "com.tencent.mm:id/ht5" //关注的人
    const val I3Y = "com.tencent.mm:id/i3y" //聊天列表
    
    // 消息相关
    const val BN1 = "com.tencent.mm:id/bn1" //所有消息块
    const val BRC = "com.tencent.mm:id/brc" //发送者
    const val BR1 = "com.tencent.mm:id/br1" //消息时间
    const val BKO = "com.tencent.mm:id/bko" //图片
    const val BKG = "com.tencent.mm:id/bkg" //图片（可长按）
    const val BKL = "com.tencent.mm:id/bkl" //消息内容
    const val BKK = "com.tencent.mm:id/bkk" //输入框
    const val BLP = "com.tencent.mm:id/blp" //发送按钮
    
    // 转发相关
    const val HS8 = "com.tencent.mm:id/hs8" //转发按钮
    const val OBC = "com.tencent.mm:id/obc" //转发按钮
    
    // 按钮文本常量
    object ButtonTexts {
        const val WECHAT = "微信"
        const val CONTACTS = "通讯录"
        const val GROUP_CHAT = "群聊"
        const val TAG = "标签"
        const val FORWARD = "转发"
        const val MULTI_SELECT = "多选"
        const val FINISH = "完成"
        const val SEND = "发送"
        const val SWITCH_TO_MESSAGE = "切换到发消息"
        const val SHARE = "分享"
        const val FILE_TRANSFER = "文件传输助手"
        const val FOLLOWED_PEOPLE = "关注的人"
    }
    
    // 节点类名常量
    object NodeClasses {
        const val TEXT_VIEW = "android.widget.TextView"
        const val LINEAR_LAYOUT = "android.widget.LinearLayout"
        const val RELATIVE_LAYOUT = "android.widget.RelativeLayout"
        const val LIST_VIEW = "android.widget.ListView"
        const val EDIT_TEXT = "android.widget.EditText"
        const val BUTTON = "android.widget.Button"
        const val IMAGE_VIEW = "android.widget.ImageView"
        const val IMAGE_BUTTON = "android.widget.ImageButton"
        const val VIEW = "android.view.View"
    }
}
