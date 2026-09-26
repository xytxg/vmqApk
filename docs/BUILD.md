# 构建、安装与签名

## 环境与构建

- JDK 17；Android SDK Platform 35、Build Tools 35.0.0。
- Gradle Wrapper 8.11.1（已固定发行包 SHA-256）；AGP 8.9.2。
- Android 5.0/API 21 及以上；targetSdk 35。Android 4.4 已不支持。

```bash
# 首次使用先通过 Android Studio SDK Manager 或 sdkmanager 安装 SDK
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
# 设置 ANDROID_HOME，或在未提交的 local.properties 中填写 sdk.dir
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease
bash scripts/package.sh
```

Windows 使用 `gradlew.bat`，产物也可以直接从 `app/build/outputs/` 取得。

## 产物

| 文件 | 用途 |
| --- | --- |
| vmq-2.0.0-debug.apk | 已用构建环境的 debug 密钥签名，可安装测试；不可作为长期正式签名 |
| vmq-2.0.0-release-unsigned.apk | 优化压缩的正式 APK，必须先签名才能安装 |
| vmq-2.0.0-release-unsigned.aab | 未签名应用包；不能直接安装，分发前需签名及处理 |
| SHA256SUMS.txt | 校验上述文件的 SHA-256 |

GitHub → Actions → Android build → 成功运行 → Artifacts 下载 `vmq-android-提交SHA`。
每次 push 到 master/main/refactor 分支或 PR 均运行测试、Lint 和构建；也可手动 Run workflow。
Actions 产物保留 30 天。请及时下载。

## 正式 APK 签名

项目不包含发布私钥，也不会自动把 debug 签名冒充为正式签名。
必须使用**原应用同一签名证书**才能覆盖安装原版。没有原签名时，请先记录后台配置，卸载旧版再安装；卸载会清除配置。
CI 的 debug 密钥不保证跨次构建相同，调试包也可能无法覆盖旧包。

```bash
# 仅首次生成自己的新签名时执行；妥善备份私钥，不要提交 Git
keytool -genkeypair -keystore vmq-release.jks -alias vmq -keyalg RSA -keysize 3072 -validity 10000
# 以下工具位于 $ANDROID_HOME/build-tools/35.0.0/
zipalign -p -f 4 dist/vmq-2.0.0-release-unsigned.apk vmq-aligned.apk
apksigner sign --ks vmq-release.jks --ks-key-alias vmq --out vmq-release.apk vmq-aligned.apk
apksigner verify --verbose --print-certs vmq-release.apk
adb install vmq-release.apk
```

密码通过交互输入，勿写入命令行或仓库。AAB 使用 `jarsigner` 签名，不使用 `apksigner`。

## 使用

1. 部署 Java 或 PHP 版 V免签服务端，设置通讯密钥，打开监控端配置页。
2. 扫码、从图片导入，或输入 `域名:端口/密钥`；也支持 `https://域名/子目录/密钥`。
3. 确认服务器地址，验证成功后保存。旧无协议配置仍使用 HTTP，以兼容旧部署；建议显式使用 HTTPS。
4. 开启系统的“通知使用权”。Android 13+ 的发送通知权限仅用于本应用测试通知，和通知使用权是两项独立权限。
5. 检测心跳，发送监听测试，确认最近运行状态显示“监听正常”。若只显示等待回执，检查通知渠道和通知使用权。
6. 允许微信/支付宝发送收款通知；在厂商设置中允许自启动和后台运行。请用真实小额交易验证通知模板和服务端回调。

## 限制与排查

- 不持有永久唤醒锁；系统休眠、强制停止、省电模式、厂商后台限制可能延迟或停止心跳。不能保证 24 小时实时到达。
- 金额解析只接受受支持收款动词后明确的“金额+元”，拒绝退款、汇总、多个金额等歧义内容。通知文案变化可能漏报，需要脱敏样例扩展测试。
- 支持普通文本和长文本通知，忽略组摘要；不补发历史通知，避免重连造成旧订单重复上报。
- 去重标识为通知 key、发布时间和内容的哈希，保存 24 小时、最多约 2000 条；不按金额去重，避免吞掉同金额的不同付款。系统重新生成不同通知标识的重复推送仍不能保证去重。
- 旧接口没有可靠幂等键，网络中断时无法判断服务端是否已入账，因此关闭自动重试和重定向。出现失败请先核对服务端订单，不要盲目重放。
- 密钥保存在应用私有 SharedPreferences；关闭系统备份，界面遮蔽密钥，不记录通知原文/签名 URL。不宣称能防御 root 或已失陷设备。
- 本项目保留旧 MD5 协议供现有服务端使用；它不是现代双向认证协议。HTTPS 使用系统正常证书校验，不忽略证书错误。
- UI/系统兼容尚需在实际机型确认，自动构建不代表实机收款联调成功。
