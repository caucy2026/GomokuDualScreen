package com.gomoku.dualscreen

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI

/**
 * 五子棋棋盘自定义View - 3D立体棋子 + 最大化棋盘
 */
class GomokuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var playerPerspective: Int = GomokuGame.PLAYER_BLACK
    var game: GomokuGame? = null
    var onPiecePlaced: ((row: Int, col: Int) -> Unit)? = null
    var onConfirmPlace: ((row: Int, col: Int) -> Unit)? = null
    var message: String = ""

    // 预览状态
    private var pendingRow = -1
    private var pendingCol = -1

    // 动画状态
    private var animState = 0         // 0=none, 1=win, 2=lose
    private var animProgress = 0f
    private val particles = mutableListOf<Particle>()

    // 棋子顺序显示
    var showPieceOrder = false

    // 动画防抖
    private var lastAnimStartMs = 0L

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#50000000"); style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A3728"); style = Paint.Style.STROKE; strokeWidth = 1.8f
    }
    private val starPtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A3728"); style = Paint.Style.FILL
    }
    private val lastMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val infoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A3728"); textSize = 30f; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED; textSize = 26f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    private var cellSize = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f
    private var boardPixelW = 0f
    private var boardPixelH = 0f

    private val starPoints = arrayOf(
        intArrayOf(3, 3), intArrayOf(3, 7), intArrayOf(3, 11),
        intArrayOf(7, 3), intArrayOf(7, 7), intArrayOf(7, 11),
        intArrayOf(11, 3), intArrayOf(11, 7), intArrayOf(11, 11)
    )

    data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                        var life: Float, val maxLife: Float, val color: Int, var size: Float)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()

        // 棋盘最大化，四周留舒适边距
        val margin = 24f
        boardPixelW = w - margin * 2; boardPixelH = h - margin * 2
        val gridCount = (GomokuGame.BOARD_SIZE - 1).toFloat()
        cellSize = minOf(boardPixelW, boardPixelH) / gridCount
        val actualW = cellSize * gridCount; val actualH = cellSize * gridCount
        boardOffsetX = (w - actualW) / 2f; boardOffsetY = (h - actualH) / 2f

        // 背景
        canvas.drawPaint(Paint().apply {
            shader = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.7f,
                intArrayOf(Color.parseColor("#F5DEB3"), Color.parseColor("#DEB887"), Color.parseColor("#C4A265")),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        })

        // 木板阴影
        val pad = 10f
        canvas.drawRoundRect(boardOffsetX - pad, boardOffsetY - pad,
            boardOffsetX + actualW + pad, boardOffsetY + actualH + pad,
            6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#35000000"); style = Paint.Style.FILL
            })

        // 棋盘面板
        canvas.drawRoundRect(boardOffsetX - pad / 2, boardOffsetY - pad / 2,
            boardOffsetX + actualW + pad / 2, boardOffsetY + actualH + pad / 2,
            5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(boardOffsetX, boardOffsetY, boardOffsetX + actualW, boardOffsetY + actualH,
                    intArrayOf(Color.parseColor("#E8D5A3"), Color.parseColor("#D4B896"), Color.parseColor("#C9A96E")),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                style = Paint.Style.FILL
            })

        // 网格线
        for (i in 0 until GomokuGame.BOARD_SIZE) {
            val p = i * cellSize
            canvas.drawLine(boardOffsetX, boardOffsetY + p, boardOffsetX + actualW, boardOffsetY + p, linePaint)
            canvas.drawLine(boardOffsetX + p, boardOffsetY, boardOffsetX + p, boardOffsetY + actualH, linePaint)
        }

        // 星位
        for ((r, c) in starPoints) {
            canvas.drawCircle(boardOffsetX + c * cellSize, boardOffsetY + r * cellSize, cellSize * 0.1f, starPtPaint)
        }

        // 棋子
        val g = game ?: return
        for (r in 0 until GomokuGame.BOARD_SIZE) {
            for (c in 0 until GomokuGame.BOARD_SIZE) {
                if (g.board[r][c] != GomokuGame.EMPTY) {
                    val cx = boardOffsetX + c * cellSize
                    val cy = boardOffsetY + r * cellSize
                    drawPiece(canvas, cx, cy, g.board[r][c], r == g.lastRow && c == g.lastCol, 1f, r, c)
                }
            }
        }

        // 预览棋子（半透明 + 蓝色勾选圈）
        if (pendingRow >= 0 && pendingCol >= 0 && g.board[pendingRow][pendingCol] == GomokuGame.EMPTY) {
            val pcx = boardOffsetX + pendingCol * cellSize
            val pcy = boardOffsetY + pendingRow * cellSize
            val pr = cellSize * 0.40f
            // 半透明预览棋子
            val previewPlayer = playerPerspective
            drawPiece(canvas, pcx, pcy, previewPlayer, false, 0.45f)
            // 蓝色勾选圈
            val checkR = pr * 0.6f
            canvas.drawCircle(pcx, pcy, checkR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#AA2196F3"); style = Paint.Style.FILL
            })
            canvas.drawCircle(pcx, pcy, checkR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f
            })
            // 白色勾号 ✓
            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val s = checkR * 0.5f
            val path = android.graphics.Path().apply {
                moveTo(pcx - s * 0.5f, pcy)
                lineTo(pcx - s * 0.1f, pcy + s * 0.55f)
                lineTo(pcx + s * 0.6f, pcy - s * 0.4f)
            }
            canvas.drawPath(path, checkPaint)
        }

        // 飞蛋渲染（阶段0：飞入）
        if (animState == 3 && eggFlyPhase == 0) {
            val ex = eggFlyX; val ey = eggFlyY
            val er = 28f
            // 蛋形椭圆
            canvas.drawOval(ex - er*0.7f, ey - er, ex + er*0.7f, ey + er, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFF8E1"); style = Paint.Style.FILL
            })
            canvas.drawOval(ex - er*0.7f, ey - er, ex + er*0.7f, ey + er, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DDD7B8"); style = Paint.Style.STROKE; strokeWidth = 2f
            })
            // 裂纹线
            canvas.drawLine(ex - 8f, ey - 5f, ex + 6f, ey + 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#998B72"); strokeWidth = 1.5f })
            canvas.drawLine(ex + 8f, ey - 8f, ex - 3f, ey + 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#998B72"); strokeWidth = 1.5f })
        }

        // 花蕾渲染（阶段0）
        if (animState == 4 && flowerBloomPhase == 0) {
            val cxb = width / 2f; val cyb = height / 2f
            canvas.drawCircle(cxb, cyb, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4CAF50"); style = Paint.Style.FILL })
            canvas.drawCircle(cxb - 4f, cyb - 6f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF80AB"); style = Paint.Style.FILL })
        }

        // 鸡蛋冲击波环（无背景底色，直接覆盖画面）
        if (showEggOverlay && shockwaveAlpha > 0 && shockwaveRadius > 0 && eggFlyPhase >= 1) {
            val cx = w / 2f; val cy = h / 2f
            canvas.drawCircle(cx, cy, shockwaveRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFF176")
                alpha = (shockwaveAlpha * 150).toInt().coerceIn(0, 255)
                style = Paint.Style.STROKE; strokeWidth = 6f
            })
            canvas.drawCircle(cx, cy, shockwaveRadius * 0.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFD54F")
                alpha = (shockwaveAlpha * 100).toInt().coerceIn(0, 255)
                style = Paint.Style.STROKE; strokeWidth = 3f
            })
        }

        // 动画粒子
        for (p in particles) {
            val alpha = (p.life / p.maxLife * 255).toInt().coerceIn(0, 255)
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.color; this.alpha = alpha; style = Paint.Style.FILL
            }.let { canvas.drawCircle(p.x, p.y, p.size * (p.life / p.maxLife), it) }
        }

        // 消息
        if (message.isNotEmpty()) {
            canvas.drawText(message, w / 2f, h - 14f, msgPaint)
        }


    }

    private fun drawPiece(canvas: Canvas, cx: Float, cy: Float, player: Int, isLast: Boolean, alpha: Float, row: Int = -1, col: Int = -1) {
        val r = cellSize * 0.40f
        canvas.drawCircle(cx + cellSize * 0.04f, cy + cellSize * 0.06f, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#50000000"); style = Paint.Style.FILL; this.alpha = (alpha * 255).toInt()
            })
        val colors: IntArray; val stops: FloatArray
        if (player == GomokuGame.PLAYER_BLACK) {
            colors = intArrayOf(Color.parseColor("#666666"), Color.parseColor("#2A2A2A"),
                Color.parseColor("#111111"), Color.BLACK)
            stops = floatArrayOf(0f, 0.3f, 0.65f, 1f)
        } else {
            colors = intArrayOf(Color.WHITE, Color.parseColor("#F5F5F5"),
                Color.parseColor("#DDDDDD"), Color.parseColor("#C0C0C0"))
            stops = floatArrayOf(0f, 0.3f, 0.65f, 1f)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r, colors, stops, Shader.TileMode.CLAMP)
            style = Paint.Style.FILL; this.alpha = (alpha * 255).toInt()
        }
        canvas.drawCircle(cx, cy, r, p)
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (player == GomokuGame.PLAYER_BLACK) Color.parseColor("#444444") else Color.parseColor("#BBBBBB")
            style = Paint.Style.STROKE; strokeWidth = 1f; this.alpha = (alpha * 255).toInt()
        })
        if (isLast && alpha > 0.9f) canvas.drawCircle(cx, cy, r * 0.32f, lastMarkPaint)
        // 棋子顺序数字（显示在棋子上）
        if (showPieceOrder && game != null && row >= 0 && col >= 0) {
            val order = game!!.pieceOrder[row][col]
            if (order > 0) {
                val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = cellSize * 0.36f; textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                    color = if (player == GomokuGame.PLAYER_BLACK) Color.WHITE else Color.parseColor("#333333")
                }
                canvas.drawText("$order", cx, cy + numPaint.textSize * 0.35f, numPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_CANCEL) { clearPreview(); return true }
        if (!BattleDisplays.ready) return true
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val g = game ?: return true
        if (g.isPaused) { message = "游戏暂停中"; invalidate(); return true }
        if (g.isGameOver) { message = "游戏已结束，请重新开始"; invalidate(); return true }
        if (g.currentPlayer != playerPerspective) { message = "请等待对方落子！"; invalidate(); return true }
        val col = ((event.x - boardOffsetX + cellSize / 2) / cellSize).toInt()
        val row = ((event.y - boardOffsetY + cellSize / 2) / cellSize).toInt()
        if (row < 0 || row >= GomokuGame.BOARD_SIZE || col < 0 || col >= GomokuGame.BOARD_SIZE) {
            pendingRow = -1; pendingCol = -1; message = "请在棋盘内落子"; invalidate(); return true
        }
        if (g.board[row][col] != GomokuGame.EMPTY) {
            pendingRow = -1; pendingCol = -1; message = "该位置已有棋子"; invalidate(); return true
        }
        // 点击预览位置的确认圈 → 真正落子
        if (pendingRow == row && pendingCol == col) {
            pendingRow = -1; pendingCol = -1; message = ""
            onConfirmPlace?.invoke(row, col)
            return true
        }
        // 设置/移动预览
        pendingRow = row; pendingCol = col; message = ""
        invalidate()
        return true
    }

    // ===== V4.1 胜利动画（5秒满屏金色彩带+烟花+冲击波，强烈视觉冲击）=====
    private var winCleanupRunnable: Runnable? = null
    private var winPhase2Runnable: Runnable? = null
    private var winPhase3Runnable: Runnable? = null

    fun startWinAnimation() {
        winCleanupRunnable?.let { removeCallbacks(it) }
        winPhase2Runnable?.let { removeCallbacks(it) }
        winPhase3Runnable?.let { removeCallbacks(it) }
        selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
        animState = 1; animProgress = 0f; particles.clear()
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val colors = intArrayOf(
            Color.parseColor("#FFD700"), Color.parseColor("#FF6D00"),
            Color.parseColor("#FF1744"), Color.parseColor("#00E676"),
            Color.parseColor("#2979FF"), Color.parseColor("#FF4081"),
            Color.parseColor("#FFEA00"), Color.parseColor("#AA00FF"),
            Color.parseColor("#00BCD4"), Color.parseColor("#FF5722"))
        // 阶段1：中心大爆炸 — 120个高速粒子 (life=3.0, ~5s存活)
        for (i in 0..120) {
            val angle = Math.random() * 2 * PI
            val speed = 5f + Math.random().toFloat() * 18f
            particles.add(Particle(cx, cy,
                (Math.cos(angle) * speed).toFloat(), (Math.sin(angle) * speed).toFloat(),
                3.0f, 3.0f, colors[i % colors.size], 8f + Math.random().toFloat() * 16f))
        }
        // 满屏彩带：100个从各方飘落 (life=2.8)
        for (i in 0..100) {
            particles.add(Particle(
                (Math.random() * w).toFloat(), -20f - Math.random().toFloat() * h * 0.3f,
                ((Math.random() - 0.5) * 3f).toFloat(), 2f + Math.random().toFloat() * 6f,
                2.8f, 2.8f, colors[i % colors.size], 5f + Math.random().toFloat() * 12f))
        }
        // 金色闪光：80个 (life=2.0)
        for (i in 0..80) {
            particles.add(Particle(
                (Math.random() * w).toFloat(), (Math.random() * h).toFloat(),
                ((Math.random() - 0.5) * 2f).toFloat(), ((Math.random() - 0.5) * 2f).toFloat(),
                2.0f, 2.0f, Color.parseColor("#FFD700"), 3f + Math.random().toFloat() * 10f))
        }
        message = "🎉 胜利！"
        invalidate()
        ensureAnimating()
        // 阶段2 (1.5s后)：第二波爆炸 — 80个彩色烟花在随机位置
        winPhase2Runnable = Runnable {
            if (animState != 1) return@Runnable
            val cx2 = w * (0.2f + Math.random().toFloat() * 0.6f)
            val cy2 = h * (0.2f + Math.random().toFloat() * 0.6f)
            for (i in 0..80) {
                val angle = Math.random() * 2 * PI
                val speed = 4f + Math.random().toFloat() * 14f
                particles.add(Particle(cx2, cy2,
                    (Math.cos(angle) * speed).toFloat(), (Math.sin(angle) * speed).toFloat(),
                    2.5f, 2.5f, colors[(i + 3) % colors.size], 6f + Math.random().toFloat() * 14f))
            }
            for (i in 0..50) {
                particles.add(Particle(
                    (Math.random() * w).toFloat(), (Math.random() * h).toFloat(),
                    ((Math.random() - 0.5) * 1.5f).toFloat(), ((Math.random() - 0.5) * 1.5f).toFloat(),
                    2.0f, 2.0f, Color.parseColor("#FFD700"), 4f + Math.random().toFloat() * 8f))
            }
        }
        postDelayed(winPhase2Runnable!!, 1500)
        // 阶段3 (2.8s后)：第三波 — 60个上升粒子形成"烟花柱"
        winPhase3Runnable = Runnable {
            if (animState != 1) return@Runnable
            for (i in 0..60) {
                particles.add(Particle(
                    (Math.random() * w).toFloat(), h + 20f,
                    ((Math.random() - 0.5) * 2f).toFloat(), -(6f + Math.random().toFloat() * 12f),
                    2.2f, 2.2f, colors[i % colors.size], 5f + Math.random().toFloat() * 10f))
            }
        }
        postDelayed(winPhase3Runnable!!, 2800)
        // 5秒后清理
        winCleanupRunnable = Runnable {
            message = ""; particles.clear(); animState = 0
            selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
            invalidate()
        }
        postDelayed(winCleanupRunnable!!, 5000)
    }

    // ===== V4.1 失败动画（5秒满屏碎片+暗色烟雾+震感，强烈视觉冲击）=====
    private var loseCleanupRunnable: Runnable? = null
    private var losePhase2Runnable: Runnable? = null
    private var losePhase3Runnable: Runnable? = null

    fun startLoseAnimation() {
        loseCleanupRunnable?.let { removeCallbacks(it) }
        losePhase2Runnable?.let { removeCallbacks(it) }
        losePhase3Runnable?.let { removeCallbacks(it) }
        selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
        animState = 2; animProgress = 0f; particles.clear()
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val darkColors = intArrayOf(
            Color.parseColor("#455A64"), Color.parseColor("#37474F"),
            Color.parseColor("#546E7A"), Color.parseColor("#263238"),
            Color.parseColor("#607D8B"), Color.parseColor("#1A237E"),
            Color.parseColor("#311B92"), Color.parseColor("#004D40"))
        // 碎片炸裂：120个 (life=3.0, ~5s存活)
        for (i in 0..120) {
            val angle = Math.random() * 2 * PI
            val speed = 3f + Math.random().toFloat() * 14f
            particles.add(Particle(cx, cy,
                (Math.cos(angle) * speed).toFloat(), (Math.sin(angle) * speed).toFloat(),
                3.0f, 3.0f, darkColors[i % darkColors.size], 6f + Math.random().toFloat() * 16f))
        }
        // 掉落碎片：100个像雨滴 (life=2.5)
        for (i in 0..100) {
            particles.add(Particle(
                (Math.random() * w).toFloat(), -20f - Math.random().toFloat() * h * 0.3f,
                ((Math.random() - 0.5) * 1.5f).toFloat(), 1.2f + Math.random().toFloat() * 4f,
                2.5f, 2.5f, darkColors[i % darkColors.size], 3f + Math.random().toFloat() * 12f))
        }
        // 暗色烟雾：60个 (life=2.2)
        for (i in 0..60) {
            particles.add(Particle(
                (Math.random() * w).toFloat(), (Math.random() * h).toFloat(),
                ((Math.random() - 0.5) * 1f).toFloat(), ((Math.random() - 0.5) * 1f).toFloat(),
                2.2f, 2.2f, Color.parseColor("#33222222"), 12f + Math.random().toFloat() * 28f))
        }
        // 红色裂纹粒子：50个增加视觉冲击 (life=2.0)
        for (i in 0..50) {
            particles.add(Particle(
                (Math.random() * w).toFloat(), (Math.random() * h).toFloat(),
                ((Math.random() - 0.5) * 2.5f).toFloat(), ((Math.random() - 0.5) * 2.5f).toFloat(),
                2.0f, 2.0f, Color.parseColor("#CC37474F"), 2f + Math.random().toFloat() * 4f))
        }
        message = "💔 失败"
        invalidate()
        ensureAnimating()
        // 阶段2 (1.5s后)：第二波碎裂 — 80个碎片从四周涌入
        losePhase2Runnable = Runnable {
            if (animState != 2) return@Runnable
            for (i in 0..80) {
                val angle = Math.random() * 2 * PI
                val dist = 100f + Math.random().toFloat() * 200f
                val sx = cx + (Math.cos(angle) * dist).toFloat()
                val sy = cy + (Math.sin(angle) * dist).toFloat()
                particles.add(Particle(sx, sy,
                    ((cx - sx) * 0.03f).toFloat(), ((cy - sy) * 0.03f).toFloat(),
                    2.2f, 2.2f, darkColors[i % darkColors.size], 5f + Math.random().toFloat() * 12f))
            }
        }
        postDelayed(losePhase2Runnable!!, 1500)
        // 阶段3 (2.8s后)：暗色雨滴 — 60个从天而降
        losePhase3Runnable = Runnable {
            if (animState != 2) return@Runnable
            for (i in 0..60) {
                particles.add(Particle(
                    (Math.random() * w).toFloat(), -30f,
                    ((Math.random() - 0.5) * 2f).toFloat(), 3f + Math.random().toFloat() * 8f,
                    1.8f, 1.8f, darkColors[i % darkColors.size], 4f + Math.random().toFloat() * 10f))
            }
        }
        postDelayed(losePhase3Runnable!!, 2800)
        // 5秒后清理
        loseCleanupRunnable = Runnable {
            message = ""; particles.clear(); animState = 0
            selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
            invalidate()
        }
        postDelayed(loseCleanupRunnable!!, 5000)
    }

    // 臭鸡蛋动画：飞来→撞击→炸裂（3阶段）
    private var showEggOverlay = false
    private var eggOverlayAlpha = 0f
    private var shockwaveRadius = 0f
    private var shockwaveAlpha = 0f
    private var eggFlyPhase = 0       // 0=飞入, 1=炸裂, 2=消散
    private var eggFlyX = 0f; private var eggFlyY = 0f
    private var eggImpactTime = 0L

    private var eggCleanupRunnable: Runnable? = null
    private var flowerCleanupRunnable: Runnable? = null

    fun startEggAnimation() {
        // 防抖：500ms内不重复触发
        val now = System.currentTimeMillis()
        if (now - lastAnimStartMs < 500) return
        lastAnimStartMs = now
        eggCleanupRunnable?.let { removeCallbacks(it) }
        selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
        animState = 3; animProgress = 0f; particles.clear()
        showEggOverlay = true; eggOverlayAlpha = 1f
        shockwaveRadius = 0f; shockwaveAlpha = 0f
        eggFlyPhase = 0
        eggFlyX = -80f; eggFlyY = -80f
        eggImpactTime = System.currentTimeMillis() + 250
        message = ""
        invalidate()
        ensureAnimating()
        // 250ms后触发炸裂
        postDelayed({
            eggFlyPhase = 1; shockwaveAlpha = 1f
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2f; val cy = h / 2f
            // 蛋黄炸裂：40个大斑
            val yolkC = intArrayOf(Color.parseColor("#FFD700"), Color.parseColor("#FFA500"), Color.parseColor("#FFC107"), Color.parseColor("#FF9800"), Color.parseColor("#FF8F00"))
            for (i in 0..40) {
                val a = Math.random() * 2 * PI; val spd = 6f + Math.random().toFloat() * 18f
                particles.add(Particle(cx, cy, (Math.cos(a) * spd).toFloat(), (Math.sin(a) * spd).toFloat() - 2f, 2f, 2f, yolkC[i % yolkC.size], 15f + Math.random().toFloat() * 30f))
            }
            // 蛋清飞溅：60滴
            val whiteC = intArrayOf(Color.parseColor("#FFFACD"), Color.parseColor("#FFFFF0"), Color.parseColor("#FFF8DC"), Color.parseColor("#FAFAD2"))
            for (i in 0..60) {
                val a = Math.random() * 2 * PI; val spd = 4f + Math.random().toFloat() * 14f
                particles.add(Particle(cx, cy, (Math.cos(a) * spd).toFloat(), (Math.sin(a) * spd).toFloat() - 1f, 1.6f, 1.6f, whiteC[i % whiteC.size], 8f + Math.random().toFloat() * 22f))
            }
            // 蛋壳碎片：70片
            val shC = intArrayOf(Color.parseColor("#F5DEB3"), Color.parseColor("#FAEBD7"), Color.parseColor("#FFF8DC"), Color.parseColor("#FFE4B5"))
            for (i in 0..70) {
                val a = Math.random() * 2 * PI; val spd = 8f + Math.random().toFloat() * 22f
                particles.add(Particle(cx, cy, (Math.cos(a) * spd).toFloat(), (Math.sin(a) * spd).toFloat() - 5f, 1f, 1f, shC[i % shC.size], 5f + Math.random().toFloat() * 16f))
            }
            // 绿色臭气
            val stC = intArrayOf(Color.parseColor("#7CB342"), Color.parseColor("#558B2F"), Color.parseColor("#9CCC65"))
            for (i in 0..20) {
                val a = Math.random() * 2 * PI; val r = 50f + Math.random().toFloat() * 150f
                particles.add(Particle(cx + (Math.cos(a)*r).toFloat(), cy + (Math.sin(a)*r).toFloat(), 0f, -3f-Math.random().toFloat()*6f, 1.5f, 1.5f, stC[i % stC.size], 12f + Math.random().toFloat() * 20f))
            }
            message = "💥 啪！"
            invalidate()
        }, 250)
        postDelayed({ shockwaveAlpha = 0f; invalidate() }, 1200)
        eggCleanupRunnable = Runnable {
            showEggOverlay = false; eggOverlayAlpha = 0f; message = ""; particles.clear(); invalidate()
        }
        postDelayed(eggCleanupRunnable!!, 2800)
    }

    // 自驱动动画循环（鲜花/鸡蛋不依赖外部ValueAnimator）
    private var selfAnimRunnable: Runnable? = null

    private fun ensureAnimating() {
        if (selfAnimRunnable != null) return
        selfAnimRunnable = object : Runnable {
            override fun run() {
                if (!updateAnimation()) { selfAnimRunnable = null; return }
                postDelayed(this, 33)
            }
        }
        postDelayed(selfAnimRunnable!!, 33)
    }

    fun updateAnimation(): Boolean {
        if (animState == 0) return false
        animProgress += 0.03f
        // 鸡蛋飞行阶段
        if (animState == 3 && eggFlyPhase == 0) {
            val cx = width / 2f; val cy = height / 2f
            eggFlyX += (cx - eggFlyX) * 0.12f
            eggFlyY += (cy - eggFlyY) * 0.12f
        }
        // 冲击波扩散
        if (shockwaveAlpha > 0) shockwaveRadius += 18f
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx; p.y += p.vy
            // 鲜花绽放阶段1：花瓣从中心向外扩展
            if (animState == 4 && flowerBloomPhase == 1) {
                p.size += 0.5f
                p.vx *= 1.04f; p.vy *= 1.04f
            }
            // 鲜花绽放阶段2：花瓣飘散
            else if (animState == 4 && flowerBloomPhase == 2) {
                p.vx += ((Math.random() - 0.5) * 0.5f).toFloat()
                p.vy += 0.03f
            } else {
                p.vy += 0.2f
            }
            p.life -= 0.018f
            if (p.life <= 0) iter.remove()
        }
        if (animProgress > 1.5f && particles.isEmpty() && eggFlyPhase >= 1 && animState != 1 && animState != 2) {
            animState = 0; showEggOverlay = false; eggOverlayAlpha = 0f
            shockwaveAlpha = 0f; eggFlyPhase = 0; flowerBloomPhase = 0; message = ""
        }
        invalidate()
        return animState != 0
    }

    // 鲜花绽放动画：花蕾→绽放→飘散（3阶段，≤3s）
    private var flowerBloomPhase = 0  // 0=花蕾, 1=绽放, 2=飘散

    fun startFlowerAnimation() {
        val now = System.currentTimeMillis()
        if (now - lastAnimStartMs < 500) return
        lastAnimStartMs = now
        flowerCleanupRunnable?.let { removeCallbacks(it) }
        selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
        animState = 4; animProgress = 0f; particles.clear()
        flowerBloomPhase = 0
        showEggOverlay = false; eggOverlayAlpha = 0f
        message = "🌷"
        invalidate()
        ensureAnimating()
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        // 阶段0→1：花蕾绽放（0.2s后）
        postDelayed({
            flowerBloomPhase = 1
            val petalC = intArrayOf(Color.parseColor("#FF6B8A"), Color.parseColor("#FF4081"), Color.parseColor("#FF80AB"), Color.parseColor("#E91E63"), Color.parseColor("#F48FB1"), Color.parseColor("#FF8A80"))
            for (i in 0..80) {
                val angle = (i.toDouble() / 80.0) * 2 * PI
                particles.add(Particle(cx, cy, (Math.cos(angle) * 0.5f).toFloat(), (Math.sin(angle) * 0.5f).toFloat(), 1.2f, 1.2f, petalC[i % petalC.size], 8f))
            }
            message = "🌸"
            invalidate()
        }, 200)
        // 阶段2：盛开+飘散（0.8s后）
        postDelayed({
            flowerBloomPhase = 2
            val petalC2 = intArrayOf(Color.parseColor("#FFD54F"), Color.parseColor("#FFC107"), Color.parseColor("#CE93D8"), Color.parseColor("#BA68C8"), Color.parseColor("#90CAF9"), Color.parseColor("#64B5F6"), Color.parseColor("#FFAB91"), Color.parseColor("#A5D6A7"))
            for (i in 0..120) {
                val angle = (i.toDouble() / 120.0) * 2 * PI
                val spd = 3f + Math.random().toFloat() * 8f
                particles.add(Particle(cx, cy, (Math.cos(angle) * spd).toFloat(), (Math.sin(angle) * spd).toFloat(), 1.5f, 1.5f, petalC2[i % petalC2.size], 8f + Math.random().toFloat() * 16f))
            }
            for (i in 0..40) {
                particles.add(Particle(cx + (Math.random().toFloat()-0.5f)*200f, cy + (Math.random().toFloat()-0.5f)*200f, 0f, 0f, 0.8f, 0.8f, Color.parseColor("#FFFFFF"), 3f + Math.random().toFloat() * 8f))
            }
            message = "🌺"
            invalidate()
        }, 800)
        // 清除
        flowerCleanupRunnable = Runnable {
            message = ""; particles.clear(); animState = 0; flowerBloomPhase = 0
            selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
            invalidate()
        }
        postDelayed(flowerCleanupRunnable!!, 2800)
    }

    /** 清除所有动画状态 */
    fun clearAllAnimations() {
        winCleanupRunnable?.let { removeCallbacks(it) }; winCleanupRunnable = null
        loseCleanupRunnable?.let { removeCallbacks(it) }; loseCleanupRunnable = null
        eggCleanupRunnable?.let { removeCallbacks(it) }; eggCleanupRunnable = null
        flowerCleanupRunnable?.let { removeCallbacks(it) }; flowerCleanupRunnable = null
        selfAnimRunnable?.let { removeCallbacks(it) }; selfAnimRunnable = null
        animState = 0; particles.clear()
        showEggOverlay = false; eggOverlayAlpha = 0f; shockwaveAlpha = 0f
        flowerBloomPhase = 0; eggFlyPhase = 0
        message = ""; invalidate()
    }

    fun clearPreview() { pendingRow = -1; pendingCol = -1 }

    fun clearMessage() { message = ""; invalidate() }
    fun showMessage(msg: String) { message = msg; invalidate() }
}
