package com.gomoku.dualscreen

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var game: GomokuGame
    private lateinit var gomokuView: GomokuView
    private lateinit var statusText: TextView
    private lateinit var countdownText: TextView
    private lateinit var startBtn: Button
    private lateinit var undoBtn: Button
    private var gamePresentation: GamePresentation? = null
    private lateinit var displayManager: DisplayManager
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var secondsLeft = 60
    @JvmField var tauntCountBlack = 0
    @JvmField var tauntCountWhite = 0
    private var tauntCountText: TextView? = null
    private var pauseBtnBlack: Button? = null
    private var animator: ValueAnimator? = null
    private var myPlayer = GomokuGame.PLAYER_BLACK
    private var isAutoMode = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // V4.0: 双屏防呆 — 强制迁回主屏 (Display 0)
        // 若用户从副屏点击图标启动，系统会将 MainActivity 分配到副屏
        // 在此极早期拦截，销毁副屏实例并在主屏重建，确保主屏=黑方、副屏=白方
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
            return  // 关键：跳过后续所有初始化，避免 lateinit 未初始化崩溃
        }

        // --- 以下仅在主屏 (Display 0) 执行 ---
        // MainActivity 始终在主屏 → 始终为黑方
        myPlayer = GomokuGame.PLAYER_BLACK
        GameState.game = GomokuGame()
        game = GameState.game
        GameState.mainActivity = this
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        prefs = getSharedPreferences("gomoku_settings", MODE_PRIVATE)
        SoundFX.stoneSoundEnabled = prefs.getBoolean("stone_sound", true)
        SoundFX.voiceStyle = prefs.getInt("voice_style", 0)
        tauntCountBlack = 1; tauntCountWhite = 1
        initMainUI()
        updateTauntDisplay()
        gomokuView.showPieceOrder = prefs.getBoolean("piece_order", false)
        launchWhiteScreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        initMainUI()
        launchWhiteScreen()
        if (game.isActive) startRemindTimers()
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun initMainUI() {
        val root = LinearLayout(this).apply {
            orientation = if (isLandscape()) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DEB887"))
        }
        gomokuView = GomokuView(this).apply {
            playerPerspective = myPlayer
            game = this@MainActivity.game
            onConfirmPlace = { row, col -> handlePiecePlaced(row, col) }
        }

        val smallSize = (resources.displayMetrics.density * 60).toInt()
        val leftPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(12, 12, 4, 12)
        }
        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.kemi_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener {
                gomokuView.startFlowerAnimation()
                SoundFX.playCheerfulSound()
            }
        }
        leftPanel.addView(logoImg, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 12 })
        val hurryBtn = create3DButton("\u23F0\n\u50AC", "#FF9800", "#E65100", smallSize)
        hurryBtn.setOnClickListener {
            if (!game.isActive || game.isGameOver) { showMsgToPlayer(myPlayer, "\u6E38\u620F\u672A\u5F00\u59CB"); return@setOnClickListener }
            if (game.isPaused && game.pausedByPlayer != myPlayer) { showMsgToPlayer(myPlayer, "\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u8BF7\u7B49\u5F85\u5BF9\u65B9\u6062\u590D"); return@setOnClickListener }
            if (game.currentPlayer == myPlayer) { showMsgToPlayer(myPlayer, "\u8F6E\u5230\u4F60\u4E86\uFF0C\u4E0D\u80FD\u50AC\u81EA\u5DF1"); return@setOnClickListener }
            val rid = if (myPlayer == GomokuGame.PLAYER_BLACK) R.raw.hurry_black else R.raw.hurry_white
            SoundFX.playVoice(this, rid)
            gamePresentation?.shakeScreen()
        }
        val tauntBtn = create3DButton("\uD83D\uDE08\n\u6311\u8845", "#9C27B0", "#4A148C", smallSize)
        tauntBtn.setOnClickListener {
            val myCount = if (myPlayer == GomokuGame.PLAYER_BLACK) tauntCountBlack else tauntCountWhite
            if (myCount <= 0) { showMsgToPlayer(myPlayer, "\u6311\u8845\u6B21\u6570\u4E0D\u8DB3\uFF0C\u8D62\u5F97\u6E38\u620F\u83B7\u53D6"); return@setOnClickListener }
            if (myPlayer == GomokuGame.PLAYER_BLACK) tauntCountBlack-- else tauntCountWhite--
            updateTauntDisplay()
            val rid = if (myPlayer == GomokuGame.PLAYER_BLACK) R.raw.taunt_black else R.raw.taunt_white
            SoundFX.playVoice(this, rid)
            gamePresentation?.startEggAnimation()
            startEggAnimLoop()
            SoundFX.playEggSplat()
        }
        leftPanel.addView(hurryBtn, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 16 })
        leftPanel.addView(tauntBtn, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 4 })

        tauntCountText = TextView(this).apply {
            text = "\u00D70"; textSize = 11f; setTextColor(Color.parseColor("#9C27B0"))
            gravity = Gravity.CENTER
        }
        leftPanel.addView(tauntCountText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })

        val pauseBtn = create3DButton("\u23F8\n\u6682\u505C", "#607D8B", "#37474F", smallSize)
        pauseBtnBlack = pauseBtn
        var pauseBtnRef: Button? = null; pauseBtnRef = pauseBtn
        pauseBtn.setOnClickListener {
            if (!game.isActive || game.isGameOver) { showMsgToPlayer(myPlayer, "\u6E38\u620F\u672A\u5F00\u59CB"); return@setOnClickListener }
            if (game.isPaused) {
                if (game.pausedByPlayer == myPlayer) {
                    game.isPaused = false; game.pausedByPlayer = GomokuGame.EMPTY
                    pauseBtnRef?.let { b ->
                        b.text = "\u23F8\n\u6682\u505C"; b.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL; setSize(smallSize, smallSize)
                            colors = intArrayOf(Color.parseColor("#607D8B"), Color.parseColor("#37474F"))
                            orientation = GradientDrawable.Orientation.TOP_BOTTOM
                            setStroke(3, Color.parseColor("#CCFFFFFF"))
                        }
                    }
                    gamePresentation?.updatePauseBtn(false)
                    updateStatusDisplay(); gamePresentation?.updateStatusText()
                    startRemindTimers()
                } else {
                    showMsgToPlayer(myPlayer, "\u7B49\u5F85\u5BF9\u65B9\u6062\u590D\u6E38\u620F")
                }
            } else {
                val myPauseCount = if (myPlayer == GomokuGame.PLAYER_BLACK) game.pauseCountBlack else game.pauseCountWhite
                if (myPauseCount <= 0) { showMsgToPlayer(myPlayer, "\u6682\u505C\u6B21\u6570\u5DF2\u7528\u5B8C"); return@setOnClickListener }
                if (myPlayer == GomokuGame.PLAYER_BLACK) game.pauseCountBlack-- else game.pauseCountWhite--
                game.isPaused = true; game.pausedByPlayer = myPlayer
                pauseBtnRef?.let { b ->
                    b.text = "\u23F8\n\u6682\u505C\u4E2D"; b.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setSize(smallSize, smallSize)
                        colors = intArrayOf(Color.parseColor("#FF8F00"), Color.parseColor("#E65100"))
                        orientation = GradientDrawable.Orientation.TOP_BOTTOM
                        setStroke(3, Color.parseColor("#CCFFFFFF"))
                    }
                }
                gamePresentation?.updatePauseBtn(true)
                stopRemindTimers(); updateStatusDisplay(); gamePresentation?.updateStatusText()
            }
        }
        leftPanel.addView(pauseBtn, LinearLayout.LayoutParams(smallSize, smallSize).apply { bottomMargin = 4 })
        root.addView(leftPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val boardLp = if (isLandscape()) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(gomokuView, boardLp)

        val panel = LinearLayout(this).apply { orientation = if (isLandscape()) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(8, 12, 8, 8) }
        statusText = TextView(this).apply { text = "\u26AB\u9ED1\u65B9\n\u7B49\u5F85\u5F00\u59CB..."; textSize = 14f; setTextColor(Color.parseColor("#4A3728")); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4) }
        panel.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = if (isLandscape()) 8 else 0; rightMargin = if (isLandscape()) 0 else 12 })
        countdownText = TextView(this).apply { text = ""; textSize = 18f; setTextColor(Color.parseColor("#D84315")); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4) }
        panel.addView(countdownText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = if (isLandscape()) 12 else 0; rightMargin = if (isLandscape()) 0 else 12 })
        val btnSize = (resources.displayMetrics.density * 120).toInt()
        val btnMargin = if (isLandscape()) 24 else 0; val btnMargin2 = if (isLandscape()) 0 else 16
        startBtn = create3DButton("\u25B6\n\u5F00\u59CB", "#4CAF50", "#2E7D32", btnSize)
        startBtn.setOnClickListener { if (game.isPaused) { showMsgToPlayer(myPlayer, "\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u8BF7\u5148\u6062\u590D\u6E38\u620F"); return@setOnClickListener }; onStartOrRestart(myPlayer) }
        undoBtn = create3DButton("\u21A9\n\u6094\u68CB", "#78909C", "#455A64", btnSize)
        undoBtn.setOnClickListener { if (game.isPaused) { showMsgToPlayer(myPlayer, "\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u65E0\u6CD5\u6094\u68CB"); return@setOnClickListener }; requestUndo(myPlayer) }
        panel.addView(startBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = btnMargin; rightMargin = btnMargin2 })
        panel.addView(undoBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = 8; rightMargin = btnMargin2 })
        val exitBtn = create3DButton("\uD83D\uDEAA\n\u9000\u51FA", "#C62828", "#8E0000", btnSize)
        exitBtn.setOnClickListener { if (game.isPaused && game.pausedByPlayer != myPlayer) { showMsgToPlayer(myPlayer, "\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u8BF7\u7B49\u5F85\u5BF9\u65B9\u6062\u590D"); return@setOnClickListener }; requestExit() }
        panel.addView(exitBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = 12; rightMargin = btnMargin2 })
        val verText = TextView(this).apply { text = "V4.5"; textSize = 10f; setTextColor(Color.parseColor("#998B7355")); gravity = Gravity.CENTER }
        panel.addView(verText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val settingsBtn = TextView(this).apply { text = "\u2261"; textSize = 22f; setTextColor(Color.parseColor("#998B7355")); gravity = Gravity.CENTER; setPadding(12, 0, 12, 4); setOnClickListener { if (game.isPaused && game.pausedByPlayer != myPlayer) { showMsgToPlayer(myPlayer, "\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u8BF7\u7B49\u5F85\u5BF9\u65B9\u6062\u590D"); return@setOnClickListener }; showSettingsDialog() } }
        panel.addView(settingsBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val panelLp = if (isLandscape()) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(panel, panelLp)
        setContentView(root)
    }

    private fun create3DButton(text: String, colorTop: String, colorBottom: String, size: Int) = Button(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(8, 8, 8, 8); setBackgroundColor(Color.TRANSPARENT)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(size, size); colors = intArrayOf(Color.parseColor(colorTop), Color.parseColor(colorBottom)); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) }
        elevation = 12f; isAllCaps = false
    }

    private fun updateButtonState() { if (game.isActive && !game.isGameOver) setStartBtn("\uD83D\uDD04\n\u91CD\u65B0\u5F00\u59CB", "#FF8F00", "#E65100") else setStartBtn("\u25B6\n\u5F00\u59CB", "#4CAF50", "#2E7D32") }
    fun startEggOnMain() { gomokuView.startEggAnimation(); startEggAnimLoop(); SoundFX.playEggSplat() }
    fun shakeMainForWhite() { shakeMainScreen() }
    fun syncPieceOrder(on: Boolean) { gomokuView.showPieceOrder = on; gomokuView.invalidate() }
    fun restartCountdown() { startRemindTimers() }
    fun updatePauseBtnForWhite(paused: Boolean) {
        pauseBtnBlack?.let { if (paused) { it.text = "\u23F8\n\u6682\u505C\u4E2D"; it.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(it.width, it.height); colors = intArrayOf(Color.parseColor("#FF8F00"), Color.parseColor("#E65100")); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } } else { it.text = "\u23F8\n\u6682\u505C"; it.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(it.width, it.height); colors = intArrayOf(Color.parseColor("#607D8B"), Color.parseColor("#37474F")); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } } }
    }
    private fun setStartBtn(text: String, top: String, bottom: String) { startBtn.text = text; startBtn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; colors = intArrayOf(Color.parseColor(top), Color.parseColor(bottom)); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } }

    private fun launchWhiteScreen() {
        try { gamePresentation?.finish() } catch (_: Exception) {}
        gamePresentation = null
        val myDisplayId = display?.displayId ?: 0
        for (display in displayManager.displays) {
            if (display.displayId != myDisplayId && display.isValid) {
                // V4.0: MainActivity 始终在主屏(黑方), GP 始终在副屏 → 白方
                GamePresentation.sharedPerspective = GomokuGame.PLAYER_WHITE
                val otherPerspective = GomokuGame.PLAYER_WHITE
                GamePresentation.sharedGame = game
                val intent = Intent(this, GamePresentation::class.java)
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    try {
                        val options = android.app.ActivityOptions.makeBasic()
                        val method = options.javaClass.getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
                        method.invoke(options, display.displayId)
                        startActivity(intent, options.toBundle())
                    } catch (_: Exception) { startActivity(intent) }
                } else { startActivity(intent) }
                handler.postDelayed({
                    GamePresentation.instance?.let { pres ->
                        pres.onPiecePlaced = { r, c -> runOnUiThread { handlePiecePlaced(r, c) } }
                        pres.onStartOrRestart = { runOnUiThread { onStartOrRestart(otherPerspective) } }
                        pres.onUndoRequest = { runOnUiThread { requestUndo(otherPerspective) } }
                        pres.getMainActivity = { this@MainActivity }
                        pres.onAutoToggle = { runOnUiThread { toggleAutoMode() } }
                        gamePresentation = pres
                        pres.updateAutoButton(isAutoMode)
                        updateTauntDisplay()
                        updateStatusDisplay()
                    }
                }, 600)
                return
            }
        }
    }

    private fun onStartOrRestart(player: Int) { if (game.isPaused) { showMsgToPlayer(player, "\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u8BF7\u5148\u6062\u590D\u6E38\u620F"); return }; if (!game.isActive) { game.startGame(player); onGameStarted(player) } else if (!game.isGameOver) requestRestart(player) else doRestart(player) }
    private fun onGameStarted(player: Int) { updateTauntDisplay(); gomokuView.clearPreview(); gomokuView.invalidate(); gamePresentation?.refreshView(); updateButtonState(); updateStatusDisplay(); gamePresentation?.updateButtonState(); startRemindTimers(); SoundFX.playVoice(this, if (player == GomokuGame.PLAYER_BLACK) R.raw.your_turn_black else R.raw.your_turn_white); if (isAutoMode && game.currentPlayer == GomokuGame.PLAYER_WHITE) scheduleAIMove() }
    private fun requestRestart(player: Int) { if (player == GomokuGame.PLAYER_BLACK) { gamePresentation?.showRestartRequestDialog(game.getPlayerName(player)) { a -> if (a) runOnUiThread { doRestart(player) } else runOnUiThread { showMsgToPlayer(player, "\u5BF9\u65B9\u62D2\u7EDD\u91CD\u65B0\u5F00\u59CB") } } } else { showRestartRequestDialog(game.getPlayerName(player)) { a -> if (a) doRestart(player) else showMsgToPlayer(player, "\u5BF9\u65B9\u62D2\u7EDD\u91CD\u65B0\u5F00\u59CB") } } }
    fun doRestart(firstPlayer: Int) { animator?.cancel(); stopRemindTimers(); game.startGame(firstPlayer); updateTauntDisplay(); pauseBtnBlack?.let { it.text = "\u23F8\n\u6682\u505C"; it.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(it.width, it.height); colors = intArrayOf(Color.parseColor("#607D8B"), Color.parseColor("#37474F")); orientation = GradientDrawable.Orientation.TOP_BOTTOM; setStroke(3, Color.parseColor("#CCFFFFFF")) } }; gamePresentation?.updatePauseBtn(false); gomokuView.clearAllAnimations(); gamePresentation?.clearAllAnimations(); gomokuView.clearPreview(); gomokuView.clearMessage(); gomokuView.invalidate(); gamePresentation?.clearPreview(); gamePresentation?.clearMessage(); gamePresentation?.refreshView(); updateButtonState(); updateStatusDisplay(); gamePresentation?.updateButtonState(); startRemindTimers(); SoundFX.playVoice(this, if (firstPlayer == GomokuGame.PLAYER_BLACK) R.raw.your_turn_black else R.raw.your_turn_white); if (isAutoMode && game.currentPlayer == GomokuGame.PLAYER_WHITE) scheduleAIMove() }
    fun showRestartRequestDialog(requesterName: String, callback: (Boolean) -> Unit) { AlertDialog.Builder(this).setTitle("\u91CD\u65B0\u5F00\u59CB\u8BF7\u6C42").setMessage("${requesterName}\u8BF7\u6C42\u91CD\u65B0\u5F00\u59CB\uFF0C\u662F\u5426\u540C\u610F\uFF1F").setPositiveButton("\u540C\u610F") { _, _ -> callback(true) }.setNegativeButton("\u4E0D\u540C\u610F") { _, _ -> callback(false) }.setCancelable(false).show() }
    fun showUndoRequestDialog(requesterName: String, callback: (Boolean) -> Unit) { AlertDialog.Builder(this).setTitle("\u6094\u68CB\u8BF7\u6C42").setMessage("${requesterName}\u8BF7\u6C42\u6094\u68CB\uFF0C\u662F\u5426\u540C\u610F\uFF1F").setPositiveButton("\u540C\u610F") { _, _ -> callback(true) }.setNegativeButton("\u4E0D\u540C\u610F") { _, _ -> callback(false) }.setCancelable(false).show() }
    private fun shakeMainScreen() { val v = gomokuView; ValueAnimator.ofFloat(0f, 16f, -16f, 12f, -12f, 10f, -10f, 8f, -8f, 5f, -5f, 3f, -3f, 0f).apply { duration = 2000; addUpdateListener { v.translationX = it.animatedValue as Float }; start() } }
    private fun requestExit() { if (game.isActive && !game.isGameOver) { gamePresentation?.showExitRequestDialog { a -> if (a) runOnUiThread { exitApp() } else runOnUiThread { showMsgToPlayer(myPlayer, "\u5BF9\u65B9\u62D2\u7EDD\u9000\u51FA") } } } else { exitApp() } }
    private fun exitApp() { stopRemindTimers(); animator?.cancel(); animator = null; countdownRunnable?.let { handler.removeCallbacks(it) }; countdownRunnable = null; gomokuView.clearAllAnimations(); try { gamePresentation?.clearAllAnimations() } catch (_: Exception) {}; try { gamePresentation?.finish() } catch (_: Exception) {}; gamePresentation = null; GamePresentation.instance = null; handler.postDelayed({ finishAffinity() }, 150) }
    private fun requestUndo(player: Int) { if (!game.isActive || game.isGameOver) { showMsg("\u65E0\u6CD5\u6094\u68CB"); return }; if (game.isPaused) { showMsgToPlayer(player, "\u6E38\u620F\u6682\u505C\u4E2D\uFF0C\u65E0\u6CD5\u6094\u68CB"); return }; if (!game.canUndo(player)) { showMsg("\u4F60\u8FD8\u6CA1\u6709\u843D\u5B50"); return }; val oppScreen = if (player == GomokuGame.PLAYER_BLACK) gamePresentation else null; val cb: (Boolean) -> Unit = { a -> if (a) doUndo(player) else { showMsgToPlayer(player, "\u5BF9\u65B9\u62D2\u7EDD\u4E86\u6094\u68CB\u8BF7\u6C42"); SoundFX.playVoice(this, R.raw.reject_undo) } }; if (player == GomokuGame.PLAYER_BLACK && oppScreen != null) { oppScreen.showUndoRequestDialog(game.getPlayerName(player), cb) } else if (player == GomokuGame.PLAYER_WHITE && oppScreen == null) { showUndoRequestDialog(game.getPlayerName(player), cb) } else { doUndo(player) } }
    private fun doUndo(player: Int) { val r = game.undo(player); if (r.success) { SoundFX.playVoice(this, R.raw.taunt_undo); gomokuView.clearPreview(); gomokuView.clearMessage(); gomokuView.invalidate(); gamePresentation?.clearMessage(); gamePresentation?.refreshView(); updateStatusDisplay(); startRemindTimers() } }

    private fun showPopupMessage(msg: String) { val root = findViewById<ViewGroup>(android.R.id.content) ?: return; for (i in root.childCount - 1 downTo 0) { if (root.getChildAt(i)?.tag == "popup_msg") root.removeViewAt(i) }; val c = FrameLayout(this).apply { tag = "popup_msg"; setOnClickListener { } }; val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(40, 28, 40, 28); background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 22f; setColor(Color.parseColor("#EE2D2D2D")); setStroke(3, Color.parseColor("#CCFFD700")) }; alpha = 0f; animate().alpha(1f).setDuration(250).start() }; card.addView(TextView(this).apply { text = msg; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; setShadowLayer(4f, 0f, 2f, Color.parseColor("#AA000000")) }); c.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }); root.addView(c, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); c.setOnClickListener { card.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(c) }.start() }; handler.postDelayed({ card.animate().alpha(0f).setDuration(300).withEndAction { (c.parent as? ViewGroup)?.removeView(c) }.start() }, 2200) }
    private fun showMsg(m: String) { showPopupMessage(m); gamePresentation?.showPopupMessage(m) }
    private fun showMsgToPlayer(player: Int, m: String) { if (player == myPlayer) showPopupMessage(m) else gamePresentation?.showPopupMessage(m) }

    private fun handlePiecePlaced(row: Int, col: Int) { val r = game.placePiece(row, col, game.currentPlayer); if (!r.success) { showMsg(r.message); return }; SoundFX.playStoneSound(); gomokuView.clearPreview(); gomokuView.clearMessage(); gomokuView.invalidate(); gamePresentation?.clearMessage(); gamePresentation?.refreshView(); updateStatusDisplay(); if (r.gameOver) { if (r.winner == GomokuGame.PLAYER_BLACK) tauntCountBlack += 3 else if (r.winner == GomokuGame.PLAYER_WHITE) tauntCountWhite += 3; updateTauntDisplay(); stopRemindTimers(); updateButtonState(); gamePresentation?.updateButtonState(); if (r.winner == GomokuGame.PLAYER_BLACK) { gomokuView.startWinAnimation(); gamePresentation?.startLoseAnimation(); SoundFX.playVoice(this, R.raw.win_black) } else if (r.winner == GomokuGame.PLAYER_WHITE) { gomokuView.startLoseAnimation(); gamePresentation?.startWinAnimation(); SoundFX.playVoice(this, R.raw.win_white) }; startAnimationLoop() } else { startRemindTimers(); if (isAutoMode && game.currentPlayer == GomokuGame.PLAYER_WHITE) scheduleAIMove() } }
    fun updateStatusDisplay() { val g = game; statusText.text = if (!g.isActive) "\u26AB\u9ED1\u65B9\n\u7B49\u5F85\u5F00\u59CB..." else if (g.isGameOver) { if (g.winner == myPlayer) "\uD83C\uDF89\u4F60\u8D62\u4E86\uFF01" else if (g.winner == GomokuGame.EMPTY) "\uD83E\uDD1D\u5E73\u5C40" else "\uD83D\uDE1E\u4F60\u8F93\u4E86" } else if (g.isPaused) { if (g.pausedByPlayer == myPlayer) "\u26AB\u9ED1\u65B9\n\u23F8 \u4F60\u6682\u505C\u4E86\u6E38\u620F" else "\u26AB\u9ED1\u65B9\n\u23F8 \u5BF9\u65B9\u6682\u505C\u4E2D" } else { if (g.currentPlayer == myPlayer) "\u26AB\u9ED1\u65B9\n\uD83D\uDC49\u8F6E\u5230\u4F60\u4E86" else "\u26AB\u9ED1\u65B9\n\u23F3\u7B49\u5F85\u5BF9\u65B9" }; gamePresentation?.updateStatusText() }
    private fun startAnimationLoop() { animator?.cancel(); animator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 33; repeatCount = ValueAnimator.INFINITE; addUpdateListener { val a1 = gomokuView.updateAnimation(); val a2 = gamePresentation?.updateAnimation() ?: false; if (!a1 && !a2) cancel() }; start() } }
    private fun startEggAnimLoop() { animator?.cancel(); animator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 80; repeatCount = ValueAnimator.INFINITE; addUpdateListener { val a1 = gomokuView.updateAnimation(); val a2 = gamePresentation?.updateAnimation() ?: false; if (!a1 && !a2) cancel() }; start() } }
    private fun startRemindTimers() { stopRemindTimers(); if (!game.isActive || game.isGameOver) return; if (game.isPaused) { countdownText.text = "\u23F8"; gamePresentation?.setCountdown(-2); return }; secondsLeft = 60; val cur = game.currentPlayer; val myTurn = cur == myPlayer; if (myTurn) updateCountdown() else countdownText.text = ""; gamePresentation?.setCountdown(if (cur == GomokuGame.PLAYER_WHITE) secondsLeft else -1); countdownRunnable = object : Runnable { override fun run() { if (!game.isActive || game.isGameOver) { stopRemindTimers(); return }; if (game.isPaused) { countdownText.text = "\u23F8"; gamePresentation?.setCountdown(-2); return }; if (game.currentPlayer != cur) { startRemindTimers(); return }; secondsLeft--; if (secondsLeft <= 0) { if (cur == myPlayer) gomokuView.startEggAnimation() else gamePresentation?.startEggAnimation(); startEggAnimLoop(); SoundFX.playEggSplat(); SoundFX.playVoice(this@MainActivity, if (cur == GomokuGame.PLAYER_BLACK) R.raw.too_slow_black else R.raw.too_slow_white); secondsLeft = 60; if (cur == myPlayer) updateCountdown(); gamePresentation?.setCountdown(if (cur == GomokuGame.PLAYER_WHITE) secondsLeft else -1); handler.postDelayed(this, 1000); return }; if (cur == myPlayer) updateCountdown(); gamePresentation?.setCountdown(if (cur == GomokuGame.PLAYER_WHITE) secondsLeft else -1); if (secondsLeft == 30) { SoundFX.playVoice(this@MainActivity, if (cur == GomokuGame.PLAYER_BLACK) R.raw.wait_30s_black else R.raw.wait_30s_white); if (cur == myPlayer) shakeMainScreen() else gamePresentation?.shakeScreen() }; handler.postDelayed(this, 1000) } }; handler.postDelayed(countdownRunnable!!, 1000) }
    fun updateTauntDisplay() { val c = if (myPlayer == GomokuGame.PLAYER_BLACK) tauntCountBlack else tauntCountWhite; tauntCountText?.text = "\u00D7$c"; gamePresentation?.setTauntCount(if (myPlayer == GomokuGame.PLAYER_BLACK) tauntCountWhite else tauntCountBlack) }

    fun toggleAutoMode() {
        isAutoMode = !isAutoMode
        gamePresentation?.updateAutoButton(isAutoMode)
        if (isAutoMode && game.isActive && !game.isGameOver && !game.isPaused) {
            if (game.currentPlayer == GomokuGame.PLAYER_WHITE) scheduleAIMove()
        }
    }

    private fun scheduleAIMove() {
        handler.postDelayed({
            if (!isAutoMode || !game.isActive || game.isGameOver || game.isPaused) return@postDelayed
            if (game.currentPlayer != GomokuGame.PLAYER_WHITE) return@postDelayed
            Thread {
                // 复制棋盘——防止 AI 搜索时修改原棋盘导致 UI 渲染异常
                val copy = Array(15) { r -> IntArray(15) { c -> game.board[r][c] } }
                val move = GomokuAI.findBestMove(copy, GomokuGame.PLAYER_WHITE)
                handler.post {
                    if (move != null && isAutoMode && game.isActive && !game.isGameOver
                        && !game.isPaused && game.currentPlayer == GomokuGame.PLAYER_WHITE) {
                        handlePiecePlaced(move.first, move.second)
                    }
                }
            }.start()
        }, 400)
    }

    private fun updateCountdown() { countdownText.text = "\u23F1${secondsLeft}s" }
    private fun stopRemindTimers() { countdownRunnable?.let { handler.removeCallbacks(it) }; countdownRunnable = null; if (::countdownText.isInitialized) countdownText.text = ""; gamePresentation?.setCountdown(-1) }

    private fun showSettingsDialog() { val root = findViewById<ViewGroup>(android.R.id.content) ?: return; val overlay = FrameLayout(this).apply { tag = "settings_dlg"; setBackgroundColor(Color.parseColor("#80000000")) }; val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(36, 30, 36, 24); background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 22f; setColor(Color.parseColor("#F5F0E8")); setStroke(3, Color.parseColor("#FF8F00")) }; alpha = 0f; animate().alpha(1f).setDuration(250).start() }; card.addView(TextView(this).apply { text = "\u2699 \u6E38\u620F\u8BBE\u7F6E"; textSize = 20f; setTextColor(Color.parseColor("#4A3728")); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, 20) }); card.addView(View(this).apply { setBackgroundColor(Color.parseColor("#DDD7C8")); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { bottomMargin = 16 } }); val sRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 12) }; sRow.addView(TextView(this).apply { text = "\uD83D\uDD0A \u843D\u5B50\u97F3\u6548"; textSize = 16f; setTextColor(Color.parseColor("#4A3728")) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); val sSw = Switch(this).apply { isChecked = SoundFX.stoneSoundEnabled; setOnCheckedChangeListener { _, on -> SoundFX.stoneSoundEnabled = on; prefs.edit().putBoolean("stone_sound", on).apply() } }; sRow.addView(sSw); card.addView(sRow); val vRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 12) }; vRow.addView(TextView(this).apply { text = "\uD83C\uDFA4 \u64AD\u62A5\u97F3\u8272"; textSize = 16f; setTextColor(Color.parseColor("#4A3728")) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); val vSw = Switch(this).apply { text = if (SoundFX.voiceStyle == 1) "\u5E7D\u9ED8" else "\u6807\u51C6"; isChecked = SoundFX.voiceStyle == 1; setOnCheckedChangeListener { _, on -> SoundFX.voiceStyle = if (on) 1 else 0; text = if (on) "\u5E7D\u9ED8" else "\u6807\u51C6"; prefs.edit().putInt("voice_style", SoundFX.voiceStyle).apply() } }; vRow.addView(vSw); card.addView(vRow); val oRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 20) }; oRow.addView(TextView(this).apply { text = "\uD83D\uDD04 \u68CB\u5B50\u987A\u5E8F"; textSize = 16f; setTextColor(Color.parseColor("#4A3728")) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); val oSw = Switch(this).apply { isChecked = gomokuView.showPieceOrder; setOnCheckedChangeListener { _, on -> gomokuView.showPieceOrder = on; gamePresentation?.updatePieceOrder(on); prefs.edit().putBoolean("piece_order", on).apply(); gomokuView.invalidate(); gamePresentation?.refreshView() } }; oRow.addView(oSw); card.addView(oRow); val cb = Button(this).apply { text = "\u5173\u95ED"; setTextColor(Color.WHITE); textSize = 16f; background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 20f; setColor(Color.parseColor("#8D6E63")) }; setPadding(40, 14, 40, 14); setOnClickListener { overlay.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(overlay) }.start() } }; card.addView(cb); overlay.addView(card, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.6f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }); overlay.setOnClickListener { overlay.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(overlay) }.start() }; root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }

    // V4.1: 返回键 + Home 键都退出全部界面（主屏+副屏）
    override fun onBackPressed() { finishAffinity() }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); finishAffinity() }
    override fun onRestart() { super.onRestart(); if (gamePresentation == null) launchWhiteScreen() }
    override fun onDestroy() { if (::gomokuView.isInitialized) { stopRemindTimers(); animator?.cancel() }; SoundFX.release(); try { gamePresentation?.finish() } catch (_: Exception) {}; gamePresentation = null; GamePresentation.instance = null; super.onDestroy() }
}
