package com.gomoku.dualscreen

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 一个玩家窗口只负责一个玩家，落到哪块屏由固件状态算出（PanelMap），不做现场标定。
 * 方向交给系统：固件已按面板安装方向和交换状态补偿输出，应用再转一次就会倒置。
 */
abstract class BattleDisplayActivity : AppCompatActivity() {
    abstract val battlePlayer: Int
    private var cover: TextView? = null
    private var applied = ""
    private var moving = false
    private var gestureVersion = -1L
    private var lastSize = ""
    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        if (oldR > oldL && oldB > oldT && (r - l != oldR - oldL || b - t != oldB - oldT)) {
            BattleDisplays.configurationChanged()
        }
    }

    override fun onStart() {
        super.onStart()
        findViewById<View>(android.R.id.content).addOnLayoutChangeListener(layoutListener)
        BattleDisplays.attach(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        BattleDisplays.configurationChanged()
        super.onConfigurationChanged(newConfig)
    }

    override fun onStop() {
        findViewById<View>(android.R.id.content).removeOnLayoutChangeListener(layoutListener)
        BattleDisplays.detach(this)
        super.onStop()
    }

    override fun onBackPressed() {
        // 侧滑/返回退出是整机退出：两侧窗口属于两个 task，只 finish 自己会留下另一屏等双屏。
        BattleDisplays.closeAll()
        super.onBackPressed()
    }

    override fun onDestroy() {
        // 系统或用户关掉其中一扇窗口时，另一扇不能干等“等待双屏就绪”。
        if (isFinishing) BattleDisplays.windowClosed(this, moving)
        super.onDestroy()
    }

    /** 中性遮罩：只在固件状态读不到或只剩一块屏时出现，出现期间双方都不能落子。 */
    fun shield(message: String) {
        val root = findViewById<FrameLayout>(android.R.id.content)
        if (cover?.parent == root && cover?.visibility == View.VISIBLE && cover?.text == message) return
        if (cover?.parent != root) {
            cover = TextView(this).apply {
                gravity = Gravity.CENTER; textSize = 22f
                setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(35, 35, 35))
                // 可点击只为吞掉触摸，避免遮罩期间点穿到棋盘。
                isClickable = true
            }
            root.addView(cover, FrameLayout.LayoutParams(-1, -1))
        }
        cover?.text = message
        cover?.visibility = View.VISIBLE
        cover?.bringToFront()
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        gestureVersion = -1
        if (this is MainActivity) cancelBattlePreview()
        if (this is GamePresentation) clearPreview()
    }

    fun bind(target: Int, token: String, version: Long, rotation: Float) {
        if (moving) return
        if (display?.displayId != target) {
            moving = true
            try {
                val options = ActivityOptions.makeBasic().apply { launchDisplayId = target }
                startActivity(Intent(this, javaClass).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK), options.toBundle())
                finish()
            } catch (e: Exception) {
                moving = false
                android.util.Log.e("BattleDisplay", "Cannot route player=$battlePlayer to $target", e)
            }
            return
        }
        val root = findViewById<FrameLayout>(android.R.id.content)
        val size = "${root.width}x${root.height}"
        if (root.width == 0 || root.height == 0) return
        if (applied == token && lastSize == size) return
        applied = token; lastSize = size
        // 抵消系统投影（含整机姿态与屏幕交换），让画面固定在物理面板上。
        // ViewGroup 会把逆变换套到触摸上，所以落子坐标不需要另外换算。
        if (root.rotation != rotation) root.rotation = rotation
        val observer = root.viewTreeObserver
        observer.registerFrameCommitCallback {
            if (!isFinishing && applied == token) BattleDisplays.frame(this, version)
        }
        root.invalidate()
    }

    fun reveal() { cover?.visibility = View.GONE }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!BattleDisplays.ready) {
            // 遮罩在等固件状态时只让它吞事件，不往棋盘里送。
            if (cover?.visibility == View.VISIBLE) return super.dispatchTouchEvent(event)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) gestureVersion = BattleDisplays.version
        if (gestureVersion != BattleDisplays.version) return true
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) GameState.save(this)
        return handled
    }
}

/**
 * 双屏输出与过渡屏障。
 *
 * 玩家 → 屏 由 PanelMap 依固件状态算出，不再依赖标定文件；遮罩只用于三种真未知：
 * 交换状态读不到、可用面板不足两块、两块屏算出同一个 displayId。
 */
