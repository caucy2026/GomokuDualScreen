package com.gomoku.dualscreen

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A calibrated output is a route to a physical panel, never a player inferred from display 0. */
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

    fun shield(message: String) {
        val root = findViewById<FrameLayout>(android.R.id.content)
        if (cover?.parent == root && cover?.visibility == View.VISIBLE && cover?.text == message) return
        if (cover?.parent != root) {
            cover = TextView(this).apply {
                gravity = Gravity.CENTER; textSize = 22f
                setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(35, 35, 35))
                isClickable = true
                setOnClickListener {
                    startActivity(Intent(this@BattleDisplayActivity, DisplayCalibrationActivity::class.java))
                }
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

    fun bind(route: JSONObject, token: String, version: Long) {
        if (moving) return
        val target = route.getInt("displayId")
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
        // Only calibrated 0/180 corrections: Android View dispatch applies the inverse to touch.
        root.rotation = route.getInt("rotation").toFloat()
        val observer = root.viewTreeObserver
        observer.registerFrameCommitCallback {
            if (!isFinishing && applied == token) BattleDisplays.frame(this, version)
        }
        root.invalidate()
    }

    fun reveal() { cover?.visibility = View.GONE }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!BattleDisplays.ready) {
            // Only the full-window neutral calibration cover receives input while paused.
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

/** Shared transition barrier. Unknown profiles and either missing panel pause both players. */
object BattleDisplays {
    fun hasWindow(player: Int) = hosts.containsKey(player)
    fun configurationChanged() { invalidate() }
    var calibrationKey = ""; private set
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
                val mode = readProperty("vendor.nl.screen_mode")
                val config = try { JSONObject(File(context.filesDir, "battle-display.json").readText()) }
                    catch (_: Exception) { null }
                handler.post { if (generation == lifecycle) update(Pair(master, mode), config) }
            }, 0, 100, TimeUnit.MILLISECONDS)
        }
    }

    /** Read-only property probe; reflection avoids spawning /system/bin/getprop ten times a second. */
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

    private fun invalidate() {
        ready = false; version++; frames.clear(); signature = ""
        hosts.values.forEach { it.shield("等待双屏恢复 / 核验物理屏映射") }
    }

    private fun update(properties: Pair<String?, String?>, config: JSONObject?) {
        val host = hosts.values.firstOrNull() ?: return
        val dm = host.getSystemService(DisplayManager::class.java)
        val displays = dm.displays.sortedBy { it.displayId }
        // Firmware builds differ: some expose vendor.nl.screen_mode, some do not. A missing shape is
        // recorded as "?" so it still has its own calibration entry instead of blocking calibration.
        val master = properties.first
        val shapeKnown = CalibrationKey.shapeKnown(properties.second)
        val key = CalibrationKey.of(master, properties.second, displays.map { it.displayId to it.rotation })
        calibrationKey = key
        (hosts[1] as? MainActivity)?.ensureWhiteScreen()
        val profile = if (master in listOf("0", "1") &&
            config?.optString("fingerprint") == Build.FINGERPRINT) config?.optJSONObject("states")?.optJSONObject(key) else null
        val routes = try {
            val confirmed = requireNotNull(profile)
            listOf(confirmed.getJSONObject("A"), confirmed.getJSONObject("B")).also { routes ->
                require(routes[0].getInt("displayId") != routes[1].getInt("displayId"))
                routes.forEach { route ->
                    val d = dm.getDisplay(route.getInt("displayId"))
                    require(d != null && d.isValid && d.state == Display.STATE_ON)
                    require(route.getString("displayName") == d.name)
                    require(route.getInt("rotation") in listOf(0, 180))
                }
            }
        } catch (_: Exception) { null }
        val next = "$key|$profile|" + displays.joinToString { "${it.displayId}:${it.state}:${it.mode.physicalWidth}x${it.mode.physicalHeight}" }
        if (signature != next) {
            invalidate(); signature = next
            android.util.Log.i("BattleDisplay", "version=$version key=$key shapeKnown=$shapeKnown profile=$profile")
        }
        if (routes == null) {
            val hint = if (shapeKnown) "" else "\n固件未提供 vendor.nl.screen_mode，本状态按未知形态单独标定"
            hosts.values.forEach { it.shield("等待双屏标定或恢复\n$key$hint\n点击进入本屏标定") }
            return
        }
        hosts.toMap().forEach { (player, activity) -> activity.bind(routes[player - 1], "$version:$next", version) }
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
