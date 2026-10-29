# Android 端侧遥测与统计口径

本目录是对本地原包的独立调查说明和原创参考实现。只提交字段名、方法签名、哈希及解释，不提交原 APK、so、DEX、反编译源码、会话配置、流地址、用户/房间标识或客户实验数据。

## 范围与证据

固定样本为 `40.7.0 / 400701`，APK SHA-256 为 `86ee431640c9fe5035135092079af2dcddf947feb7e02cb78aaf459c821f80c0`。已运行的原内核版本为 `1.10.288.9-native`。不能将这些结论自动用于其他版本、iOS 或某个 H.266 灰度。

- `evidence-40.7.0.json`：13 个相关类的声明、指定方法的签名和指令区 SHA-256，以及三个原 native 库的 SHA-256。无方法指令或实现源码。
- `observed-fields-40.7.0.json`：本轮实际出现的字段名和类型；无字段值、地址、标识或客户实验数据。
- `catalog-40.7.0.json`：按指标区分端侧已证实内容、分支限制及后台未知项。
- `../tools/telemetry_metrics.py`：原创参考计算，可执行但不替代原播放器或后台。
- 静态方法说明经过本地 JADX 核查；指令哈希用于定位同一份代码，不单独证明计算语义。超大方法存在反编译失败/重复展开，相关逻辑只在能核查的小方法中表述。
- 运行时记录保存在被忽略的 `captures/`。SDK 对象字段、原 observer、业务客户端入口、app-info 注入及业务上报入口分别标层，不合并成单一口径。

重新生成元数据证据：

```powershell
python tools/inspect_telemetry.py C:/path/to/original.apk --output analysis-output/telemetry/evidence.json
```

原包哈希不符或目标类缺失会返回非零退出码。字段同名、签名相同甚至部分指令相同，都不能保证线上配置或后台口径一致。

## 当前能拿到什么

| 数据层 | 入口／输出 | 状态与限制 |
| --- | --- | --- |
| 原播放器周期统计 | `VeLivePlayerStatistics`，41 个声明字段 | 原探针已经记录全部可读公开基础字段；回调是否开启由配置控制 |
| 原内核事件 JSON | 原 observer 的 `onMonitorLog` | 已观察首帧、起播、会话、卡顿、重试及播放错误等；当前 Java 采集器不强行开启统计 |
| 业务接收入口 | `LivePlayerClient.e(JSONObject,String)` | 保留单独的层；常与原 observer 重复，不能重复计数 |
| app-info 注入 | `LivePlayerAdapter$setAppInfoFetcher$appInfoFetcher$2.getAppInfo()` | 可观察 `live_base_new` 等实际返回字段；分支关闭时不能补出 |
| UI／业务统计上报 | 帧卡顿 composer 与两个版本绑定的 analytics 入口 | 按技术事件名前缀过滤；挂钩成功不等于某事件本轮已发生 |
| 电池温度来源 | 原电池广播 receiver | 可观察原广播输入；独立宿主另记录带来源标签的 OS 参考采样 |

本轮从原客户端观察到 452 个不同的原内核事件字段、91 个 app-info 字段和 201 个业务报告字段；这些是该会话的字段并集，既不是每条事件都齐全，也不是整个客户端的全部遥测。15 个 Java 挂钩入口均安装成功，但部分入口没有本轮事件，不能据此推定其运行时覆盖。

## 已核查的端侧计算

### Android 温度

`X.1dpf` 注册 `ACTION_BATTERY_CHANGED`，`X.1dAZ.LIZ(Intent)` 将 `temperature` 整数除以 `10.0f`，存入缓存。`LivePerformanceManager` 将其复制到性能缓存，`X.1dj9.run()` 的一条上报路径及 `X.1dzu.LIZ(...)` 温度 composer 写入 `temperature_android`。本轮也在真实 `livesdk_performance_monitor_base_new` 上报中拿到了这个字段。

这条路径测量电池温度，不是 CPU、GPU 或机壳温度。独立宿主每 5 秒读取同一系统广播作为 `probe_os_reference`，同时在 API 29+ 记录 `thermal_status`。后者是系统热状态等级，不是摄氏度，不能与电池温度混用。模拟器传感器值不用于真机热性能结论。

原 receiver 对缺失温度使用 0 默认值；原创参考工具保留缺失为 null，避免将缺测当作 0℃。没有从截图反推出 iOS 温度含义，也未证明后台“温度均值”的采样频率和加权方式。

