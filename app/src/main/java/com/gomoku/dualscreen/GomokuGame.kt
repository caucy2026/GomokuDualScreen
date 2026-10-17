package com.gomoku.dualscreen

/**
 * 五子棋游戏核心逻辑
 */
class GomokuGame : java.io.Serializable {
    companion object {
        const val BOARD_SIZE = 15
        const val EMPTY = 0
        const val PLAYER_BLACK = 1
        const val PLAYER_WHITE = 2
    }

    // 棋盘状态: 0=空, 1=黑子, 2=白子
    val board = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) { EMPTY } }

    // 每步落子顺序编号（基于棋盘实际计数，悔棋后自动修正），0=无棋子
    val pieceOrder = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) { 0 } }

    // 当前轮到哪个玩家 (黑先)
    var currentPlayer = PLAYER_BLACK
        private set

    // 游戏是否结束
    var isGameOver = false
        private set

    // 游戏是否已开始（有人点了开始）
    var isActive = false
        private set

    // 获胜者
    var winner = EMPTY
        private set

    // 暂停状态
    var isPaused = false
    var pausedByPlayer = EMPTY
    var pauseCountBlack = 1
    var pauseCountWhite = 1

    // 最后落子位置
    var lastRow = -1
        private set
    var lastCol = -1
        private set

    // 落子历史: Triple(row, col, player) - 用于分玩家悔棋
    private val moveHistory = mutableListOf<Triple<Int, Int, Int>>()

    // 游戏结束原因
    var gameOverReason: String = ""
        private set

    /**
     * 在指定位置落子
     * @return true 表示落子成功, false 表示非法操作
     */
    fun placePiece(row: Int, col: Int, player: Int): PlaceResult {
        if (!isActive) return PlaceResult(false, "请先点击开始游戏")
        if (isPaused) return PlaceResult(false, "游戏暂停中")
        if (isGameOver) {
            return PlaceResult(false, "游戏已结束，请重新开始")
        }
        if (player != currentPlayer) {
            return PlaceResult(false, if (currentPlayer == PLAYER_BLACK) "请等待黑方落子" else "请等待白方落子")
        }
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            return PlaceResult(false, "落子位置超出棋盘范围")
        }
        if (board[row][col] != EMPTY) {
            return PlaceResult(false, "该位置已有棋子")
        }

        board[row][col] = player
        // 基于棋盘实际计数：统计该玩家已有棋子数+1
        var cnt = 0
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) if (board[r][c] == player) cnt++
        pieceOrder[row][col] = cnt
        lastRow = row
        lastCol = col
        moveHistory.add(Triple(row, col, player))

        // 检查是否获胜
        if (checkWin(row, col, player)) {
            isGameOver = true
            winner = player
            gameOverReason = if (player == PLAYER_BLACK) "黑方获胜！" else "白方获胜！"
            return PlaceResult(true, gameOverReason, gameOver = true, winner = player)
        }

        // 检查平局
        if (isBoardFull()) {
            isGameOver = true
            winner = EMPTY
            gameOverReason = "棋盘已满，平局！"
            return PlaceResult(true, gameOverReason, gameOver = true, winner = EMPTY)
        }

        // 切换玩家
        currentPlayer = if (player == PLAYER_BLACK) PLAYER_WHITE else PLAYER_BLACK
        val nextPlayer = if (currentPlayer == PLAYER_BLACK) "黑方" else "白方"
        return PlaceResult(true, "轮到${nextPlayer}落子")
    }

    /**
     * 检查是否获胜
     */
    private fun checkWin(row: Int, col: Int, player: Int): Boolean {
        // 四个方向: 水平、垂直、对角线、反对角线
        val directions = arrayOf(
            intArrayOf(0, 1),   // 水平
            intArrayOf(1, 0),   // 垂直
            intArrayOf(1, 1),   // 对角线
            intArrayOf(1, -1)   // 反对角线
        )

        for ((dr, dc) in directions) {
            var count = 1
            // 正方向计数
            var r = row + dr
            var c = col + dc
            while (r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE && board[r][c] == player) {
                count++
                r += dr
                c += dc
            }
            // 反方向计数
            r = row - dr
            c = col - dc
            while (r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE && board[r][c] == player) {
                count++
                r -= dr
                c -= dc
            }
            if (count >= 5) return true
        }
        return false
    }

    /**
     * 检查棋盘是否已满
     */
    private fun isBoardFull(): Boolean {
        for (row in board) {
            for (cell in row) {
                if (cell == EMPTY) return false
            }
        }
        return true
    }

    /**
     * 悔棋 - 若对方已落子则撤销双方最后一步，否则只撤销自己
     * @param requestingPlayer 请求悔棋的玩家
     * @return UndoResult(success, undoCount=撤销步数(1或2))
     */
    fun undo(requestingPlayer: Int): UndoResult {
        if (!isActive) return UndoResult(false, "游戏尚未开始", 0)
        if (isGameOver) return UndoResult(false, "游戏已结束，无法悔棋", 0)
        if (moveHistory.isEmpty()) return UndoResult(false, "没有可悔的棋", 0)

        // 找到请求玩家最后一步
        val myIdx = moveHistory.indexOfLast { it.third == requestingPlayer }
        if (myIdx < 0) return UndoResult(false, "你还没有落子，无法悔棋", 0)

        val lastMove = moveHistory.last()
        var undoCount = 0

        if (lastMove.third != requestingPlayer) {
            // 对方已落子 → 先撤销对方最后一步，再撤销自己的
            val (or, oc, _) = moveHistory.removeAt(moveHistory.size - 1)
            board[or][oc] = EMPTY
            pieceOrder[or][oc] = 0
            undoCount++
        }

        // 撤销请求玩家最后一步
        val myNewIdx = moveHistory.indexOfLast { it.third == requestingPlayer }
        val (mr, mc, mp) = moveHistory.removeAt(myNewIdx)
        board[mr][mc] = EMPTY
        pieceOrder[mr][mc] = 0
        undoCount++

        currentPlayer = requestingPlayer
        lastRow = if (moveHistory.isEmpty()) -1 else moveHistory.last().first
        lastCol = if (moveHistory.isEmpty()) -1 else moveHistory.last().second
        return UndoResult(true, "已悔${undoCount}步，轮到${getPlayerName(requestingPlayer)}", undoCount)
    }

    /**
     * 检查指定玩家是否有可悔的棋
     */
    fun canUndo(player: Int): Boolean {
        if (isGameOver) return false
        return moveHistory.any { it.third == player }
    }

    /**
     * 开始游戏 - firstPlayer 为先手方
     */
    fun startGame(firstPlayer: Int) {
        restart()
        currentPlayer = firstPlayer
        isActive = true
    }

    /**
     * 重新开始游戏
     */
    fun restart() {
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                board[r][c] = EMPTY
                pieceOrder[r][c] = 0
            }
        }
        currentPlayer = PLAYER_BLACK
        isGameOver = false
        isActive = false
        winner = EMPTY
        isPaused = false
        pausedByPlayer = EMPTY
        pauseCountBlack = 1
        pauseCountWhite = 1
        lastRow = -1
        lastCol = -1
        moveHistory.clear()
        gameOverReason = ""
    }

    /**
     * 获取当前玩家颜色名称
     */
    fun getCurrentPlayerName(): String {
        return if (currentPlayer == PLAYER_BLACK) "黑方" else "白方"
    }

    /**
     * 获取指定玩家颜色名称
     */
    fun getPlayerName(player: Int): String {
        return when (player) {
            PLAYER_BLACK -> "黑方"
            PLAYER_WHITE -> "白方"
            else -> ""
        }
    }
}

/**
 * 落子结果
 */
data class PlaceResult(
    val success: Boolean,
    val message: String,
    val gameOver: Boolean = false,
    val winner: Int = GomokuGame.EMPTY
)

data class UndoResult(
    val success: Boolean,
    val message: String,
    val undoCount: Int = 0
)
