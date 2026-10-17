package com.gomoku.dualscreen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class GamePresentation : BattleDisplayActivity() {
    override val battlePlayer = GomokuGame.PLAYER_WHITE

    companion object {
        var instance: GamePresentation? = null
        lateinit var sharedGame: GomokuGame
        var sharedPerspective: Int = GomokuGame.PLAYER_WHITE
    }

    private val playerPerspective: Int get() = sharedPerspective
    private val game: GomokuGame get() = GameState.game

    private var gomokuView: GomokuView? = null
    internal val gomoku: GomokuView? get() = gomokuView
    private var statusText: TextView? = null
    private var countdownText: TextView? = null
    var onPiecePlaced: ((row: Int, col: Int) -> Unit)? = null
    var onStartOrRestart: (() -> Unit)? = null
    var onUndoRequest: (() -> Unit)? = null
    var getMainActivity: (() -> MainActivity)? = null
    var onAutoToggle: (() -> Unit)? = null
    private var autoBtn: Button? = null
    private var startBtn: Button? = null
    private var pauseBtnWhite: Button? = null
    private var tauntCountView: TextView? = null

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameState.restore(this)
        instance = this
        // V4.0: 视角由 MainActivity 根据目标显示屏 ID 显式设置，不自行检测
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = if (isLandscape()) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DEB887"))
        }
        gomokuView = GomokuView(this).apply {
            this.playerPerspective = this@GamePresentation.playerPerspective
            this.game = this@GamePresentation.game
            this.showPieceOrder = getSharedPreferences("gomoku_settings", Context.MODE_PRIVATE).getBoolean("piece_order", false)
            onConfirmPlace = { row, col -> this@GamePresentation.onPiecePlaced?.invoke(row, col) }
        }

        val smallSize = (resources.displayMetrics.density * 60).toInt()
        val leftPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(12, 12, 4, 12)
        }
        // 自动对战按钮
        val autoBtnRef = create3DButton("\uD83E\uDD16\n\u81EA\u52A8", "#546E7A", "#263238", smallSize)
        autoBtn = autoBtnRef
        autoBtnRef.setOnClickListener { onAutoToggle?.invoke() }
        leftPanel.addView(autoBtnRef, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 12 })

        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.kemi_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { gomokuView?.startFlowerAnimation(); SoundFX.playCheerfulSound() }
        }
        leftPanel.addView(logoImg, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 12 })

        val hurryBtn = create3DButton("\u23F0\n\u50AC", "#FF9800", "#E65100", smallSize)
        hurryBtn.setOnClickListener {
            if (!game.isActive || game.isGameOver) { showPopupMessage("\u6E38\u620F\u672A\u5F00\u59CB"); return@setOnClickListener }
            if (game.isPaused && game.pausedByPlayer != playerPerspective) { showPopupMessage("\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u8BF7\u7B49\u5F85\u5BF9\u65B9\u6062\u590D"); return@setOnClickListener }
            if (game.currentPlayer == playerPerspective) { showPopupMessage("\u8F6E\u5230\u4F60\u4E86\uFF0C\u4E0D\u80FD\u50AC\u81EA\u5DF1"); return@setOnClickListener }
            val rid = if (playerPerspective == GomokuGame.PLAYER_BLACK) R.raw.hurry_black else R.raw.hurry_white
            SoundFX.playVoice(this, rid)
            getMainActivity?.invoke()?.shakeMainForWhite()
        }
        val tauntBtn = create3DButton("\uD83D\uDE08\n\u6311\u8845", "#9C27B0", "#4A148C", smallSize)
        tauntBtn.setOnClickListener {
            val ma = getMainActivity?.invoke() ?: return@setOnClickListener
            val myCount = if (playerPerspective == GomokuGame.PLAYER_BLACK) ma.tauntCountBlack else ma.tauntCountWhite
            if (myCount <= 0) { showPopupMessage("\u6311\u8845\u6B21\u6570\u4E0D\u8DB3\uFF0C\u8D62\u5F97\u6E38\u620F\u83B7\u53D6"); return@setOnClickListener }
            if (playerPerspective == GomokuGame.PLAYER_BLACK) ma.tauntCountBlack-- else ma.tauntCountWhite--
            ma.updateTauntDisplay()
            val rid = if (playerPerspective == GomokuGame.PLAYER_BLACK) R.raw.taunt_black else R.raw.taunt_white
            SoundFX.playVoice(this, rid)
            ma.startEggOnMain()
        }
        leftPanel.addView(hurryBtn, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 16 })
        leftPanel.addView(tauntBtn, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 4 })

        val tcText = TextView(this).apply { text = "\u00D70"; textSize = 11f; setTextColor(Color.parseColor("#9C27B0")); gravity = Gravity.CENTER }
        tauntCountView = tcText
        leftPanel.addView(tcText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })

        val pauseBtn = create3DButton("\u23F8\n\u6682\u505C", "#607D8B", "#37474F", smallSize)
        pauseBtnWhite = pauseBtn
        pauseBtn.setOnClickListener {
            if (!game.isActive || game.isGameOver) { showPopupMessage("\u6E38\u620F\u672A\u5F00\u59CB"); return@setOnClickListener }
            if (game.isPaused) {
                if (game.pausedByPlayer == playerPerspective) {
                    game.isPaused = false; game.pausedByPlayer = GomokuGame.EMPTY
                    updatePauseBtn(false)
                    getMainActivity?.invoke()?.updatePauseBtnForWhite(false)
                    updateStatusText()
                    getMainActivity?.invoke()?.updateStatusDisplay()
                    getMainActivity?.invoke()?.restartCountdown()
                } else { showPopupMessage("\u7B49\u5F85\u5BF9\u65B9\u6062\u590D\u6E38\u620F") }
            } else {
                val myPauseCount = if (playerPerspective == GomokuGame.PLAYER_BLACK) game.pauseCountBlack else game.pauseCountWhite
                if (myPauseCount <= 0) { showPopupMessage("\u6682\u505C\u6B21\u6570\u5DF2\u7528\u5B8C"); return@setOnClickListener }
                if (playerPerspective == GomokuGame.PLAYER_BLACK) game.pauseCountBlack-- else game.pauseCountWhite--
                game.isPaused = true; game.pausedByPlayer = playerPerspective
                updatePauseBtn(true)
                getMainActivity?.invoke()?.updatePauseBtnForWhite(true)
                updateStatusText()
                getMainActivity?.invoke()?.updateStatusDisplay()
            }
        }
        leftPanel.addView(pauseBtn, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 4 })
        root.addView(leftPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val boardLp = if (isLandscape()) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(gomokuView, boardLp)

        val panel = LinearLayout(this).apply { orientation = if (isLandscape()) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(8, 12, 8, 8) }
        statusText = TextView(this).apply { text = "\u26AA\u767D\u65B9\n\u7B49\u5F85\u5F00\u59CB..."; textSize = 14f; setTextColor(Color.parseColor("#4A3728")); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4) }
        panel.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = if (isLandscape()) 8 else 0; rightMargin = if (isLandscape()) 0 else 12 })
        countdownText = TextView(this).apply { text = ""; textSize = 18f; setTextColor(Color.parseColor("#D84315")); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4) }
        panel.addView(countdownText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = if (isLandscape()) 12 else 0; rightMargin = if (isLandscape()) 0 else 12 })

        val btnSize = (resources.displayMetrics.density * 120).toInt()
        val btnMargin = if (isLandscape()) 24 else 0; val btnMargin2 = if (isLandscape()) 0 else 16
        startBtn = create3DButton("\u25B6\n\u5F00\u59CB", "#4CAF50", "#2E7D32", btnSize)
        startBtn!!.setOnClickListener { onStartOrRestart?.invoke() }
        val undoBtn = create3DButton("\u21A9\n\u6094\u68CB", "#78909C", "#455A64", btnSize)
        undoBtn.setOnClickListener { onUndoRequest?.invoke() }
        panel.addView(startBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = btnMargin; rightMargin = btnMargin2 })
        panel.addView(undoBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = 8; rightMargin = btnMargin2 })
        panel.addView(TextView(this).apply { text = "V${BuildConfig.VERSION_NAME}"; textSize = 10f; setTextColor(Color.parseColor("#998B7355")); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val settingsBtn = TextView(this).apply { text = "\u2261"; textSize = 22f; setTextColor(Color.parseColor("#998B7355")); gravity = Gravity.CENTER; setPadding(12, 0, 12, 4); setOnClickListener { showSettingsDialog() } }
        panel.addView(settingsBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val panelLp = if (isLandscape()) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(panel, panelLp)
        setContentView(root)
        GameState.mainActivity?.connectWhiteScreen(this)
    }

    private fun create3DButton(text: String, colorTop: String, colorBottom: String, size: Int): Button = Button(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(8, 8, 8, 8); setBackgroundColor(Color.TRANSPARENT)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(size, size); colors = intArrayOf(Color.parseColor(colorTop), Color.parseColor(colorBottom)); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) }
        elevation = 12f; isAllCaps = false
    }

    fun showPopupMessage(msg: String) {
        val container = findViewById<FrameLayout>(android.R.id.content) ?: return
        for (i in container.childCount - 1 downTo 0) { if (container.getChildAt(i)?.tag == "popup_msg") container.removeViewAt(i) }
        val overlay = FrameLayout(this).apply { tag = "popup_msg" }
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(40, 28, 40, 28); background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 22f; setColor(Color.parseColor("#EE2D2D2D")); setStroke(3, Color.parseColor("#CCFFD700")) }; alpha = 0f; animate().alpha(1f).setDuration(250).start() }
        card.addView(TextView(this).apply { text = msg; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; setShadowLayer(4f, 0f, 2f, Color.parseColor("#AA000000")) })
        overlay.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        overlay.setOnClickListener { card.animate().alpha(0f).setDuration(200).withEndAction { container.removeView(overlay) }.start() }
        container.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        gomokuView?.postDelayed({ card.animate().alpha(0f).setDuration(300).withEndAction { (overlay.parent as? ViewGroup)?.removeView(overlay) }.start() }, 2200)
    }

    fun setTauntCount(c: Int) { tauntCountView?.text = "\u00D7$c" }
    fun updatePieceOrder(on: Boolean) { gomokuView?.showPieceOrder = on; gomokuView?.invalidate() }
    fun clearAllAnimations() = gomokuView?.clearAllAnimations()
    fun startEggAnimation() = gomokuView?.startEggAnimation()
    fun refreshView() = gomokuView?.invalidate()
    fun clearPreview() = gomokuView?.clearPreview()
    fun showMessage(msg: String) = gomokuView?.showMessage(msg)
    fun clearMessage() = gomokuView?.clearMessage()
    fun startWinAnimation() = gomokuView?.startWinAnimation()
    fun startLoseAnimation() = gomokuView?.startLoseAnimation()
    fun updateAnimation(): Boolean = gomokuView?.updateAnimation() ?: false

    fun updatePauseBtn(paused: Boolean) {
        pauseBtnWhite?.let {
            if (paused) { it.text = "\u23F8\n\u6682\u505C\u4E2D"; it.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(it.width, it.height); colors = intArrayOf(Color.parseColor("#FF8F00"), Color.parseColor("#E65100")); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } }
            else { it.text = "\u23F8\n\u6682\u505C"; it.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(it.width, it.height); colors = intArrayOf(Color.parseColor("#607D8B"), Color.parseColor("#37474F")); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } }
        }
    }

    fun setCountdown(sec: Int) { countdownText?.text = when { sec == -2 -> "\u23F8"; sec >= 0 -> "\u23F1${sec}s"; else -> "" } }

    fun updateStatusText() {
        val g = game; val pc = if (playerPerspective == GomokuGame.PLAYER_BLACK) "\u26AB\u9ED1\u65B9" else "\u26AA\u767D\u65B9"
        statusText?.text = if (!g.isActive) "$pc\n\u7B49\u5F85\u5F00\u59CB..."
        else if (g.isGameOver) { if (g.winner == playerPerspective) "\uD83C\uDF89\u4F60\u8D62\u4E86\uFF01" else if (g.winner == GomokuGame.EMPTY) "\uD83E\uDD1D\u5E73\u5C40" else "\uD83D\uDE1E\u4F60\u8F93\u4E86" }
        else if (g.isPaused) { if (g.pausedByPlayer == playerPerspective) "$pc\n\u23F8 \u4F60\u6682\u505C\u4E86\u6E38\u620F" else "$pc\n\u23F8 \u5BF9\u65B9\u6682\u505C\u4E2D" }
        else { if (g.currentPlayer == playerPerspective) "$pc\n\uD83D\uDC49\u8F6E\u5230\u4F60\u4E86" else "$pc\n\u23F3\u7B49\u5F85\u5BF9\u65B9" }
    }

    fun showUndoRequestDialog(requesterName: String, callback: (Boolean) -> Unit) =
        RequestDialogs.undo(this, requesterName, callback)

    fun updateButtonState() { val btn = startBtn ?: return; if (game.isActive && !game.isGameOver) { btn.text = "\uD83D\uDD04\n\u91CD\u65B0\u5F00\u59CB"; btn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; colors = intArrayOf(Color.parseColor("#FF8F00"), Color.parseColor("#E65100")); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } } else { btn.text = "\u25B6\n\u5F00\u59CB"; btn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; colors = intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#2E7D32")); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } } }

    fun showRestartRequestDialog(requesterName: String, callback: (Boolean) -> Unit) =
        RequestDialogs.restart(this, requesterName, callback)


    fun showExitRequestDialog(callback: (Boolean) -> Unit) =
        RequestDialogs.exit(this, "退出游戏请求", "黑方请求退出游戏，是否同意？", callback)

    fun updateAutoButton(isAuto: Boolean) {
        autoBtn?.let { b ->
            if (isAuto) {
                b.text = "\uD83E\uDD16\n\u505C\u6B62"
                b.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setSize(b.width, b.height)
                    colors = intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#2E7D32"))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    setStroke(3, Color.parseColor("#CCFFFFFF"))
                }
            } else {
                b.text = "\uD83E\uDD16\n\u81EA\u52A8"
                b.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setSize(b.width, b.height)
                    colors = intArrayOf(Color.parseColor("#546E7A"), Color.parseColor("#263238"))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    setStroke(3, Color.parseColor("#CCFFFFFF"))
                }
            }
        }
    }

    fun shakeScreen() { val v = gomokuView ?: return; android.animation.ValueAnimator.ofFloat(0f, 16f, -16f, 12f, -12f, 10f, -10f, 8f, -8f, 5f, -5f, 3f, -3f, 0f).apply { duration = 2000; addUpdateListener { v.translationX = it.animatedValue as Float }; start() } }

    private fun showSettingsDialog() { val container = findViewById<FrameLayout>(android.R.id.content) ?: return; val overlay = FrameLayout(this).apply { tag = "settings_dlg"; setBackgroundColor(Color.parseColor("#80000000")) }; val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(36, 30, 36, 24); background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 22f; setColor(Color.parseColor("#F5F0E8")); setStroke(3, Color.parseColor("#FF8F00")) }; alpha = 0f; animate().alpha(1f).setDuration(250).start() }; card.addView(TextView(this).apply { text = "\u2699 \u6E38\u620F\u8BBE\u7F6E"; textSize = 20f; setTextColor(Color.parseColor("#4A3728")); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, 20) }); card.addView(View(this).apply { setBackgroundColor(Color.parseColor("#DDD7C8")); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { bottomMargin = 16 } }); val sRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 12) }; sRow.addView(TextView(this).apply { text = "\uD83D\uDD0A \u843D\u5B50\u97F3\u6548"; textSize = 16f; setTextColor(Color.parseColor("#4A3728")) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); val sSw = Switch(this).apply { isChecked = SoundFX.stoneSoundEnabled; setOnCheckedChangeListener { _, on -> SoundFX.stoneSoundEnabled = on; getSharedPreferences("gomoku_settings", Context.MODE_PRIVATE).edit().putBoolean("stone_sound", on).apply() } }; sRow.addView(sSw); card.addView(sRow); val vRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 12) }; vRow.addView(TextView(this).apply { text = "\uD83C\uDFA4 \u64AD\u62A5\u97F3\u8272"; textSize = 16f; setTextColor(Color.parseColor("#4A3728")) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); val vSw = Switch(this).apply { text = if (SoundFX.voiceStyle == 1) "\u5E7D\u9ED8" else "\u6807\u51C6"; isChecked = SoundFX.voiceStyle == 1; setOnCheckedChangeListener { _, on -> SoundFX.voiceStyle = if (on) 1 else 0; text = if (on) "\u5E7D\u9ED8" else "\u6807\u51C6"; getSharedPreferences("gomoku_settings", Context.MODE_PRIVATE).edit().putInt("voice_style", SoundFX.voiceStyle).apply() } }; vRow.addView(vSw); card.addView(vRow); val oRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 20) }; oRow.addView(TextView(this).apply { text = "\uD83D\uDD04 \u68CB\u5B50\u987A\u5E8F"; textSize = 16f; setTextColor(Color.parseColor("#4A3728")) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); val oSw = Switch(this).apply { isChecked = gomokuView?.showPieceOrder ?: false; setOnCheckedChangeListener { _, on -> gomokuView?.showPieceOrder = on; getMainActivity?.invoke()?.syncPieceOrder(on); getSharedPreferences("gomoku_settings", Context.MODE_PRIVATE).edit().putBoolean("piece_order", on).apply(); gomokuView?.invalidate() } }; oRow.addView(oSw); card.addView(oRow); val cb = Button(this).apply { text = "\u5173\u95ED"; setTextColor(Color.WHITE); textSize = 16f; background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 20f; setColor(Color.parseColor("#8D6E63")) }; setPadding(40, 14, 40, 14); setOnClickListener { overlay.animate().alpha(0f).setDuration(200).withEndAction { container.removeView(overlay) }.start() } }; card.addView(cb); overlay.addView(card, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.6f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }); overlay.setOnClickListener { overlay.animate().alpha(0f).setDuration(200).withEndAction { container.removeView(overlay) }.start() }; container.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }

    // 返回键 / 侧滑退出：两个窗口是两个 task，必须一起关，否则另一屏停在“等待双屏就绪”
    override fun onBackPressed() {
        instance = null
        BattleDisplays.closeAll()
        super.onBackPressed()
    }
    override fun onUserLeaveHint() { super.onUserLeaveHint() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
