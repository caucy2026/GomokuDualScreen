package com.gomoku.dualscreen

import android.content.Context
import java.io.*

object GameState {
    var game = GomokuGame()
    var mainActivity: MainActivity? = null
    var blackTaunts = 1
    var whiteTaunts = 1
    var secondsLeft = 60
    var sessionId = java.util.UUID.randomUUID().toString()
    var inputSequence = 0L
    private var restored = false
    fun restore(context: Context) {
        if (restored) return
        restored = true
        try {
            ObjectInputStream(android.util.AtomicFile(File(context.filesDir, "battle-session.bin")).openRead()).use {
                game = it.readObject() as GomokuGame
                blackTaunts = it.readInt(); whiteTaunts = it.readInt()
                secondsLeft = it.readInt()
                sessionId = it.readUTF(); inputSequence = it.readLong()
            }
        } catch (_: Exception) { /* First launch or an incompatible old session. */ }
    }
    fun save(context: Context) {
        val file = android.util.AtomicFile(File(context.filesDir, "battle-session.bin"))
        val stream = file.startWrite()
        try {
            val output = ObjectOutputStream(stream)
            output.writeObject(game); output.writeInt(blackTaunts); output.writeInt(whiteTaunts)
            output.writeInt(secondsLeft)
            output.writeUTF(sessionId); output.writeLong(inputSequence)
            output.flush(); file.finishWrite(stream)
        } catch (_: Exception) { file.failWrite(stream) }
    }
}
