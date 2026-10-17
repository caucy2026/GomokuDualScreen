# KEMI 双屏对战应用适配指南（V1.1）

适用于 KEMI Vibe Pads S1 / `hi3781v730` / Android 12，**两名玩家各看一面**的双屏应用
（棋类对战、卡牌、双人小游戏等）。单页瀑布拼接、A 字同显类应用不适用本文的玩家归属部分，
但方向、过渡、退出三节同样适用。

本文是本项目（五子棋双屏 4.7）实机验证过的结论，不是推演。每条都写了“为什么”和“踩过的坑”。

---

## 0. 三条铁律

1. **玩家绑定物理面板，不绑定 `displayId`。** 物理面板由硬件决定，`displayId` 会随交换改变角色。
2. **画面朝向要抵消“显示投影”，不是抵消窗口 rotation。** 交换会额外加 180°，只补窗口方向会翻。
3. **两个窗口在两个 task 里，退出必须显式关两个。** 只 `finish` 自己，另一屏会永远停在等待。

---

## 1. 五个身份，别混

| 身份 | 含义 | 会不会变 |
| --- | --- | --- |
| 物理面板 A | 出厂主屏，MIPI 转 eDP，笔记本形态在下方 | 不变 |
| 物理面板 B | 出厂副屏，HDMI 转 eDP，笔记本形态在上方 | 不变 |
| 逻辑显示 `displayId` | 0 = 逻辑主屏，另一块（本机 2）= 逻辑副屏 | 交换时角色互换 |
| 窗口 | Activity 落在哪个 display 上 | 交换/迁移会变 |
| 玩家 | 谁执黑、谁执白 | 不变 |

**不要用 `displayId == 0` 推断“这是主玩家”或“这面朝下”。** 交换时 `displayId` 编号不变，
但它对应到哪块实体面板会变。

### 1.1 映射算法

固件说明（`vendor/newlink/development/docs/AI双屏助手产品形态与软件状态说明.md` 第七章 1 节）：

- 交换关闭 `persist.vendor.display.master = "0"`：逻辑主屏显示在 A，逻辑副屏显示在 B；
- 交换开启 `master = "1"`：逻辑主屏显示在 B，逻辑副屏显示在 A。

```kotlin
// 物理面板 A 固定玩家 1（黑），物理面板 B 固定玩家 2（白）
fun routes(primaryId: Int, secondaryId: Int?, master: String?): Map<Int, Int>? {
    if (primaryId < 0 || secondaryId == null || secondaryId == primaryId) return null
    return when (master) {
        "0" -> mapOf(PLAYER_1 to primaryId, PLAYER_2 to secondaryId)
        "1" -> mapOf(PLAYER_1 to secondaryId, PLAYER_2 to primaryId)
        else -> null            // 读不到就遮罩等待，绝不猜
    }
}
```

取“逻辑副屏”的规则：**排除 `TYPE_VIRTUAL` 与 `STATE_OFF`，在剩下的里取第一个非主屏**，
不要写死 `displayId = 2`（别的机型/固件可能是 1 或其它值）。`getType()` 在部分 SDK 上是隐藏接口，
用反射读，读不到时再按 `uniqueId` 是否以 `virtual:` 开头兜底。

```kotlin
val panels = displayManager.displays
    .filter { !isVirtual(it) && it.isValid && it.state != Display.STATE_OFF }
    .sortedBy { it.displayId }
```

### 1.2 为什么不能靠“标定”代替

曾经的做法是让用户在每种状态下手动贴 A/B 标定，理由是“软件分不清物理面板”。
实际上 `persist.vendor.display.master` 就是固件给出的交换语义，配合 `displayId` 足以判定归属，
不需要标定，也不该为此在常态显示中性遮罩（那等于让用户每次切形态都要点一遍）。

---

## 2. 方向：抵消显示投影

### 2.1 两个“旋转”不是一回事

- **窗口 rotation**：`Display.getRotation()`，0/1/2/3 表示 0°/90°/180°/270°。应用拿到的是它。
- **显示投影方向**：`dumpsys display` 里 `DisplayViewport.orientation`，是系统把这块屏的输出映射到
  面板时的方向。应用**拿不到公开 API**，但可以从窗口 rotation 推导。

固件实测（说明文档第七章 3 节的十场景表）：

