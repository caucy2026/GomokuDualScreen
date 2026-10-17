package com.gomoku.dualscreen

/**
 * 两个窗口分属两个 task（白方那个 taskAffinity 带 .white），所以退出必须显式处理：
 * 只 finish 自己会留下另一屏停在“等待双屏就绪”。
 */
object ExitPolicy {
    /**
     * 用户关掉一扇窗口时，另一扇是否要跟着退出。
     *
     * @param peerAlive 另一扇窗口是否还在
     * @param panelsAvailable 当前可用实体面板数
     * @param replaced 该玩家是否已经被新窗口替换（切换显示时的迁移/重建）
     * @param migrating 这一扇是否正在迁往另一块屏
     */
    fun closePeer(peerAlive: Boolean, panelsAvailable: Int, replaced: Boolean, migrating: Boolean): Boolean =
        !migrating && !replaced && peerAlive && panelsAvailable >= 2
}
