package com.gomoku.dualscreen

/**
 * 物理面板与逻辑显示的对应关系，依据固件说明
 * （docs/AI双屏助手产品形态与软件状态说明.md 第七章 1 节）：
 *
 * - 物理面板 A = 出厂主屏（MIPI 转 eDP），物理面板 B = 出厂副屏（HDMI 转 eDP）；
 * - 交换关闭（master=0）：逻辑主屏显示在 A，逻辑副屏显示在 B；
 * - 交换开启（master=1）：逻辑主屏显示在 B，逻辑副屏显示在 A。
 *
 * 交换只交换逻辑角色，不改变硬件面板，所以“谁在哪块屏”可以由固件状态算出来，
 * 不需要现场贴标定。物理面板 A 固定黑方，物理面板 B 固定白方。
 */
object PanelMap {
    /** 返回 玩家 → 目标 displayId；状态不可判定时返回 null（调用方应遮罩等待）。 */
    fun routes(primaryId: Int, secondaryId: Int?, master: String?): Map<Int, Int>? {
        if (primaryId < 0 || secondaryId == null || secondaryId == primaryId) return null
        return when (master) {
            "0" -> mapOf(
                GomokuGame.PLAYER_BLACK to primaryId,
                GomokuGame.PLAYER_WHITE to secondaryId
            )
            "1" -> mapOf(
                GomokuGame.PLAYER_BLACK to secondaryId,
                GomokuGame.PLAYER_WHITE to primaryId
            )
            else -> null
        }
    }

    /**
     * 画面绑定实体面板，不跟着整机转动或屏幕交换翻过去。
     *
     * 最终落在面板上的朝向 = 应用旋转 + 显示投影方向。固件说明第七章第 3 节的实测表给出：
     * 普通形态下投影方向等于窗口 rotation；打开屏幕交换后，投影方向再整体加 180°
     * （场景 02/06：窗口 [0,2] 对应投影 [2,0]，窗口 [2,0] 对应投影 [0,2]）；
     * A 字形态不叠加这次交换补偿（场景 09/10 两者相同）。
     *
     * 所以应用要抵消的是投影方向，不是窗口 rotation：
     *
     *     投影 = 窗口 rotation×90 + (已交换且非 A 字 ? 180 : 0)
     *     应用旋转 = 360 - 投影
     *
     * 只要这么算，任何姿态、交换开或关，面板上的朝向都不变。
     * 本产品只出现 0°/180° 两种投影（1920×1280 横屏面板），90°/270° 需要换宽高，不在范围内。
     */
    fun contentRotation(windowRotation: Int, master: String?, screenMode: String?): Float {
        val window = (windowRotation % 4) * 90
        // A 字（screen_mode=1）不叠加交换补偿；形态未知时按“叠加”处理，与实测场景 02/06 一致。
        val swapped = master == "1" && screenMode != "1"
        val projection = (window + if (swapped) 180 else 0) % 360
        // 竖持姿态（90°/270°）需要换宽高，本产品未定义，直接不补，避免把画面转到侧面。
        if (projection % 180 != 0) return 0f
        return ((360 - projection) % 360).toFloat()
    }
}