系统接口说明：[BatteryManager](https://developer.android.com/reference/android/os/BatteryManager#EXTRA_TEMPERATURE)、[PowerManager 热状态](https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus())。

### UI 卡顿直方图

`X.1dj7.LJIIIIZZ(double,JSONObject)` 按直方图桶 `k` 及次数 `n[k]` 换算时间，帧周期为 `1000 / refresh_hz` ms；刷新率无效时原路径使用 `16.666668` ms。

- `1 ≤ k < 3`：minor；`3 ≤ k < 7`：slight；`7 ≤ k < 14`：moderate；`k ≥ 14`：serious。
- 每桶时长为 `k × n[k] × frame_period_ms`。
- 该缓存的 UI 卡顿总次数、总时长只合计 slight / moderate / serious；另存包含 minor 的总时长。
- `LJII(window_ms)` 计算 `UI duration / window_ms`、`video duration / window_ms` 及两者时长相加后的比率。它没有求 UI/视频卡顿时间区间的并集，不能据此认为组合比率一定 ≤ 1。
- `X.1djI.LIZ(...)` 输出 `ui_stall_duration`、`ui_total_stall_rate`、`video_total_stall_rate` 等字段。

这是一个已确认的 Java 缓存/上报分支。截图中的 UI 卡顿率是否恰好使用它、是否使用新 `stall_ui_rate_new`，仍需客户字段映射或本会话实际 app-info 字段确认。

本轮业务报告实际出现了 `stall_ui_duration`、`stall_monitor_duration`、`stall_ui_rate` 和 `stall_ui_rate_new`；单个记录中两种 rate 均与 duration / monitor-duration 一致。这个样本一致性不证明新旧算法在所有分支等同，也不证明后台截图直接使用其中一个字段。

### 首帧

当前 native observer 将 `firstFrameTimeMs` 映射到 `first_frame_time:`。运行时 `first_frame` 事件还提供 `total_cost`、`start`、`first_frame_render_end`、DNS、连接、首包及解码阶段时间。

同包的旧 Java logger `X.0BL2.LJJIIJZLJL(...)` 明确以首视频帧 render-end 时间减起播时间生成 `total_cost`；另有 `session_first_frame` 口径。它与当前 native 路径是不同实现分支，不能用这个 Java 公式冒充 native 的完整实现。重试、预播、命中缓存、进房时点和 first-live-frame 均可能影响业务选择哪个字段。

### SEI 延迟

同包 Java logger 的 `X.0BL2.LLII(JSONObject)` 在具备 `ts` 时计算：

`sei_delay_ms = System.currentTimeMillis() - sei.ts + active_clock_offset_ms`

它对 `source`、`tt_ntp` 和设置分支进行检查，时钟偏移可能来自 `time_diff_server_and_client`；另读 `stream_delay`，不是把该值加到上述公式。参考函数保留负值，供定位时钟异常。此公式只证明该 Java 分支，不能证明 native 所有 SEI 算法完全相同。

当前原内核实际还输出 `live_sei_delay` 数组、`live_sei_delay_samples`、`live_sei_delay_error_code`、`live_sei_delay_last_ntp_diff_ms` 等。已经观察过样本数 -1、错误码 -1 且数组 `[0,0,0]` 的无效记录；不得将其当作 0 延迟参与平均。数组各项的精确定义和 native 计算过程尚未恢复，不按数组位置擅自命名平均值或分位数。

### 播放卡顿与“百秒卡顿时长”

原报告区分 `stall_time`、`video_render_stall_time`、`audio_render_stall_time`，还包括下载、解复用、解码、同步及 buffer-control 的阶段卡顿字段。多阶段可能重叠，不能相加当作用户感知卡顿。

旧 Java logger 的渲染卡顿记录方法 `X.0BL2.LJLLI(int,long,boolean,boolean)` 对传入时长做 `duration > threshold` 且 `duration ≤ now - start` 检查，再按音视频、预览等分支分别计数和累积。阈值是参数，不能仅凭方法名推断线上数值；native 路径仍需其实际配置。`stall_counter` 事件另有按 200/300/400/500 命名的桶，不能与“百秒”混淆。

**“百秒卡顿时长”通常指按 100 秒观看时长归一，不是 100 ms 卡顿阈值。** 仓库仅提供一个明确标为参考的计算：`100 × stall_ms / watch_ms`，单位为秒／100 秒。尚未证明客户报表选择哪个卡顿分子、观看分母、预览范围、阈值、重试和后台过滤。

周期 `playing` 的 `play_time` 已观察到约 20 秒一段，而 `total_play_time` 随会话增长。不能重复相加累计计数器，也不能将周期事件、session_stop 和 play_stop 全部混加。多会话归一应先对口径一致且不重叠的分子分母求和，再计算比率；不要直接平均各会话比率。

## 采集及提取

要求当前原包版本匹配、可附加的 root 开发设备/模拟器，以及与 server 匹配的 Frida Python 环境。使用先前的 `capture_liveplayer.py` 配置采集不等于本处的业务遥测采集。

```powershell
adb -s emulator-5556 shell pidof com.ss.android.ugc.aweme
python tools/capture_telemetry.py --device emulator-5556 --pid 12345 --seconds 90 --output captures/telemetry-private.jsonl
python tools/extract_telemetry.py captures/telemetry-private.jsonl --output captures/telemetry-projection.json
```

PID 使用当前主进程值。开始后切到直播或重新打开直播间，覆盖首帧/起播；持续观看覆盖周期事件，正常退出可观察停止事件。该 Java 采集器不替换 JNI 方法、不更改播放配置、不强制启用统计，此入口拒绝 `--statistics`。

采集记录 `telemetry_hook` 的安装情况以及 `telemetry_capture_error`。某入口未触发时不会补 0。工具保存完整原始 JSON 到本地，包括可能含有地址和标识的字段；它不抓聊天、账号凭据、其他 App 或任意全局网络请求。所有文件仍需分享前检查。

本次实时采集的 Frida 卸载曾在模拟器中卡住，停止原客户端进程后才退出；已增加 5 秒卸载超时保护。发生该情况时保留本地记录、写入 `capture_cleanup_timeout` 并以 3 退出，需停止目标进程清理后再采集。挂钩安装成功、成功拿到数据及干净卸载是三件不同的事；这轮受插桩影响的卡顿数据不用于线上性能归因。

提取器生成字段名/类型清单及数值投影，保留原层、原行号和事件窗口，不自动合并重复事件、不把队列 buffer 差当作呈现同步差。原始参数有类型差异、缺测、-1 哨兵及多个会话时均保留这些差异。字段名/类型清单不含原字段值。

独立 App 的原回调 JSONL 也可直接提取。新宿主增加的 `device_telemetry` 为 OS 参考采样，不能冒充原业务层字段：

```powershell
python tools/extract_telemetry.py captures/path/probe.jsonl --output captures/path/telemetry-projection.json
python -m unittest discover -s tests -v
```

新版宿主回归使用原创 H.265 图案/音频素材，已获得音视频首帧、10 个周期统计和 3 个 OS 参考样本。该模拟器拒绝普通 App 绑定本机端口 80，因此这次通过临时 root ADB reverse 将设备端口 80 转到电脑环回 HTTP 服务完成验证；验证后已移除转发。这验证宿主采样与播放并存，不是新直播的协议等价复播。

此前保存的签名会话及本轮报告里的 CDN 备用地址，在这轮独立播放尝试中均未获得出帧统计；原因未定位，不据此宣称签名失效或解码不支持。原客户端本轮实际 LLS / UDP-SDP 分支的独立等价复播尚未完成。

## 还不能恢复的部分

- 只能观察当前进程、当前版本、当前采集窗口实际执行的分支，不能宣称拿到了所有端侧遥测。
- 当前 native 内部计时与 SEI 聚合细节没有完整恢复；公开声明或字符串命中不等于算法已恢复。
- 不含 iOS 二进制，无法从 Android 包还原 iOS 温度或 iOS 播放统计。
- 客户截图的降采样、CUPED、实验分桶、显著性、多重比较校正及后台 SQL/清洗不在当前 APK 的可验证范围。不能从相对增幅和均值唯一反推它们。
- 没有真实 H.266 灰度流及客户端/服务端口径映射，本仓库不会将已有 H.265 普通直播采样标为 H.266 问题复现。

本轮新直播还报告了 `play_format=lls`、`play_protocol=udpsdp`，原始 request URL 的 scheme 为 `webrtc`；这与先前验证的 LLASH / HTTP 是不同播放分支。同包可以走多种路径，不能把所有直播都当作 HTTP-FLV，也不能把报告里的 CDN 备用地址当作原会话实际采用的协议。
