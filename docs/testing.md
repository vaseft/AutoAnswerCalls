# 测试记录与复现

记录日期：2026-11-13。以下为模拟器与本地构建验证，尚未在中兴 / 努比亚 Z50 真机或真实运营商线路上验证。

## 结果

| 场景 | Android 13 / API 33 | API 37 |
| --- | --- | --- |
| 亮屏来电 | 自动接通 | 自动接通 |
| 关屏来电 | 自动接通，屏幕保持关闭 | 自动接通，屏幕保持关闭 |
| 应用进程被回收，关屏 | 系统重新启动服务并自动接通 | 系统重新启动服务并自动接通 |
| 深度休眠，关屏 | 自动接通，屏幕保持关闭 | 自动接通，屏幕保持关闭 |
| 设置 PIN 后锁屏、关屏 | 自动接通，屏幕保持关闭 | 自动接通，屏幕保持关闭 |
| 关闭自动接听 | 保持振铃，未请求自动接听 | 保持振铃，未请求自动接听 |
| 重新开启，短号码来电 | 自动接通，屏幕保持关闭 | 自动接通，屏幕保持关闭 |

原始模拟器记录：[API 33](test-results/api33.json)、[API 37](test-results/api37.json)。`observedWithinMs` 含模拟器注入与轮询开销，不是应用人为添加的等待，也不代表真机性能承诺。

另完成：4 项策略单元测试（0 失败）、Android Lint（0 错误）、APK 签名验证。已在模拟器验证从拨号界面发起电话，以及通话中的静音和界面挂断。

隐藏号码的行为来自接听条件不查询号码这一实现事实，本轮未另造隐藏号码的模拟器来电。双卡选择、蓝牙、来电等待与真实音频质量尚需相应设备和线路验证。

## 构建验证

JDK 17、Android SDK 36 / Build Tools 35.0.0 环境下：

```powershell
.\tools\build.ps1
```

脚本执行应用 APK、测试 APK、策略单元测试及 Lint 构建。默认构建目录在临时目录中；可通过 `-BuildRoot` 指定。普通 Gradle 构建的测试 APK 在 `app/build/outputs/apk/androidTest/debug/`。

## 模拟器设备测试

只对专用测试模拟器执行。测试脚本会创建模拟来电、临时设置 PIN、调整休眠状态和自动接听开关，不会拨打真实电话。先安装应用和测试 APK，再授予模拟器上的默认电话角色及通知权限：

```powershell
# 将序列号和 APK 路径改为实际值。
adb -s emulator-5582 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5582 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5582 shell cmd role add-role-holder --user 0 android.app.role.DIALER cn.lightanswer.app
adb -s emulator-5582 shell pm grant cn.lightanswer.app android.permission.POST_NOTIFICATIONS
New-Item -ItemType Directory -Force output
.\tools\verify-emulator.ps1 -Serial emulator-5582 -ReportPath output/device-tests.json
```

`SmokeTestRunner` 仅在测试 APK 中，用于配置测试开关，不属于日常安装的应用 APK。脚本会在各项来电结束、Telecom 解绑后再执行下一场景，并在要求关屏的场景前等待实际进入休眠状态。

## Z50 真机确认

安装并完成 [权限与后台设置](permissions.md) 后，分别测试亮屏、关屏、PIN 锁屏以及返回桌面后的来电。重启后先解锁再测。若未接通，首先确认默认电话身份、自动接听开关、厂商后台限制以及来电是否被系统提前拦截。
