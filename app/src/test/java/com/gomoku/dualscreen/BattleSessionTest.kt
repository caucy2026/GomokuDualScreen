package com.gomoku.dualscreen

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class BattleSessionTest {
    @Test fun inputKeepsItsSourcePlayer() {
        val game = GomokuGame()
        game.startGame(GomokuGame.PLAYER_BLACK)
        assertFalse(game.placePiece(7, 7, GomokuGame.PLAYER_WHITE).success)
        assertEquals(GomokuGame.EMPTY, game.board[7][7])
        assertTrue(game.placePiece(7, 7, GomokuGame.PLAYER_BLACK).success)
        assertFalse(game.placePiece(7, 8, GomokuGame.PLAYER_BLACK).success)
        assertTrue(game.placePiece(7, 8, GomokuGame.PLAYER_WHITE).success)
    }

    @Test fun pausedGameRejectsEvenCurrentPlayer() {
        val game = GomokuGame()
        game.startGame(GomokuGame.PLAYER_BLACK)
        game.isPaused = true
        assertFalse(game.placePiece(7, 7, GomokuGame.PLAYER_BLACK).success)
        assertEquals(GomokuGame.EMPTY, game.board[7][7])
    }

    @Test fun persistedSessionRetainsTurnHistoryAndPause() {
        val game = GomokuGame()
        game.startGame(GomokuGame.PLAYER_BLACK)
        game.placePiece(7, 7, GomokuGame.PLAYER_BLACK)
        game.placePiece(7, 8, GomokuGame.PLAYER_WHITE)
        game.isPaused = true; game.pausedByPlayer = GomokuGame.PLAYER_WHITE
        game.pauseCountWhite = 0
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(game) }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as GomokuGame }
        assertEquals(GomokuGame.PLAYER_BLACK, restored.currentPlayer)
        assertEquals(GomokuGame.PLAYER_WHITE, restored.board[7][8])
        assertEquals(1, restored.pieceOrder[7][7])
        assertTrue(restored.isPaused)
        assertEquals(GomokuGame.PLAYER_WHITE, restored.pausedByPlayer)
        assertEquals(0, restored.pauseCountWhite)
        assertTrue(restored.canUndo(GomokuGame.PLAYER_WHITE))
    }
}
