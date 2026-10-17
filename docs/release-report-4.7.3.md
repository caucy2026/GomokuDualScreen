# 五子棋双屏 4.7.3 · 商城发布报告

发布时间：2026-10-17　｜　平台：KEMI 应用商城（`kemi.newlinksz.com`）　｜　操作系统：Android
仓库：`caucy2026/GomokuDualScreen` master `ed8c9c7`　｜　工作副本：`D:\Backup\Documents\deepseek-harness\default-workspace\gomoku-dualscreen`

## 一、结果

| 项 | 值 |
| --- | --- |
| 应用 | **app_id 10**，`五子棋双屏`，`com.gomoku.dualscreen`，分类「游戏」 |
| 操作 | **更新**（原线上 4.5 / code 2） |
| 新版本 | `versionName 4.7.3`　`versionCode 7` |
| 上传结果 | `apk-token → CDN → apk-complete` 均 200，服务端回读的包名/版本/字节/SHA 与本地一致 |
| 更新时间 | 2026-10-17，服务端返回“已直接更新线上版本（免审）” |

## 二、产物与一致性

| 项 | 值 |
| --- | --- |
| 本地 APK | `GomokuDualScreen-4.7.3-release.apk`，**11,656,435 字节** |
| SHA-256 | `0e31a71fa214be9158c86fe7fcc484a29f69ca86cf3022d1b69ac6c8818c471d` |
| 签名证书 SHA-256 | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`（与线上 4.5 同证书，可覆盖升级） |
| CDN 对象 | `https://cdn.newlink-sz.com/kemiAppStore/apk/2026/10/1792226412029_3e045133_GomokuDualScreen-4_7_3-release.apk` |
| 顺带修复 | 线上旧记录 `download_url` 是 http、`file_size` 写成 `11.1MB`、`file_size_bytes` 为 0、`apk_sha256` 为空 → 本次一并改为 HTTPS + 精确字节串 + 整数 + SHA-256 |

## 三、发布后四层校验（全部通过）

**1. 开发者记录**：唯一一条；版本 4.7.3 / code 7；URL https；`file_size` 精确字节串；`file_size_bytes` = 11656435；`apk_sha256` 一致；`list_in_store` 为真。

**2. 公共店面详情**（`/api/store/apps/10?os=android`）：可读；`file_size` 精确字节串；URL https；`apk_sha256` 一致；`version_code` = 7。

**3. 公共平台列表**（不带 `category` 过滤）：app 10 出现在默认「全部」列表中。

**4. CDN**：HTTP 200；`Content-Length` = 11656435；**整包下载后字节数一致、SHA-256 一致**。

**5. 更新检查**：`version_code=2` → `has_update=true`，目标 code 7；`version_code=7` → `has_update=false`。

## 四、更新说明（已写入商城）

1. 双屏切换后玩家归属修复：按固件交换状态自动判定黑/白方所在屏，开关屏幕交换不再串屏；
2. 画面朝向修复：转动设备、开关交换后画面不再整体倒置；
3. 退出修复：任意一侧返回/侧滑退出，两侧窗口一起关闭，不再残留“等待双屏就绪”；
4. 修复部分机型画面反复闪“等待双屏就绪”；
5. 统一“重新开始/悔棋/退出”确认弹窗，两块屏样式一致；
6. 对局进度、回合、提子数持久化，杀进程后重新打开可继续。

## 五、限制与未覆盖项

- **客户端无内置自更新**：本应用没有应用内检查更新/下载安装逻辑，升级走商城更新通道（本次已验证旧版本能收到更新提示）。
- **店面按钮的肉眼确认**：API 合同（HTTPS/字节/SHA/版本）已全部满足，但没有在真机商城客户端里点击下载安装；如需，我可按设备上的商城 App 再走一遍。
- **十场景方向回归**：仍按 `五子棋双屏现场验收单.md` 由人眼确认，本次发布未包含该人工验收结论。
- 未改动分类（沿用「游戏」）、未改动图标与截图内容（仅把 http 链接换成 https，指向同一对象）。

## 六、凭据与临时文件

- 登录凭据仅以进程环境变量传入，未写入仓库、日志或本报告；临时脚本不包含任何账号、密码、token。
- 上传回执（不含令牌）留在 `.kemi-upload.json` 作为证据；其余临时 JSON 已清理。
