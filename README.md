# V免签 Android 监控端 · 2.0

基于原 V免签 v1.8.1 的重构版本。监听微信/支付宝收款通知，将识别结果发送给自己的 V免签服务端。

## 本次更新

- 模块化配置、签名、网络和收款解析；支持 HTTPS、IPv6 和子目录部署。
- 修复非法配置、误读金额、线程/响应资源泄漏，加入通知去重与可读错误状态。
- AndroidX、浅色/深色界面、扫码和系统图片导入、Android 13+ 测试通知权限。
- Android 5.0+，target SDK 35；JDK 17 / Gradle 8.11.1 / AGP 8.9.2。
- GitHub Actions 自动测试、Lint、构建 APK/AAB 和生成 SHA-256。

## 下载与安装

到 [Actions](../../actions/workflows/android.yml) 打开成功运行，在 Artifacts 下载 `vmq-android-提交SHA`。
`debug.apk` 可安装测试；`release-unsigned.apk` 必须使用自己的证书签名，AAB 不能直接安装。
**没有原版签名证书，不能承诺覆盖安装旧版。** 请先保留配置，参阅签名说明。

[完整构建、签名和使用说明](docs/BUILD.md) · [重构记录](docs/REFACTOR_REPORT.md) · [原项目说明与历史](docs/README_LEGACY.md)

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease
bash scripts/package.sh
```

## 配置与使用

输入 `域名:端口/密钥` 或 `https://域名/路径/密钥`，也可扫描后台二维码。连接验证成功后保存配置，开启系统通知使用权，检测心跳与监听，然后用实机交易核对回调。

系统休眠、厂商省电策略和收款通知文案变化可能影响到账通知。网络失败不自动重发，避免旧服务端重复入账。限制和排查方法见构建说明。

服务端：[Java](https://github.com/szvone/Vmq) · [PHP](https://github.com/szvone/vmqphp)

## 许可

MIT License，保留原作者 vone 的版权与许可，见 [LICENSE](LICENSE)。
