# Android 原包直播播放器探针

一个实验性的 Android 宿主，用于验证能否从用户提供的官方 APK，直接加载其原始播放器 DEX 与 ARM64 `.so`，并记录初始化、播放和诊断回调。

**状态：已在 ARM64 Android 17 真机，用本地导入的 `40.7.0 / 400701` 原包完成 native 加载、播放器创建、H.264 和 H.265 的首音频帧/首视频帧及周期统计验证。** H.265 硬解和软解对照均已出帧。标准 `vvc1` enhanced-FLV 合成输入尚未出帧，不能声称 H.266 已跑通。`40.9.0 / 400901` 仅完成静态接口检查，未完成对应的宿主初始化适配。这不是抖音等价实现，也不能保证复现某个线上会话的动态配置。

仓库只包含原创探针源码、构建脚本和静态检查工具。原始 APK、库、DEX、反编译输出、测试流地址和运行日志均由使用者在本地提供，不随仓库或探针 APK 分发。

## 现在只有手机，也可以先试

1. 从本仓库 Releases 下载探针 APK 并安装。
2. 打开探针，选择“导入本机已安装的抖音”。这个操作复制已安装 APK 供独立加载，不修改已安装的应用。
3. 如果系统安装的是拆分 APK，当前版本会提示改用文件选择导入官方完整 APK。也可直接选择手机上下载的完整 APK 文件。
4. 先运行“验证原类和 native 加载”，退出探针进程后再运行“初始化原播放器”。这两步不需要直播 URL。
5. 可先点击“本机合成测试：H.264 / H.265”；素材在手机本机通过环回 HTTP 服务实时发送，不需要电脑推流。H.265 素材使用实验性的 FLV CodecID 12 封装，该结果不代表某个线上会话的封装。再用自有 HTTP-FLV 地址测试。退出每一轮请用“停止并关闭探针进程”。
6. 回到主页面查看或导出 `probe.jsonl`。如果 native 崩溃，文件中至少可能保留最后执行到的阶段；完整 native 崩溃日志需连接电脑采集。

需要 ARM64 Android 设备。已验证的宿主适配限于 40.7.0；换版本需重新验证，不能仅因接口同名视为可运行。探针使用到的原始播放器版本可能要求比宿主 `minSdk 23` 更高的系统版本。x86 模拟器不用于评估原生 ARM64 解码、硬解能力或卡顿表现。

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

构建前可生成原创图案和正弦音素材，要求 FFmpeg 包含 libx264、libx265；可选 VVC 控制素材还需 libvvenc。未生成的素材对应按钮会禁用，播放器导入和自有 URL 入口仍可用：

```powershell
python tools/generate_samples.py --ffmpeg C:/path/to/ffmpeg.exe --vvc
```

