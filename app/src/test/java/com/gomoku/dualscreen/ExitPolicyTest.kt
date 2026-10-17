package com.gomoku.dualscreen

import org.junit.Assert.*
import org.junit.Test

/** 退出时另一扇窗口怎么办：要退一起退，但一块屏灭了就是“等双屏恢复”，不能跟着退。 */
class ExitPolicyTest {
    @Test fun userClosingOneWindowClosesThePeer() {
        assertTrue(ExitPolicy.closePeer(peerAlive = true, panelsAvailable = 2, replaced = false, migrating = false))
    }

    @Test fun migrationToAnotherDisplayKeepsThePeer() {
        assertFalse(ExitPolicy.closePeer(peerAlive = true, panelsAvailable = 2, replaced = false, migrating = true))
    }

    @Test fun replacementWindowKeepsThePeer() {
        assertFalse(ExitPolicy.closePeer(peerAlive = true, panelsAvailable = 2, replaced = true, migrating = false))
    }

    @Test fun singlePanelKeepsWaitingInsteadOfExiting() {
        assertFalse(ExitPolicy.closePeer(peerAlive = true, panelsAvailable = 1, replaced = false, migrating = false))
    }

    @Test fun nothingToCloseWhenPeerIsGone() {
        assertFalse(ExitPolicy.closePeer(peerAlive = false, panelsAvailable = 2, replaced = false, migrating = false))
    }
}
