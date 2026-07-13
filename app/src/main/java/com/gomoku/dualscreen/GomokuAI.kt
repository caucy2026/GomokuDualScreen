package com.gomoku.dualscreen

/**
 * 五子棋 AI V3 — 方向扫描评估 + Alpha-Beta + 跳模式 + 复合威胁
 * 目标：3 秒内落子，棋力强
 */
object GomokuAI {

    private const val FIVE        = 100_000_000
    private const val OPEN_FOUR   = 10_000_000
    private const val JUMP_FOUR   = 5_000_000
    private const val CLOSED_FOUR = 1_000_000
    private const val OPEN_THREE  = 500_000
    private const val JUMP_THREE  = 200_000
    private const val CLOSED_THREE= 50_000
    private const val OPEN_TWO    = 10_000
    private const val CLOSED_TWO  = 1_000
    private const val SINGLE_OPEN = 10

    private val dirs = arrayOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, -1))
    private var deadline = 0L
    private const val TIME_MS = 2800L

    // ==================== 主入口 ====================

    fun findBestMove(board: Array<IntArray>, ai: Int): Pair<Int, Int>? {
        deadline = System.currentTimeMillis() + TIME_MS
        val cand = getCandidates(board, 30, ai)
        if (cand.isEmpty()) return null
        if (cand.size == 1) return cand[0]

        val opp = if (ai == 1) 2 else 1

        // 秒杀：自己赢 / 堵对方
        for ((r, c) in cand) { if (wouldWin(board, r, c, ai)) return Pair(r, c) }
        for ((r, c) in cand) { if (wouldWin(board, r, c, opp)) return Pair(r, c) }

        // 按攻击+防御排序
        val scored = cand.map { (r, c) -> Triple(r, c, quickScore(board, r, c, ai)) }
            .sortedByDescending { it.third }

        val topN = minOf(scored.size, 25)
        var best = Pair(scored[0].first, scored[0].second)

        // 迭代加深 1..6
        for (d in 1..6) {
            if (timedOut()) break
            var curBest = best; var curScore = Int.MIN_VALUE; var to = false
            for (i in 0 until topN) {
                if (timedOut()) { to = true; break }
                val (r, c, _) = scored[i]
                board[r][c] = ai
                val s = ab(board, d - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1, false, ai, 0)
                board[r][c] = 0
                if (s > curScore) { curScore = s; curBest = Pair(r, c) }
            }
            if (!to) best = curBest
        }
        return best
    }

    // ==================== Alpha-Beta ====================

    private fun ab(
        board: Array<IntArray>, depth: Int, alpha: Int, beta: Int,
        maxing: Boolean, ai: Int, lvl: Int
    ): Int {
        if (depth == 0 || timedOut()) return eval(board, ai)
        val opp = if (ai == 1) 2 else 1
        val player = if (maxing) ai else opp
        val lim = when (lvl) { 0 -> 28; 1 -> 20; else -> 14 }
        val cand = getCandidates(board, lim, player)
        if (cand.isEmpty()) return 0

        var a = alpha; var b = beta
        if (maxing) {
            var best = Int.MIN_VALUE
            for ((r, c) in cand) {
                board[r][c] = ai
                if (fiveAt(board, r, c, ai)) { board[r][c] = 0; return FIVE + depth }
                val s = ab(board, depth - 1, a, b, false, ai, lvl + 1)
                board[r][c] = 0
                if (s > best) best = s
                if (best > a) a = best
                if (a >= b) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for ((r, c) in cand) {
                board[r][c] = opp
                if (fiveAt(board, r, c, opp)) { board[r][c] = 0; return -(FIVE + depth) }
                val s = ab(board, depth - 1, a, b, true, ai, lvl + 1)
                board[r][c] = 0
                if (s < best) best = s
                if (best < b) b = best
                if (a >= b) break
            }
            return best
        }
    }

    // ==================== 评估（核心：方向扫描 + 跳模式检测） ====================

    /** 候选排序：攻+防 + 双威胁 5x */
    private fun quickScore(board: Array<IntArray>, r: Int, c: Int, ai: Int): Int {
        val opp = if (ai == 1) 2 else 1
        board[r][c] = ai; val (aiS, aiT) = cellEval(board, r, c, ai); board[r][c] = 0
        board[r][c] = opp; val (oppS, oppT) = cellEval(board, r, c, opp); board[r][c] = 0
        var score = aiS + oppS
        if (aiT >= 2) score = score * 5   // 双活三必胜
        if (oppT >= 2) score = score * 5
        return score
    }

    /** 全盘评估 */
    private fun eval(board: Array<IntArray>, ai: Int): Int {
        val opp = if (ai == 1) 2 else 1; var s = 0
        for (r in 0..14) for (c in 0..14) {
            when (board[r][c]) {
                ai -> s += cellEval(board, r, c, ai).first
                opp -> s -= cellEval(board, r, c, opp).first
            }
        }
        return s
    }

    /** 单格四方向评分 → (总分, 活三+威胁数) */
    private fun cellEval(board: Array<IntArray>, r: Int, c: Int, p: Int): Pair<Int, Int> {
        var score = 0; var threats = 0
        for ((dr, dc) in dirs) {
            val s = dirScan(board, r, c, dr, dc, p)
            score += s
            if (s >= OPEN_THREE) threats++
        }
        return Pair(score, threats)
    }

    /**
     * 方向扫描：从 (r,c) 沿 (dr,dc) 统计连续子 + 跳模式
     * 返回该方向对此玩家的评分。
     */
    private fun dirScan(board: Array<IntArray>, row: Int, col: Int, dr: Int, dc: Int, player: Int): Int {
        val B = 15

        // ---- 连续计数 ----
        var fwd = 0; var fr = row + dr; var fc = col + dc
        while (fr in 0 until B && fc in 0 until B && board[fr][fc] == player) { fwd++; fr += dr; fc += dc }
        val fwdOpen = fr in 0 until B && fc in 0 until B && board[fr][fc] == 0

        var bwd = 0; var br = row - dr; var bc = col - dc
        while (br in 0 until B && bc in 0 until B && board[br][bc] == player) { bwd++; br -= dr; bc -= dc }
        val bwdOpen = br in 0 until B && bc in 0 until B && board[br][bc] == 0

        val cont = 1 + fwd + bwd
        val openEnds = (if (fwdOpen) 1 else 0) + (if (bwdOpen) 1 else 0)

        // ---- 跳模式检测：跳过 1 空格后继续数 ----
        var fwdJump = 0; var hasFwdJump = false
        if (!fwdOpen && cont < 5) {
            val gr = row + dr * (fwd + 1); val gc = col + dc * (fwd + 1)
            if (gr in 0 until B && gc in 0 until B && board[gr][gc] == 0) {
                hasFwdJump = true
                var jr = gr + dr; var jc = gc + dc
                while (jr in 0 until B && jc in 0 until B && board[jr][jc] == player) { fwdJump++; jr += dr; jc += dc }
            }
        }
        var bwdJump = 0; var hasBwdJump = false
        if (!bwdOpen && cont < 5) {
            val gr = row - dr * (bwd + 1); val gc = col - dc * (bwd + 1)
            if (gr in 0 until B && gc in 0 until B && board[gr][gc] == 0) {
                hasBwdJump = true
                var jr = gr - dr; var jc = gc - dc
                while (jr in 0 until B && jc in 0 until B && board[jr][jc] == player) { bwdJump++; jr -= dr; jc -= dc }
            }
        }

        val totalJump = cont + fwdJump + bwdJump
        val hasJump = hasFwdJump || hasBwdJump

        // 跳模式的开口
        val fwdJOpen = if (hasFwdJump) {
            val jr = row + dr * (fwd + 2 + fwdJump); val jc = col + dc * (fwd + 2 + fwdJump)
            jr in 0 until B && jc in 0 until B && board[jr][jc] == 0
        } else fwdOpen
        val bwdJOpen = if (hasBwdJump) {
            val jr = row - dr * (bwd + 2 + bwdJump); val jc = col - dc * (bwd + 2 + bwdJump)
            jr in 0 until B && jc in 0 until B && board[jr][jc] == 0
        } else bwdOpen
        val jumpOpen = (if (fwdJOpen) 1 else 0) + (if (bwdJOpen) 1 else 0)

        // ---- 综合评分 ----
        return when {
            cont >= 5 -> FIVE
            cont == 4 && openEnds == 2 -> OPEN_FOUR
            cont == 4 && openEnds == 1 -> CLOSED_FOUR
            totalJump >= 4 && hasJump && jumpOpen == 2 -> JUMP_FOUR
            totalJump >= 4 && hasJump && jumpOpen == 1 -> CLOSED_FOUR
            cont == 3 && openEnds == 2 -> OPEN_THREE
            totalJump >= 3 && hasJump && jumpOpen == 2 -> JUMP_THREE
            cont == 3 && openEnds == 1 -> CLOSED_THREE
            totalJump >= 3 && hasJump && jumpOpen == 1 -> CLOSED_THREE
            cont == 2 && openEnds == 2 -> OPEN_TWO
            cont == 2 && openEnds == 1 -> CLOSED_TWO
            cont == 1 && openEnds == 2 -> SINGLE_OPEN
            cont == 1 && openEnds == 1 -> 1
            else -> 0
        }
    }

    // ==================== 工具函数 ====================

    private fun timedOut() = System.currentTimeMillis() >= deadline

    private fun wouldWin(board: Array<IntArray>, r: Int, c: Int, p: Int): Boolean {
        board[r][c] = p; val w = fiveAt(board, r, c, p); board[r][c] = 0; return w
    }

    private fun fiveAt(board: Array<IntArray>, row: Int, col: Int, p: Int): Boolean {
        for ((dr, dc) in dirs) {
            var cnt = 1
            var r = row + dr; var c = col + dc
            while (r in 0..14 && c in 0..14 && board[r][c] == p) { cnt++; r += dr; c += dc }
            r = row - dr; c = col - dc
            while (r in 0..14 && c in 0..14 && board[r][c] == p) { cnt++; r -= dr; c -= dc }
            if (cnt >= 5) return true
        }
        return false
    }

    // ==================== 候选生成 ====================

    /** 已有棋子周围 2 格 + 威胁分排序 + 数量截断 */
    private fun getCandidates(board: Array<IntArray>, max: Int, player: Int): List<Pair<Int, Int>> {
        val v = BooleanArray(225); val list = mutableListOf<Pair<Int, Int>>()
        for (r in 0..14) for (c in 0..14) {
            if (board[r][c] != 0) {
                for (dr in -2..2) for (dc in -2..2) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr; val nc = c + dc; val idx = nr * 15 + nc
                    if (nr in 0..14 && nc in 0..14 && board[nr][nc] == 0 && !v[idx]) {
                        v[idx] = true; list.add(Pair(nr, nc))
                    }
                }
            }
        }
        if (list.isEmpty()) return listOf(Pair(7, 7))
        if (list.size <= max) return list
        // 按该玩家的威胁分排序（而非密度）
        return list.map { (r, c) -> Triple(r, c, quickScore(board, r, c, player)) }
            .sortedByDescending { it.third }.take(max).map { Pair(it.first, it.second) }
    }
}
