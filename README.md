# Android 原包直播播放器探针

一个实验性的 Android 宿主，用于验证能否从用户提供的官方 APK，直接加载其原始播放器 DEX 与 ARM64 `.so`，并记录初始化、播放和诊断回调。

**状态：构建和静态接口检查已完成，尚未在 ARM64 真机上验证 native 加载或直播播放。** 当前检查过的输入样本是 Android 抖音 `40.9.0 / 400901`。这不是抖音等价实现，也不能保证复现某个线上会话的动态配置。

仓库只包含原创探针源码、构建脚本和静态检查工具。原始 APK、库、DEX、反编译输出、测试流地址和运行日志均由使用者在本地提供，不随仓库或探针 APK 分发。

## 现在只有手机，也可以先试

1. 从本仓库 Releases 下载探针 APK 并安装。
2. 打开探针，选择“导入本机已安装的抖音”。这个操作复制已安装 APK 供独立加载，不修改已安装的应用。
3. 如果系统安装的是拆分 APK，当前版本会提示改用文件选择导入官方完整 APK。也可直接选择手机上下载的完整 APK 文件。
4. 先运行“验证原类和 native 加载”，退出探针进程后再运行“初始化原播放器”。这两步不需要直播 URL。
5. 有自有 HTTP-FLV 地址时，再尝试播放。退出每一轮请用“停止并关闭探针进程”。
6. 回到主页面查看或导出 `probe.jsonl`。如果 native 崩溃，文件中至少可能保留最后执行到的阶段；完整 native 崩溃日志需连接电脑采集。

需要 ARM64 Android 设备。探针使用到的原始播放器版本可能要求比宿主 `minSdk 23` 更高的系统版本。x86 模拟器不用于评估原生 ARM64 解码、硬解能力或卡顿表现。

## 在电脑上先验证输入 APK

要求 Python 3.11 或更新版本，无第三方 Python 依赖。

```powershell
python tools/preflight.py C:/path/to/official.apk --output analysis-output/preflight.json
```

工具检查探针调用的原类、构造函数、方法签名以及 ARM64 ELF 中的 JNI 注册表候选。退出码 `0` 表示这些静态接口检查通过，`1` 表示接口缺失或不兼容。它不会执行 DEX、`JNI_OnLoad`、解码或拉流。

需要更完整的媒体类、库依赖及哈希清单时：

```powershell
python tools/inspect_apk.py C:/path/to/official.apk analysis-output/inventory
```

输出可能含从用户 APK 提取的库和字符串，应留在本地；`analysis-output/` 已加入 `.gitignore`。

## 构建

使用 Android SDK Platform 35、Build Tools 35.0.0、JDK 17 或更新版本。Gradle Wrapper 固定为 8.13，Android Gradle Plugin 固定为 8.12.2。

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-17-or-21'
$env:ANDROID_HOME = 'C:/path/to/Android/Sdk'
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Linux/macOS：

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

首次构建需要联网获取 Gradle 和 Android 构建依赖。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 同样构建 APK 并保存为工作流 artifact。

`build.ps1` 是 Windows 便利入口，默认寻找 Android Studio JBR 和本机标准 Android SDK 路径；可传入 `-JavaHome`、`-AndroidSdk`，缓存完整时可加 `-Offline`。

## 实现范围

- 保留用户导入的完整原 APK，以 `DexClassLoader` 加载原始 DEX。
- 逐字节提取 `lib/arm64-v8a/*.so`，记录输入 APK 与各库 SHA-256。
- 默认目标是原包 `com.ss.videoarch.liveplayer2.VeLivePlayer`；另提供 Java `VideoLiveManager` 初始化对照入口。
- 使用原始类的静态初始化、native 加载器、构造函数和 URL 播放入口，没有集成公开直播 SDK，也没有重新编译反编译的播放器实现。
- 适配 Resources、Assets、ClassLoader 和 `nativeLibraryDir`；保留探针自身的包身份、权限及数据目录，不启动原始应用的 Application。
- 单独的 `:engine` 进程承载每轮尝试，避免不同 ClassLoader 重复加载同一 native 库。

原始播放器可能依赖宿主 Application、全局单例、hook、动态模块或运行配置。遇到这些依赖时，探针记录失败，不会替换为另一个播放器后声称加载成功。

## H.265 硬解对照

UI 的“允许硬解”设置原配置 `enableHardwareDecode=true`，它不是“强制且只能硬解”。原内核仍可能回退软解。另一个选项设置为 `false`，仅用于 Native 直播内核的软解对照。

请以原始 `onStatistics` 回调中的 codec、软硬解标记及原内核日志确认实际路径。Android 使用 MediaCodec 也不自动等于硬解；API 29 起可结合 `MediaCodecInfo.isHardwareAccelerated()` 和 `isSoftwareOnly()` 判断系统 codec 属性。

- [火山直播：H.265 硬解优先、失败回退及统计标记](https://docs.volcengine.com/docs/video-live/player-sdk-for-android-advanced-features?lang=zh)
- [Android：codec 的硬件/软件属性](https://developer.android.com/media/optimize/performance/codec)

比较两路流时，建议先后播放，保持同一设备、相同网络和相近温度。分别保存日志，确认 H.265 实际硬解、H.266 实际使用的解码路径，再讨论性能差异。

## 日志与测量限制

- 记录单调时钟、阶段、Java 异常和原始回调对象的基础字段；保留 JSON 数值类型。
- 原始 `onMonitorLog`、`onStatistics`、首帧及卡顿事件由原包产生。
- 诊断模式将统计回调设为 1 秒，并显式应用 UI 的硬解选项；这不是线上配置的完整重建。
- 未启用逐帧像素拷贝，也没有对 SDK 缺失字段补 `0`。
- 1 秒周期样本不能重建所有 200 ms 尖峰；buffer 时长差也不等于实际音画呈现差。
- 点击播放、方法返回或 native 版本查询成功，均不等于 H.266 已正常解码。
- 原始库可能在回调或 native 日志里输出完整签名 URL。日志仅保存在本地，分享前应检查其中的地址和标识。

## 连接电脑后采集

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
.\capture.ps1 -Seconds 60
```

脚本只请求探针 UID 的 logcat，不清空设备日志；也保存探针 JSONL、设备 ABI 和本机抖音版本。多设备时通过 `-Serial` 指定设备。

## 验证记录

| 检查 | 当前状态 |
| --- | --- |
| Android 工程编译 | 已通过 |
| 40.9.0 的 16 项 DEX 接口和 3 项 JNI 静态检查 | 已通过 |
| 旧版不兼容样本负向检查 | 已验证为不通过 |
| 真机类初始化、native 加载 | 尚未验证 |
| 原包 H.265/H.266 播放、硬解及卡顿对照 | 尚未验证 |

## License

MIT，仅适用于本仓库原创代码。用户导入的第三方 APK 及其内容不属于本仓库许可证覆盖的内容。