| 场景 | 窗口 rotation（主/副） | 显示投影（主/副） |
| --- | --- | --- |
| 0°，未交换 | `[0, 2]` | `[0, 2]` |
| 0°，已交换 | `[0, 2]` | `[2, 0]` |
| 180°，未交换 | `[2, 0]` | `[2, 0]` |
| 180°，已交换 | `[2, 0]` | `[0, 2]` |

规律：**交换打开时，投影 = 窗口方向 + 180°**；A 字形态不叠加这次补偿。

### 2.2 算法

```text
投影   = 窗口 rotation × 90 + (已交换且非 A 字 ? 180 : 0)
应用旋转 = 360 − 投影
```

```kotlin
fun contentRotation(windowRotation: Int, master: String?, screenMode: String?): Float {
    val window = (windowRotation % 4) * 90
    val swapped = master == "1" && screenMode != "1"   // A 字不叠加交换补偿
    val projection = (window + if (swapped) 180 else 0) % 360
    if (projection % 180 != 0) return 0f              // 竖持 90/270 需要换宽高，另行处理
    return ((360 - projection) % 360).toFloat()
}
```

把这层旋转施加在**内容根 View** 上（`android.R.id.content`）：

- `ViewGroup` 分发触摸时会套用子 View 的逆变换，所以落子/点击坐标不需要另行换算；
- 弹窗、提示、遮罩都在同一棵树里，会跟着一起转；
- 只用 0°/180°，不需要换宽高。

### 2.3 怎么不靠肉眼验证

预测值和设备读数对照，能提前发现算错：

```bash
adb shell dumpsys display | grep mViewports
# 每个 DisplayViewport 的 orientation 就是显示投影方向
```

用上面的公式算出两块屏的投影，应该和这里读到的 `orientation` 完全一致。
不一致就是公式或状态读取有问题，不要等上机看。

### 2.4 踩过的坑

- **写死“白方 +180°”**：0° 姿态碰巧对，设备转 180° 后两块屏全倒。方向必须跟姿态走。
- **只补窗口 rotation**：交换后窗口 rotation 没变、投影变了 180°，结果一块屏倒。
- **`screencap` 当证据**：`screencap` 抓的是合成前的画面，`orientation=2` 那块屏的截图是倒的，
  实际面板上是正的。**判断朝向必须看实体屏。**
- **在 Activity / View / GL 里重复补一次**：会出现双重旋转。

---

## 3. 窗口绑定与迁移

两个玩家两个 Activity，各自一个 task（`taskAffinity` 不同，例如白方加 `.white` 后缀），
由 `ActivityOptions.launchDisplayId` 拉到目标屏：

```kotlin
val options = ActivityOptions.makeBasic().apply { launchDisplayId = target }
startActivity(Intent(this, WhiteActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
```

窗口当前不在自己该在的屏上时，用同样方式拉起新实例再 `finish()` 旧实例（不要在同一个实例里改 display）。
迁移标记要在 `onDestroy` 里用上，避免把“迁移”误判成“用户退出”。

两个 Activity 都要 `android:screenOrientation="unspecified"`，不要锁横屏。

---

## 4. 过渡屏障（切形态不闪、不误落子）

状态指纹 = `master` + `screen_mode` + 每块屏的 `displayId / state / rotation / 名称`。
指纹变化时按固定顺序处理：

1. 旧版本号失效（`version++`），两侧立即上中性遮罩；
2. 给两侧内容补发 `ACTION_CANCEL`，清预览、清长按，丢弃旧输入；
3. 暂停双方（计时、AI、提醒都停），不允许只停一方；
4. 按新映射重绑窗口，必要时迁移；
5. 两侧各自 `viewTreeObserver.registerFrameCommitCallback` 提交**新版本帧**后，才撤遮罩、恢复输入。

不要用固定 `sleep` 当“完成条件”。触发源要合并：`DisplayManager` 的 Added/Removed/Changed +
`onConfigurationChanged` + 约 100ms 一次的只读属性采样（有些帧交换不发 Display 事件）。

属性读取用反射，不要每帧 `ProcessBuilder("/system/bin/getprop")`：

```kotlin
private fun readProperty(name: String): String? = try {
    (Class.forName("android.os.SystemProperties")
        .getMethod("get", String::class.java).invoke(null, name) as? String)?.takeIf { it.isNotEmpty() }
} catch (_: Exception) { null }
```

---

## 5. 遮罩只留三种真未知

常态下不该出现遮罩。只有这几种情况允许上遮罩并停输入：

