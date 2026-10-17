package com.gomoku.dualscreen

import org.junit.Assert.*
import org.junit.Test

/**
 * 玩家归属由固件状态算出：交换只换逻辑角色，物理面板 A/B 固定。
 * 这批用例就是为了防止再退回“必须现场标定才能玩”。
 */
class PanelMapTest {
    @Test fun swapOffPutsBlackOnPrimaryPanel() {
        val routes = PanelMap.routes(primaryId = 0, secondaryId = 2, master = "0")
        assertEquals(0, routes?.get(GomokuGame.PLAYER_BLACK))
        assertEquals(2, routes?.get(GomokuGame.PLAYER_WHITE))
    }

    @Test fun swapOnMovesBlackToTheOtherPanel() {
        val routes = PanelMap.routes(primaryId = 0, secondaryId = 2, master = "1")
        assertEquals(2, routes?.get(GomokuGame.PLAYER_BLACK))
        assertEquals(0, routes?.get(GomokuGame.PLAYER_WHITE))
    }

    @Test fun playersAlwaysEndUpOnDifferentDisplays() {
        for (master in listOf("0", "1")) {
            val routes = PanelMap.routes(0, 2, master)
            assertNotNull("master=$master 应可判定", routes)
            assertEquals(2, routes?.values?.toSet()?.size)
        }
    }

    @Test fun unknownSwapStateIsBlockedInsteadOfGuessed() {
        assertNull(PanelMap.routes(0, 2, null))
        assertNull(PanelMap.routes(0, 2, ""))
        assertNull(PanelMap.routes(0, 2, "garbage"))
        assertNull(PanelMap.routes(0, 2, "-1"))
    }

    @Test fun singleScreenOrSameDisplayIsBlocked() {
        assertNull(PanelMap.routes(0, null, "0"))
        assertNull(PanelMap.routes(0, 0, "0"))
        assertNull(PanelMap.routes(-1, 2, "0"))
    }

    @Test fun secondaryIdIsNotHardcodedToTwo() {
        val routes = PanelMap.routes(primaryId = 0, secondaryId = 5, master = "1")
        assertEquals(5, routes?.get(GomokuGame.PLAYER_BLACK))
        assertEquals(0, routes?.get(GomokuGame.PLAYER_WHITE))
    }

    @Test fun whiteViewIsTurnedAroundForTheOpposingPlayer() {
        assertEquals(0f, PanelMap.contentRotation(GomokuGame.PLAYER_BLACK), 0f)
        assertEquals(180f, PanelMap.contentRotation(GomokuGame.PLAYER_WHITE), 0f)
    }
}
