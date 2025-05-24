**利用Android无障碍服务（AccessibilityService）能做什么**

可以开发各种各样的自动化脚本程序以及协助脚本，比如：
1. 微信自动转发消息
2. 微信自动接听电话
3. 支付宝蚂蚁森林自动浇水
4. 支付宝芭芭农场自动施肥、自动收集能量...
5. 各种平台的拓客、引流、营销系统
6. 远程控制

# Assists作用
基于Android无障碍服务（AccessibilityService）封装的框架
1. 简化自动化脚本开发
2. 为自动化脚本提供各种增强能力
3. 提高脚本易维护性

# 主要能力
1. 易于使用的无障碍服务API
2. 浮窗管理器：易于实现及管理浮窗
3. 步骤器：为快速实现、可复用、易维护的自动化步骤提供框架及管理
4. 配套屏幕管理：快速生成输出屏幕截图、元素截图
5. 屏幕管理结合opencv：便于屏幕内容识别为自动化提供服务

# 功能示例：微信消息自动转发系统

## 主要功能
1. 自动监控指定微信群的新消息
2. 自动转发图片消息到指定群组
3. 自动处理文字消息，包括：
   - 处理消息源的消息（替换特定文本）
   - 处理京粉消息
4. 支持消息转发到文件传输助手
5. 支持调试模式，方便开发和测试

## 工作流程
1. 启动微信并进入主界面
2. 监控指定群的新消息
3. 发现新消息后：
   - 图片消息：自动转发到指定群组
   - 文字消息：处理后转发到京粉群
4. 自动处理转发后的消息
5. 支持异常处理和自动恢复

## 特色功能
1. 智能消息处理
   - 自动识别消息类型（图片/文字）
   - 智能处理消息内容
   - 自动添加防失联信息
2. 异常处理机制
   - 自动检测并处理各种异常情况
   - 支持自动返回到主界面
   - 支持服务重启和恢复
3. 调试模式
   - 支持开启/关闭调试模式
   - 详细的日志记录
   - 可视化调试信息

# 快速开始
### 1. 导入依赖
#### 1.1 项目根目录build.gradle添加
```
allprojects {
    repositories {
        //添加jitpack仓库
        maven { url 'https://jitpack.io' }
    }
}
```

#### 1.2 主模块build.gradle添加
最新版本：[![](https://jitpack.io/v/ven-coder/Assists.svg)](https://jitpack.io/#ven-coder/Assists)
```
dependencies {
    //按需添加
    //基础库（必须）
    implementation "com.github.ven-coder.Assists:assists-base:最新版本"
    //屏幕录制相关（可选）
    implementation "com.github.ven-coder.Assists:assists-mp:最新版本"
    //opencv相关（可选）
    implementation "com.github.ven-coder.Assists:assists-opcv:最新版本"
}
```

### 2. 注册&开启服务
#### 2.1 主模块AndroidManifest.xml中注册服务
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.ven.assists.simple">

    <application
        android:name="com.ven.assists.simple.App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:requestLegacyExternalStorage="true"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="true">
        
        <service
            android:name="com.google.android.accessibility.selecttospeak.SelectToSpeakService"
            android:enabled="true"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <!--android:priority="10000" 可提高服务在设置中的权重，排在前面     -->
            <intent-filter android:priority="10000">
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/assists_service" />
        </service>
    </application>
</manifest>
```

#### 2.2 开启服务
调用```AssistsCore.openAccessibilitySetting()```跳转到无障碍服务设置页面，找到对应的应用开启服务。

### 3 修改配置
#### 3.1 在ContactList.kt中修改线报来源和发单机器人名称

# 使用说明
1. 确保微信已登录并保持在线
2. 确保目标群组存在
3. 开启无障碍服务
4. 启动应用，系统会自动开始监控和转发消息

# 微信配置
1. 将所有相关的群置顶
2. 在线报群里的发单机器人设置为关注的群成员
3. 将要转发的联系人添加标签：转发
4. 将要转发的群添加到通讯录

# 注意事项
1. 请确保手机保持亮屏状态
2. 建议开启调试模式进行测试
3. 如遇到异常，系统会自动尝试恢复
4. 请勿频繁切换应用，以免影响服务运行

# 使用Appium获取节点
参考：https://juejin.cn/post/7483409317564907530

1. 打开 https://inspector.appiumpro.com/
2. 输入
```
{
  "platformName": "Android",
  "appium:deviceName": "Android Emulator",
  "appium:automationName": "UiAutomator2"
}
```
3. 查找节点。

# License
[GNU General Public License v3.0](https://github.com/ven-coder/Assists/blob/master/LICENSE)