object BattleDisplays {
    fun hasWindow(player: Int) = hosts.containsKey(player)
    fun configurationChanged() { invalidate() }
    var ready = false; private set
    var version = 0L; private set
    private val hosts = mutableMapOf<Int, BattleDisplayActivity>()
    private val frames = mutableSetOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var executor: java.util.concurrent.ScheduledExecutorService? = null
    private var signature = ""
    private var lifecycle = 0L
    private var displayManager: DisplayManager? = null
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) { invalidate() }
        override fun onDisplayRemoved(id: Int) { invalidate() }
        override fun onDisplayChanged(id: Int) { invalidate() }
    }

    fun attach(host: BattleDisplayActivity) {
        hosts[host.battlePlayer]?.takeUnless { it === host }?.finish()
        hosts[host.battlePlayer] = host
        invalidate()
        if (executor != null) return
        val context = host.applicationContext
        displayManager = context.getSystemService(DisplayManager::class.java).also {
            it.registerDisplayListener(listener, handler)
        }
        val generation = ++lifecycle
        executor = Executors.newSingleThreadScheduledExecutor().also { worker ->
            worker.scheduleWithFixedDelay({
                val master = readProperty("persist.vendor.display.master")
                val shape = readProperty("vendor.nl.screen_mode")
                handler.post { if (generation == lifecycle) update(master, shape) }
            }, 0, 100, TimeUnit.MILLISECONDS)
        }
    }

    /** 只读属性；反射读取避免每 100ms 拉起一个 getprop 进程。 */
    private fun readProperty(name: String): String? = try {
        val value = Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java).invoke(null, name) as? String
        value?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }

    fun detach(host: BattleDisplayActivity) {
        if (hosts[host.battlePlayer] === host) { hosts.remove(host.battlePlayer); invalidate() }
        if (hosts.isEmpty()) {
            executor?.shutdownNow(); executor = null; lifecycle++
            displayManager?.unregisterDisplayListener(listener); displayManager = null
        }
    }

    /** 两侧一起退出：两个 Activity 在两个 task 里，必须逐个 finish，否则留下一屏空等。 */
    fun closeAll() {
        val open = hosts.values.toList()
        executor?.shutdownNow(); executor = null; lifecycle++
        displayManager?.unregisterDisplayListener(listener); displayManager = null
        ready = false; frames.clear()
        hosts.clear()
        open.forEach { runCatching { it.finishAffinity() } }
    }

    /**
     * 一扇窗口真的被关掉时（不是迁移、不是被新窗口替换），另一扇也要退出；
     * 但如果另一块面板本身已经没了/灭了，那是“等双屏恢复”，不能跟着退。
     */
    fun windowClosed(host: BattleDisplayActivity, migrating: Boolean) {
        val replaced = hosts[host.battlePlayer] != null
        val peer = hosts.values.firstOrNull()
        if (!ExitPolicy.closePeer(peer != null, availablePanels(), replaced, migrating)) return
        android.util.Log.i("BattleDisplay", "player=${host.battlePlayer} closed, finishing peer window")
        peer?.let { runCatching { it.finishAffinity() } }
    }

    private fun availablePanels(): Int {
        val dm = displayManager ?: return 0
        return dm.displays.count { !isVirtualDisplay(it) && it.isValid && it.state != Display.STATE_OFF }
    }

    private fun invalidate() {
        ready = false; version++; frames.clear(); signature = ""
        hosts.values.forEach { it.shield("等待双屏就绪") }
    }

    /** getType/getUniqueId 在部分 SDK 上是隐藏接口，用反射读取；读不到时按“无类型信息”处理。 */
    private fun isVirtualDisplay(d: Display): Boolean {
        val type = try {
            Display::class.java.getMethod("getType").invoke(d) as Int
        } catch (_: Exception) { -1 }
        if (type == 5) return true   // Display.TYPE_VIRTUAL
        val id = try {
            Display::class.java.getMethod("getUniqueId").invoke(d) as? String
        } catch (_: Exception) { null }
        return id?.startsWith("virtual:") == true
    }

    private fun update(master: String?, shape: String?) {
        val host = hosts.values.firstOrNull() ?: return
        val dm = host.getSystemService(DisplayManager::class.java)
        val panels = dm.displays
            .filter { !isVirtualDisplay(it) && it.isValid && it.state != Display.STATE_OFF }
            .sortedBy { it.displayId }
        (hosts[GomokuGame.PLAYER_BLACK] as? MainActivity)?.ensureWhiteScreen()

        val primary = panels.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY }
        val secondary = panels.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        val routes = PanelMap.routes(primary?.displayId ?: -1, secondary?.displayId, master)

        val next = "${master ?: "?"}|${shape ?: "?"}|" + panels.joinToString {
            "${it.displayId}:${it.state}:${it.rotation}:${it.name}"
        }
        if (signature != next) {
            invalidate(); signature = next
            android.util.Log.i("BattleDisplay", "version=$version state=$next routes=$routes")
        }
        if (routes == null) {
            hosts.values.forEach { it.shield(blockedReason(panels, master)) }
            return
        }
        hosts.toMap().forEach { (player, activity) ->
            val target = routes[player] ?: return@forEach
            val windowRotation = dm.getDisplay(target)?.rotation ?: activity.display?.rotation ?: 0
            activity.bind(
                target, "$version:$next", version,
                PanelMap.contentRotation(windowRotation, master, shape)
            )
        }
    }

    private fun blockedReason(panels: List<Display>, master: String?): String = when {
        master !in listOf("0", "1") ->
            "读不到屏幕交换状态 persist.vendor.display.master\n请确认固件与读取权限后重启应用"
        panels.size < 2 ->
            "等待双屏恢复\n当前可用面板 ${panels.size} 块"
        else -> "双屏状态异常，等待恢复"
    }

    fun frame(host: BattleDisplayActivity, frameVersion: Long) {
        if (frameVersion != version || hosts[host.battlePlayer] !== host) return
        frames.add(host.battlePlayer)
        if (frames.containsAll(listOf(1, 2)) && hosts.size == 2) {
            ready = true
            android.util.Log.i("BattleDisplay", "ready version=$version both frames committed")
            hosts.values.forEach { it.reveal() }
        }
    }
}