1. `persist.vendor.display.master` 读不到或不是 `0/1`；
2. 可用实体面板不足两块（一屏 `STATE_OFF` / 被移除 / 只剩虚拟屏）；
3. 两块屏算出同一个 `displayId`。

`vendor.nl.screen_mode` **缺失不能阻塞开局**，它只用于诊断和 A 字分支。我们踩过这个坑：
新固件不暴露该属性，旧代码要求它有效，结果永远停在遮罩页、连标定都存不进去。

---

## 6. 退出与生命周期

两个窗口在两个 task 里，`finishAffinity()` 只关自己那个 task。所以：

```kotlin
/** 两侧一起退：逐个 finishAffinity()，两个 task 都关 */
fun closeAll() {
    val open = hosts.values.toList()
    hosts.clear()
    open.forEach { runCatching { it.finishAffinity() } }
}
```

- `onBackPressed`（返回键和侧滑都走这里）→ `closeAll()`；
- 应用内“退出”按钮 → 清理资源后 `closeAll()`；
- **不要依赖对方 Activity 的引用**（`peer?.finish()`）：那个引用可能为空，一空就漏掉一个 task。

一扇窗口被系统/手势单独关掉时的兜底：

```kotlin
override fun onDestroy() {
    if (isFinishing) BattleDisplays.windowClosed(this, migrating = moving)
    super.onDestroy()
}

// 另一扇是否要跟着退
fun closePeer(peerAlive: Boolean, panelsAvailable: Int, replaced: Boolean, migrating: Boolean) =
    !migrating && !replaced && peerAlive && panelsAvailable >= 2
```

- `migrating`：这一扇正在迁往另一块屏 → 不关对方；
- `replaced`：这个玩家已被新窗口替换（切换/重建）→ 不关对方；
- **`panelsAvailable < 2`：一块屏灭了，这是“等双屏恢复”，不能跟着退。**

---

## 7. 会话、界面与状态一致性

### 7.1 持久化

- 对局状态、历史、回合、暂停次数写 `AtomicFile`，落子后和 `onStop` 时各存一次；
- Activity 重建（转屏/切形态）共享内存单例会话，进程被杀后从文件恢复；
- **不要在触摸事件里写盘**：每次 MOVE 都序列化整局会卡。

### 7.2 会话对象只有一份，视图不要缓存

两块屏的 Activity 各自持有 UI，但**对局状态必须始终读同一个单例**：

```kotlin
// 对：每次都取单例
private val game: GomokuGame get() = GameState.game

// 错：onCreate 里存一份，之后会话被替换就指到旧对象
private lateinit var game: GomokuGame
```

缓存实例的后果是两侧状态各说各话：状态栏显示“轮到你”，棋盘判断却是“不是你的回合”，
点击直接被拒且没有提示。真机上我们就是被这个坑过一次——白方窗口拿着旧对象，白子落不下去。

凡是“会话被替换”的路径都要考虑：从文件恢复、Activity 重建、换对局。视图里只留 `get()` 引用，
不要留字段。

### 7.3 请求确认弹窗：两块屏必须同款

“重新开始 / 悔棋 / 退出”这类需要对方同意的弹窗，有三条硬要求：

1. **同一套样式**：一边用系统 `AlertDialog`、一边自绘卡片，用户会在两块屏上看到两种样子；
2. **开在被请求方那侧**：谁发起，弹窗就出现在对方屏上，由对方点同意/不同意；
3. **跟着那块屏一起转**：弹窗挂在被请求方的内容根 View 里，旋转 180° 时跟着转，发起方看到的是倒的。

实现方式：抽一个共用的确认框（本项目是 `RequestDialogs`），两种 Activity 都调它，
只按请求类型换颜色（重新开始橙、悔棋蓝、退出红）。同时只允许存在一个确认框，连点不叠。

---

## 8. 自测与验收

### 8.1 十种摆放场景

5 种方位 × 交换开/关：0°、顺时针 90° 书本式、整机 180°、顺时针 270°、A 字，各配交换开/关。
逐格从**进入 / 退出 / 冷启动**各来一遍。每格检查：

