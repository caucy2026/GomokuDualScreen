# GomokuDualScreen — AI 编程助手指南

> 双屏五子棋 Android 应用。历经 V3.9.2 → V4.4 共 7 个版本迭代，积累了丰富的经验教训。

---

## 构建与安装

```powershell
$env:JAVA_HOME='D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7'
D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7\bin\java.exe `
  -classpath 'D:\work\ai_code\GomokuDualScreen\gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain assembleRelease --no-daemon `
  -p D:\work\ai_code\GomokuDualScreen

adb install -r D:\work\ai_code\GomokuDualScreen\app\build\outputs\apk\release\app-release.apk
```

| 环境 | 路径 |
|------|------|
| JDK 17 | `D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7` |
| Android SDK | `D:\work\ai_code\tools\android-sdk` (api 34) |
| Gradle | 8.5 / Kotlin 1.9.20 / compileSdk 34 / minSdk 31 |
| Python | `D:/Python312/python.exe` |
| Keystore | `D:\qianming\debug.keystore` (alias=`androiddebugkey`, pwd=`880203`) |

---

## 架构

### 双 Activity 方案

弃用 Android `Presentation` API（存在跨屏显示限制），改用两个独立 Activity：

| Activity | 物理屏 | 视角 | 启动方式 |
|----------|:------:|:----:|----------|
| `MainActivity` | Display 0（强制） | ⚫ 黑方 | 用户点击图标 |
| `GamePresentation` | Display 2 | ⚪ 白方 | `startActivity` + 反射 `setLaunchDisplayId` |

### 核心文件

| 文件 | 职责 |
|------|------|
| `GomokuGame.kt` | 15×15 棋盘，落子/判胜/悔棋，暂停状态机 |
| `GomokuView.kt` | 自定义 View：3D 棋子 + 粒子动画（胜/负/蛋/花） |
| `MainActivity.kt` | 主屏 Activity，游戏控制中心，倒计时，防呆逻辑 |
| `GamePresentation.kt` | 副屏 Activity，按钮回调转发到 MainActivity |
| `SoundFX.kt` | 落子音效 + TTS 语音播放（线程安全，每次 new AudioTrack） |
| `GameState.kt` | 单例：`game: GomokuGame` + `mainActivity: MainActivity?` |

---

## 主屏防呆机制 ⭐ 最关键的设计

**问题**：用户从副屏(Display 2)点击图标 → Android 把 MainActivity 分配到副屏 → 主屏空白。

**方案**（V4.0 引入）：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 极早期检测，在 setContentView 之前
    @Suppress("DEPRECATION")
    val launchedDisplayId = windowManager.defaultDisplay.displayId
    if (launchedDisplayId != Display.DEFAULT_DISPLAY) {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = Display.DEFAULT_DISPLAY
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            options.toBundle()
        )
        finish()
        return  // 关键！跳过后续 lateinit 初始化
    }
    // 以下仅在主屏执行
    myPlayer = GomokuGame.PLAYER_BLACK  // 始终黑方
    ...
}
```

**要点**：
- 必须在 `setContentView` 之前拦截，否则 lateinit 崩溃
- `finish()` 后必须 `return`，不能继续执行
- 必须用 `FLAG_ACTIVITY_CLEAR_TASK` 清空错误任务栈
- 由于此机制，**MainActivity 永远在主屏**，`myPlayer` 固定为 `PLAYER_BLACK`

---

## 视角分配规则

| 规则 | 说明 |
|------|------|
| MainActivity = 黑方 | 防呆保证在主屏 → `myPlayer = PLAYER_BLACK` |
| GP = 白方 | `launchWhiteScreen()` 中显式设置 `sharedPerspective = PLAYER_WHITE` |
| ⚠️ 不要让 GP 自检 | `display?.displayId` 在 `onCreate` 中不可靠（Activity 未完全挂载） |

---

## 版本演进历史

| 版本 | 关键变更 | 经验教训 |
|------|----------|----------|
| V3.9.2 | 双 Activity 方案替代 Presentation | Presentation 跨屏有 Android 限制 |
| V3.9.3 | GP 自检 displayId 决定视角 | **失败**：`onCreate` 时 `display?.displayId` 不可靠 |
| V4.0 | 主屏防呆 + 显式视角分配 + Home 键 `finishAffinity()` | 防呆是本项目最重要的设计决策 |
| V4.1 | 返回键 `onBackPressed()` + 动画延长到 5s 多阶段粒子 | `finishAffinity()` 跨屏可能失效（不同任务栈） |
| V4.2 | 挑衅次数不被重启重置 + GP 退出通知 MainActivity | 仅靠 `finishAffinity()` 不够，需显式回调 |
| V4.3 | 挑衅次数初始显示 + 棋子缩小 10% | 初始化顺序影响 UI 状态 |
| V4.4 | GP 连接后同步挑衅次数 | `updateTauntDisplay()` 需在 `gamePresentation = pres` 后调用 |

---

## 关键 Bug 及修复记录

