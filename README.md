# GomokuDualScreen V4.6

双屏五子棋 Android 应用 — 物理面板 A⚫黑方 + 物理面板 B⚪白方，支持人机 AI 对战。

双屏适配规则见 [docs/dual-screen-app-guide.md](docs/dual-screen-app-guide.md)：玩家绑定物理面板，
方向抵消显示投影，退出两侧联动。

## 截图
（副屏左侧 🤖 按钮一键开启 AI 自动对战）

## 功能

- 🖥️ **双屏双视角** — 物理面板 A 固定黑方、物理面板 B 固定白方；按 `persist.vendor.display.master` 自动判定谁在哪块屏，不依赖 `displayId` 写死
- 🤖 **AI 自动对战** — Alpha-Beta 迭代加深，方向扫描评估 + 跳模式检测
- 🎨 **3D 立体棋子** — 光影渲染 + 4 种粒子动画（胜利/失败/臭鸡蛋/鲜花）
- 🔊 **TTS 语音播报** — 标准/东北话双音色，30+ 场景语音
- ⏱️ **60 秒倒计时** — 超时催促 + 震动
- ⏸️ **暂停系统** — 每方 1 次暂停
- 😈 **挑衅系统** — 消耗挑衅次数触发臭鸡蛋动画
- ↩️ **悔棋 + 重新开始** — 需对方确认
- ⚙️ **设置持久化** — 音效开关、音色切换、棋子顺序编号

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 1.9.20 |
| Gradle | 8.5 |
| compileSdk / minSdk | 34 / 31 |
| JDK | 17 |
| TTS | Microsoft Edge TTS |

## 构建

```powershell
$env:JAVA_HOME='D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7'
D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7\bin\java.exe `
  -classpath 'gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain assembleRelease --no-daemon `
  -p D:\work\ai_code\GomokuDualScreen
```

APK 输出：`app\build\outputs\apk\release\app-release.apk`

## 安装

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

## 架构

双 Activity + 双 task：

| Activity | 玩家 | 参与 | 说明 |
|----------|:----:|------|------|
| `MainActivity` | ⚫ 黑方 | 物理面板 A | 游戏控制中心；落在哪块 `displayId` 由 `master` 算出 |
| `GamePresentation` | ⚪ 白方 | 物理面板 B | 独立 task（`taskAffinity` 加 `.white`），通过 `launchDisplayId` 投递 |

双屏会话、过渡屏障、退出联动由 `BattleDisplays`（在 `BattleDisplayActivity.kt` 内）统一管理。

## 核心文件

| 文件 | 职责 |
|------|------|
| `PanelMap.kt` | 玩家→displayId 映射、方向（显示投影）换算 |
| `BattleDisplayActivity.kt` | 双屏基类 + `BattleDisplays` 会话管理、绑定、遮罩、退出 |
| `ExitPolicy.kt` | 一侧退出时另一侧是否跟着退 |
| `GomokuGame.kt` | 15×15 棋盘逻辑、落子、判胜、悔棋、暂停 |
| `GomokuView.kt` | 自定义 View：3D 棋子 + 粒子动画 |
| `MainActivity.kt` | 黑方 Activity、倒计时、AI 调度 |
| `GamePresentation.kt` | 白方 Activity、按钮回调转发 |
| `GomokuAI.kt` | AI 引擎：Alpha-Beta + 方向扫描 + 跳模式 |
| `GameState.kt` | 会话单例 + `AtomicFile` 持久化 |

## 版本历史

| 版本 | 关键变更 |
|------|----------|
| V4.6 | 玩家绑定物理面板（按 `master` 自动映射）；方向抵消显示投影；退出两侧联动；会话持久化 |
| V4.5 | 🤖 AI 自动对战 + 棋子顺序双屏同步 + 评估函数修复 |
| V4.4 | 白方挑衅次数同步修复 |
| V4.3 | 棋子缩小 10% + 挑衅次数初始显示 |
| V4.2 | 副屏退出通知主屏 + 挑衅次数逻辑修正 |
| V4.1 | 返回键双屏同步 + 动画增强至 490 粒子 |
| V4.0 | 主屏防呆机制 + Home 键双屏退出 |
| V3.9.2 | 双 Activity 架构替代 Presentation |
| V1.0–V3.8 | 单屏原型 → Presentation 双屏 → 功能完善 |

详见 [`cl.md`](cl.md)

## License

MIT
