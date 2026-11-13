# 权限与系统设置

以下内容对应当前 [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) 和实际代码。普通权限、系统角色以及服务绑定限制是不同机制。

## 先授予默认电话角色

`RoleManager.ROLE_DIALER` 是自动接听的核心前提。安装后点击「设为默认电话应用」，由用户在系统弹窗选择轻接。没有该角色，本应用不会作为默认电话界面接收相应通话服务事件，代码也不会自动请求接听。

选择角色会改变手机的默认电话应用，不只是打开一个来电监听开关。要恢复系统电话应用，可在「默认应用 → 电话」切换回去。参见 [InCallService 默认电话要求](https://developer.android.com/reference/android/telecom/InCallService#becoming-the-default-phone-app)。

## 清单中声明的权限

| 权限 | 类型 / 适用版本 | 本应用用途 | 拒绝或未启用的影响 |
| --- | --- | --- | --- |
| `android.permission.CALL_PHONE` | 危险权限，可能由角色授权或在使用功能时申请 | 拨号页通过 `TelecomManager.placeCall()` 发起电话 | 无法从本应用发起呼叫；自动接听判断本身不检查它 |
| `android.permission.READ_CALL_LOG` | 危险且受系统限制的权限；应用先检查默认电话身份再申请 | 查询系统通话记录，显示最近 50 条 | 通话记录页不可用；不参与自动接听条件 |
| `android.permission.POST_NOTIFICATIONS` | Android 13+ 运行时权限 | 显示来电 / 正在通话通知及接听、挂断入口 | 通知入口可能不可见；本应用不把它作为请求接听的条件，建议允许 |
| `android.permission.USE_FULL_SCREEN_INTENT` | 清单权限；Android 14+ 另受全屏通知特殊访问控制 | 关闭自动接听或自动请求异常时，给手动处理来电提供全屏界面 | 全屏来电界面可能受限；自动接听不需要先打开它 |
| `android.permission.FOREGROUND_SERVICE` | 普通清单权限，无单独运行时授权弹窗 | 通话期间启动前台状态并附带通话通知 | 属于当前实现的服务声明，用户无需手动授予 |
| `android.permission.FOREGROUND_SERVICE_PHONE_CALL` | Android 14+ 电话类型前台服务的普通清单权限 | 与 `foregroundServiceType="phoneCall"` 一起声明电话用途 | Android 13 无需单独操作；Android 14+ 还须满足默认电话角色等系统前提 |

权限定义见 [Android Manifest.permission](https://developer.android.com/reference/android/Manifest.permission)。某些电话权限可能随默认电话角色一并授权；具体弹窗取决于系统和安装状态。

Android 13 的通知权限并不是启动前台服务本身的前提，但前台服务仍须提供通知；拒绝后显示位置和可用入口可能受影响。参见 [通知运行时权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)。电话前台服务的角色条件见 [Phone call 类型](https://developer.android.com/develop/background-work/services/fgs/service-types#phone-call)。

## BIND_INCALL_SERVICE 不是让用户手动授权的权限

服务声明是：

```xml
<service
    android:name=".AnswerService"
    android:exported="true"
    android:foregroundServiceType="phoneCall"
    android:permission="android.permission.BIND_INCALL_SERVICE">
    <meta-data android:name="android.telecom.IN_CALL_SERVICE_UI" android:value="true" />
    <intent-filter>
        <action android:name="android.telecom.InCallService" />
    </intent-filter>
</service>
```

这里的 `android:permission` 限制调用方：必须持有系统签名级绑定权限才能绑定该服务。它不是应用通过 `<uses-permission>` 申请的运行时权限，也不需要 Root、ADB 授权或用户在权限页寻找同名开关。服务必须能被系统绑定，所以保留 `exported="true"`，并由绑定权限保护。参见 [官方服务声明要求](https://developer.android.com/reference/android/telecom/InCallService)。

## Z50 的后台设置

电池优化、自启动和后台运行属于系统 / 厂商设置，不是上述 Android 运行时权限。

1. 在轻接点击「电池优化设置」，找到轻接，选择「不优化」或对应的不限制选项。
2. 若 MyOS 的应用管理中有自启动、允许后台运行等选项，为轻接开启。
3. 保持默认电话身份和自动接听开关，关屏、锁屏后用另一部手机拨号确认。

中兴官方的通用路径为「设置 → 电池 → 电池优化」；Z50 的菜单以实际 MyOS 版本为准。[中兴官方说明](https://support.ztedevices.com/en-uk/why-do-background-programs-always-close/)

应用只打开系统电池优化设置页，没有声明 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 来直接申请豁免，也没有启动空闲时常驻的前台服务。

## 没有申请的权限

- 没有 `INTERNET`：应用无网络接口，不上传来电号码、设置或通话记录。
- 没有 `RECORD_AUDIO`：不录音，不在应用中采集或保存通话音频；本人通话由系统电话栈提供。
- 没有 `READ_CONTACTS`：不读取通讯录，不按联系人筛选。
- 没有无障碍服务或 `SYSTEM_ALERT_WINDOW`：不靠模拟点击、悬浮窗接听。
- 没有 `ANSWER_PHONE_CALLS`：当前实现通过默认电话角色下的 `Call.answer()` 管理通话。
- 没有 `WAKE_LOCK` 或开机接收器：自动接听不要求屏幕常亮；重启后须先解锁一次。

以上只描述本应用；运营商通信和系统电话服务仍会正常使用手机的电话与音频能力。
