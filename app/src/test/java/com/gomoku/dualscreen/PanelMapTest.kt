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

    @Test fun contentStaysAnchoredWhenTheDeviceTurns() {
        // 0° 姿态、未交换：窗口 rotation 0 / 2 → 应用 0° / 180°，落在面板上都是正向
        assertEquals(0f, PanelMap.contentRotation(0, "0", null), 0f)
        assertEquals(180f, PanelMap.contentRotation(2, "0", null), 0f)
        // 整机 180°、未交换：窗口 rotation 换成 2 / 0，应用反着补
        assertEquals(180f, PanelMap.contentRotation(2, "0", null), 0f)
        assertEquals(0f, PanelMap.contentRotation(0, "0", null), 0f)
    }

    @Test fun contentStaysAnchoredWhenScreensAreSwapped() {
        // 交换后投影再整体加 180°（固件说明场景 02/06），所以应用要再补 180°
        assertEquals(180f, PanelMap.contentRotation(0, "1", "-1"), 0f)
        assertEquals(0f, PanelMap.contentRotation(2, "1", "-1"), 0f)
    }

    @Test fun aShapeDoesNotAddSwapCompensation() {
        // A 字（screen_mode=1）投影不再叠加交换补偿（场景 09/10）
        assertEquals(0f, PanelMap.contentRotation(0, "1", "1"), 0f)
        assertEquals(180f, PanelMap.contentRotation(2, "1", "1"), 0f)
    }

    @Test fun oddRotationsFallBackToZero() {
        assertEquals(0f, PanelMap.contentRotation(1, "0", null), 0f)
        assertEquals(0f, PanelMap.contentRotation(3, "0", null), 0f)
    }
}