### 1. SoundFX 多线程崩溃 (SIGSEGV)
- **原因**：多线程共享单个 `AudioTrack` 引用
- **修复**：每个方法用 `val at = AudioTrack.Builder()...` 局部变量

### 2. 退出时 `lateinit countdownText` 未初始化崩溃
- **原因**：迁移 hack 中 `finish()` 在 `initMainUI()` 之前触发
- **修复**：添加 `::countdownText.isInitialized` 检查 → 最终用防呆替代迁移 hack

### 3. PowerShell UTF-8 编码损坏
- **现象**：`Get-Content -Raw | -replace | Set-Content` 后中文变乱码，编译失败
- **修复**：杜绝 PowerShell 管道编辑 `.kt` 文件，只用 `create_file` / `replace_string_in_file` / Python 脚本文件

### 4. `python -c` 多行代码被 PowerShell 解释
- **现象**：多行字符串被当作 Shell 命令分段执行
- **修复**：改用独立 `.py` 脚本文件

### 5. 副屏退出时主屏残留
- **原因**：`setLaunchDisplayId` 可能将 GP 放到独立任务栈，GP 的 `finishAffinity()` 无法跨栈关闭 MainActivity
- **修复**（V4.2）：GP 显式调用 `getMainActivity?.invoke()?.finishAffinity()` 后再自关

### 6. 白方挑衅次数初始显示为 0
- **原因**：`updateTauntDisplay()` 在 `onCreate` 中调用时 `gamePresentation` 还是 `null`（GP 需要 600ms 延迟连接）
- **修复**（V4.4）：GP 连接回调中也调用一次 `updateTauntDisplay()`

---

## 退出同步机制

| 触发方式 | MainActivity | GamePresentation |
|----------|-------------|------------------|
| 返回键 | `onBackPressed()` → `finishAffinity()` | `getMainActivity?.invoke()?.finishAffinity()` + `finishAffinity()` |
| Home 键 | `onUserLeaveHint()` → `finishAffinity()` | 同上 |

---

## 挑衅次数完整逻辑

```
程序首次启动 → tauntCountBlack=1, tauntCountWhite=1  (onCreate)
开始新游戏   → 不操作                                (V4.2 移除重置)
重新开始     → 不操作                                (V4.2 移除重置)
胜利方       → tauntCount += 3                       (handlePiecePlaced)
```

`updateTauntDisplay()` 调用时机：
1. `onCreate` 中 `initMainUI()` 之后（设置主屏显示）
2. `launchWhiteScreen` 回调中 `gamePresentation = pres` 之后（同步到副屏）
3. 每次消耗挑衅后
4. 胜利后（+3 更新）

---

## 动画系统

### 状态机
`animState`: 0=无, 1=胜利, 2=失败, 3=臭鸡蛋, 4=鲜花

### 驱动方式
- 自驱动：`ensureAnimating()` → `selfAnimRunnable` (33ms/帧)
- `updateAnimation()` 返回 `false` 时自动停止
- 胜利/失败动画由 `postDelayed` 清理（**不依赖** `animProgress > 1.5f` 自动停止——V4.1 已排除）

### 粒子生命周期
```
life 衰减 = 0.018/帧 (~30fps)
5 秒存活 ≈ life 3.0 ÷ 0.018 ≈ 167 帧
```

### 多阶段动画
| 动画 | 阶段 1 (0s) | 阶段 2 (1.5s) | 阶段 3 (2.8s) | 清理 (5s) |
|------|:-----------:|:-------------:|:-------------:|:---------:|
| 胜利 | 120 爆炸 + 100 彩带 + 80 闪光 | 80 随机烟花 | 60 上升烟花柱 | `winCleanupRunnable` |
| 失败 | 120 碎裂 + 100 雨滴 + 60 烟雾 + 50 裂纹 | 80 四周涌入 | 60 暗色雨滴 | `loseCleanupRunnable` |

### 棋子半径
`GomokuView.drawPiece()`: `r = cellSize * 0.40f`，预览棋子同步（V4.3 从 0.44f 缩小 10%）

---

## ⚠️ 编辑文件铁律

1. **永远不要用 PowerShell `Get-Content | -replace | Set-Content`** 编辑 `.kt` 文件 → 中文损坏
2. **永远不要用 `python -c "..."`** 传递含 Kotlin 代码的多行字符串 → PowerShell 截断
3. **编辑 `.kt` 文件只用 `create_file` / `replace_string_in_file` / `multi_replace_string_in_file`**
4. **需要批量生成代码用独立 `.py` 脚本文件**（如 `gen_v392.py`、`fix_gp.py`）
5. **版本号必须同时修改两个文件**：`MainActivity.kt` + `GamePresentation.kt`

---

## TTS 语音

`generate_tts.py` → edge-tts → `app/src/main/res/raw/` 下 24 个 MP3：
- 标准：`zh-CN-XiaoxiaoNeural`
- 幽默（东北话）：`zh-CN-liaoning-XiaobeiNeural`（文件前缀 `humor_`）
- `SoundFX.playVoice()` 根据 `voiceStyle` (0=标准, 1=幽默) 自动路由
