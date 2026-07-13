# GomokuDualScreen V4.5

双屏五子棋 Android 应用 — 主屏⚫黑方 + 副屏⚪白方，支持人机 AI 对战。

## 截图
（副屏左侧 🤖 按钮一键开启 AI 自动对战）

## 功能

- 🖥️ **双屏双视角** — 主屏(Display 0)黑方视角，副屏(Display 2)白方视角
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

双 Activity 方案：

| Activity | 物理屏 | 视角 | 说明 |
|----------|:------:|:----:|------|
| `MainActivity` | Display 0 | ⚫ 黑方 | 游戏控制中心，防呆机制强制主屏 |
| `GamePresentation` | Display 2 | ⚪ 白方 | 通过 `setLaunchDisplayId` 投递到副屏 |

## 核心文件

| 文件 | 行数 | 职责 |
|------|:---:|------|
| `GomokuGame.kt` | ~170 | 15×15 棋盘逻辑、落子、判胜、悔棋、暂停 |
| `GomokuView.kt` | ~620 | 自定义 View：3D 棋子 + 4 种粒子动画 |
| `MainActivity.kt` | ~330 | 主屏 Activity，倒计时，防呆，AI 调度 |
| `GamePresentation.kt` | ~330 | 副屏 Activity，按钮回调转发 |
| `GomokuAI.kt` | ~250 | AI 引擎：Alpha-Beta + 方向扫描 + 跳模式 |
| `SoundFX.kt` | ~80 | 落子音效 + TTS 语音（线程安全） |
| `GameState.kt` | 5 | 单例：`game` + `mainActivity` |

## 版本历史

| 版本 | 关键变更 |
|------|----------|
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
