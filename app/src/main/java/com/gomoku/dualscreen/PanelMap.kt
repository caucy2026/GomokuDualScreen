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
     * 画面绑定实体面板，不跟着整机转动走。
     *
     * 系统会按整机姿态给每块屏的输出加旋转（例如整机转 180° 后，两块屏的 orientation 变成 2 和 0），
     * 应用若原样跟随，人看到的就是画面整体翻过去。这里用该窗口所在显示的系统 rotation 反过来抵消，
     * 使最终落在面板上的朝向与 0° 姿态时一致。
     *
     * 本产品只出现 0°/180° 两种投影（1920×1280 横屏面板），90°/270° 需要换宽高，不在范围内。
     */
    fun contentRotation(displayRotation: Int): Float = when (displayRotation) {
        2 -> 180f
        else -> 0f
    }
}