- [ ] 玩家 1 的面板仍是玩家 1 的视图，玩家 2 同理，没有互换
- [ ] 画面里贴的 ▲ 与面板上的 ▲ 方向一致（人眼，不看截图）
- [ ] 完整画面，不裁半屏、不同显、不镜像
- [ ] 四角 + 中心点击命中
- [ ] 两侧同时多指，不串玩家、不串手指
- [ ] 按住期间切形态：旧手势被取消，不误落子
- [ ] 对局不重开
- [ ] 侧滑/返回退出：两屏都退干净
- [ ] 两侧各点一次“重新开始/悔棋/退出”：弹窗都出现在**对方屏**，且两块屏样式一致
- [ ] 按住落子中切形态：旧手势取消、不误落子；切完继续下，两侧回合显示一致

### 8.2 日志字段

每个状态变化打一行，便于现场对账：

```text
version=42 state=<master>|<screen_mode>|<id>:<state>:<rotation>:<name>, ... routes={1=0, 2=2}
```

### 8.3 常用命令

```bash
adb shell getprop persist.vendor.display.master   # 0 未交换 / 1 已交换
adb shell getprop vendor.nl.screen_mode           # 1 A字 / 0 L形 / -1 普通（可能缺失）
adb shell dumpsys display | grep mViewports       # 看每块屏的 orientation（= 显示投影）
adb shell dumpsys window | grep mCurrentFocus     # 两屏各自前台窗口
adb logcat -s BattleDisplay                       # 应用自己的状态日志
```

---

## 9. 项目内的实现索引

| 职责 | 文件 |
| --- | --- |
| 玩家→display 映射、方向换算 | `app/src/main/java/com/gomoku/dualscreen/PanelMap.kt` |
| 双屏会话、过渡屏障、窗口绑定、退出 | `app/src/main/java/com/gomoku/dualscreen/BattleDisplayActivity.kt` |
| 退出策略（是否连对方一起退） | `app/src/main/java/com/gomoku/dualscreen/ExitPolicy.kt` |
| 请求确认弹窗（重开/悔棋/退出，两屏同款） | `app/src/main/java/com/gomoku/dualscreen/RequestDialogs.kt` |
| 两个玩家窗口 | `MainActivity.kt`（黑）、`GamePresentation.kt`（白） |
| 会话持久化 | `GameState.kt`、`GomokuGame.kt` |
| 单元测试 | `app/src/test/java/com/gomoku/dualscreen/` |

单元测试覆盖：映射（含交换、未知状态、副屏不写死）、方向（四种姿态组合 + 交换 + A 字 + 竖持回退）、
退出策略。跑法：

```bash
gradlew testDebugUnitTest assembleRelease
```

---

## 10. 踩坑清单（按出问题顺序）

| 现象 | 根因 | 结论 |
| --- | --- | --- |
| 转 180° 后两屏都倒 | 写死“白方 +180°” | 方向跟姿态走 |
| 交换后两屏都倒 | 只补窗口 rotation，没算交换的 180° | 补“显示投影” |
| 一屏侧滑退出，另一屏停在“等待双屏就绪” | 两个 task 只 finish 了自己 | `closeAll()` 显式关两个 |
| 每次切形态都要点标定 | 把面板归属当成了不可知 | 用 `master` 算 |
| 新固件上永远停在遮罩 | 要求 `screen_mode` 有效 | 该属性降级为诊断用 |
| 帧率/耗电异常 | 每 100ms 拉一次 `getprop` 进程 | 反射读属性 |
| 截图判断朝向判错 | `screencap` 是合成前画面 | 看实体屏 |
| 转屏后 Activity 重建丢局 | 会话未持久化 | `AtomicFile` + 单例会话 |
| 状态栏说“轮到你”，棋盘却说“等对方” | 视图缓存了对局实例，会话被替换后指向旧对象 | 视图只留 `GameState.game` 的 `get()` 引用 |
| 副屏点重开，主屏弹窗跟平时长得不一样 | 一边系统 `AlertDialog`、一边自绘卡片 | 请求弹窗抽共用实现，两屏同款 |
| 两侧弹窗叠了两个 | 连点没有去重 | 同一时刻只允许一个确认框 |

---

*本文依据固件说明 `AI双屏助手产品形态与软件状态说明.md`（V1.1，第七章）与五子棋双屏 4.7.3 的实机验证结论整理。*

## 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| V1.1 | 2026-10-17 | 补“会话对象只有一份、视图不缓存”“请求弹窗两屏同款、开在被请求方”；新增对应坑位与验收项 |
| V1.0 | 2026-10-17 | 首版：映射、方向（显示投影）、窗口绑定、过渡屏障、遮罩、退出 |