H.265 同时保留 enhanced `hvc1` 和 CodecID 12 两种本地文件，App 合成测试使用后者。VVC 文件采用 enhanced `vvc1`，仅用于封装兼容探查，不假设与实验流完全一致。相关扩展格式见 [Veovera 规范](https://github.com/veovera/enhanced-rtmp/blob/main/docs/enhanced/enhanced-rtmp-v2.md)。

首次构建需要联网获取 Gradle 和 Android 构建依赖。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 同样构建 APK 并保存为工作流 artifact。

`build.ps1` 是 Windows 便利入口，默认寻找 Android Studio JBR 和本机标准 Android SDK 路径；可传入 `-JavaHome`、`-AndroidSdk`，缓存完整时可加 `-Offline`。

## 实现范围

- 保留用户导入的完整原 APK，以 `DexClassLoader` 加载原始 DEX。
- 逐字节提取 `lib/arm64-v8a/*.so`，记录输入 APK 与各库 SHA-256。
- 默认目标是原包 `com.ss.videoarch.liveplayer2.VeLivePlayer`；另提供 Java `VideoLiveManager` 初始化对照入口。
- 使用原始类的静态初始化、native 加载器、构造函数和 URL 播放入口，没有集成公开直播 SDK，也没有重新编译反编译的播放器实现。
- 适配 Resources、Assets、ClassLoader 和 `nativeLibraryDir`；保留探针自身的包身份、权限及数据目录，不启动原始应用的 Application。
- 单独的 `:engine` 进程承载每轮尝试，避免不同 ClassLoader 重复加载同一 native 库。
- 40.7.0 适配器将探针自身 Application、版本和私有配置目录绑定到原宿主依赖；初始化原 Librarian 加载器。没有构造或启动原应用 Application，也没有修改导入的 DEX / so。
- 原创具体 Observer 方法与原包一起由同一 ClassLoader 加载，避免 Android 17 上 Java 动态代理回调触发的 JNI 返回类型检查失败。`observer-api` 只有编译用接口签名和空类型，不进入 App 或回调 DEX；实际接口和模型来自用户导入的 APK。

原始播放器可能依赖宿主 Application、全局单例、hook、动态模块或运行配置。遇到这些依赖时，探针记录失败，不会替换为另一个播放器后声称加载成功。

当前已验证入口使用 HTTP 80 / HTTPS 443。实测原内核会将缺少流配置的 HTTP URL 端口归一化为 80，因此本版拒绝非标准端口的自有 URL。手机合成服务只监听 `127.0.0.1:80`；端口被占用或系统不允许绑定时会报错。导入后独立进程运行，不要求官方客户端同时打开。

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
- 当前启用 native Debug 日志，用于定位调用流程；文件同步写入和日志 UI 本身也有开销，不能拿此模式直接做竞标性能结论。
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

可将最后一轮 JSONL 转为不含 URL、路径和设备标识的摘要：

```powershell
python tools/summarize_probe.py captures/path/probe.jsonl --output captures/path/summary.json
```

Debug 包也支持 ADB 阶段调用；不传测试 URL 时可使用本机素材：

```powershell
adb shell am start -W -S -f 0x10008000 -n local.originalplayer.probe/.MainActivity --es probe_action run --es probe_mode play --ei probe_engine 0 --es probe_smoke h265 --ez probe_hardware true
```

将 `probe_hardware` 改为 `false` 运行软解对照。`-S` 只重启探针自身；请勿在需要持续观察官方客户端时同时启动探针播放。

## Windows root 模拟器实验环境

已在 Windows WHPX 上验证独立 AVD `LivePlayerRoot35`：Android 15 / API 35，Google APIs x86_64 镜像（非 Play Store，revision 9）。该镜像实测 `ro.debuggable=1`、`adb root` 后 `uid=0`，ABI 包含 x86_64 与 arm64-v8a，Native Bridge 为 `libndk_translation.so`。本地导入的官方 40.7.0 可启动、加载 `libttmplayer.so`，匹配版本的 Frida 17.24.3 已成功附加 Java 进程。尚未由此完成真实直播会话的 URL/配置采集。

Google Play Store 镜像不能直接 `adb root`；AOSP 镜像虽然可 root，但不保证带 ARM 转译。镜像选择见 [Android 官方说明](https://developer.android.com/studio/run/managing-avds)。实际镜像的 root 和 ARM 兼容性仍须验证。

有 Android SDK Command-line Tools 时，可按以下命令创建同名 AVD。新版 Android CLI 使用斜杠形式的包路径：

```powershell
android --no-metrics sdk install system-images/android-35/google_apis/x86_64
avdmanager create avd -n LivePlayerRoot35 -k 'system-images;android-35;google_apis;x86_64' -d pixel_6
.\start-root-emulator.ps1 -FridaServer C:/path/to/frida-server-17.24.3-android-x86_64
```

本机也可双击 `start-root-emulator.cmd`。启动器只操作指定 AVD，验证 root 后启动本地提供的 Frida server；客户端和 server 须匹配版本。未提供 server 时只启动 root 环境。原始 APK、Frida 二进制和个人登录状态不随仓库分发。

模拟器适合入口参数和调用链探查。ARM 转译、虚拟 codec 和不同设备身份会影响行为与服务端下发；不能把模拟器的解码性能、硬解路径、功耗、卡顿或灰度配置当作原手机的测量结果。Frida 插桩本身也有开销。采集与重放仍需校验具体流和配置，而不是仅确认处在同一直播间。

## 验证记录

| 检查 | 当前状态 |
| --- | --- |
| Android 工程编译 | 已通过 |
| 40.9.0 的 16 项 DEX 接口和 3 项 JNI 静态检查 | 已通过 |
| 旧版不兼容样本负向检查 | 已验证为不通过 |
| 40.7.0 真机类初始化、native 加载 | 通过，原 native 版本 1.10.288.9-native |
| 本机 H.264 音视频出帧 | 通过，统计报告 hardware_codec / isHardwareDecode=true |
| 本机 H.265 允许硬解 | 通过，统计报告 h265 / hardware_codec / isHardwareDecode=true |
| 本机 H.265 关闭硬解 | 通过，统计报告 h265 / JX_ByteVC1_codec / isHardwareDecode=false |
| 标准 enhanced-FLV VVC 控制素材 | 未出帧，出现重试；未判定根因 |
| 客户实验流的 H.266、线上配置及卡顿等价性 | 尚未验证，需要真实测试流与配置证据 |

上述本机素材为 640×360、30 fps、图案与正弦音，测试目的是验证链路；不是与 H.266 的性能比较。统计字段中无直播时间基准的值（例如合成流的 firstLiveFrameTimeMs）不能拿来计算直播延迟。A/V buffer 差不能直接解释为呈现同步差。

## License

MIT，仅适用于本仓库原创代码。用户导入的第三方 APK 及其内容不属于本仓库许可证覆盖的内容。
