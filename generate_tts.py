"""五子棋TTS语音生成脚本 - Edge TTS 标准版+幽默方言版"""
import asyncio
import os
import sys

import edge_tts

AUDIO_DIR = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res", "raw")
os.makedirs(AUDIO_DIR, exist_ok=True)

VOICE_STANDARD = "zh-CN-XiaoxiaoNeural"   # 标准女声
VOICE_HUMOR = "zh-CN-liaoning-XiaobeiNeural"  # 东北方言女声

# 标准版文案
phrases_standard = [
    ("your_turn_black.mp3", "轮到你了，黑方请落子"),
    ("your_turn_white.mp3", "轮到你了，白方请落子"),
    ("wait_30s_black.mp3", "黑方选手，已经等三十秒了，我等到花都谢了"),
    ("wait_30s_white.mp3", "白方选手，已经等三十秒了，我等到花都谢了"),
    ("too_slow_black.mp3", "黑方你太墨迹了，送你一个臭鸡蛋"),
    ("too_slow_white.mp3", "白方你太墨迹了，送你一个臭鸡蛋"),
    ("hurry_black.mp3", "快点快点，黑方催促"),
    ("hurry_white.mp3", "快点快点，白方催促"),
    ("taunt_black.mp3", "黑方挑衅你，菜就多练"),
    ("taunt_white.mp3", "白方挑衅你，菜就多练"),
    ("taunt_undo.mp3", "你太菜了，可怜可怜你，让你一步"),
    ("reject_undo.mp3", "想悔棋，没门"),
    ("win_black.mp3", "黑方胜利了，白方需要加油"),
    ("win_white.mp3", "白方胜利了，黑方需要加油"),
]

# 东北方言版文案（地道东北话）
phrases_humor = [
    ("humor_your_turn_black.mp3", "黑方老铁，到你啦，麻溜地落子"),
    ("humor_your_turn_white.mp3", "白方老铁，轮到你啦，别磨叽"),
    ("humor_wait_30s_black.mp3", "黑方大哥，等你半天了，花儿都等蔫巴了"),
    ("humor_wait_30s_white.mp3", "白方大姐，半拉点了，你搁那嘎达寻思啥呢"),
    ("humor_too_slow_black.mp3", "黑方你也太肉了，臭鸡蛋可劲儿造"),
    ("humor_too_slow_white.mp3", "白方你比王八还慢，接住了臭蛋"),
    ("humor_hurry_black.mp3", "麻溜地，黑方急得直蹦高"),
    ("humor_hurry_white.mp3", "赶紧地，白方等得五脊六兽的"),
    ("humor_taunt_black.mp3", "黑方说了，技术太洼，回去练练再来"),
    ("humor_taunt_white.mp3", "白方放话了，啥也不是，别搁这儿嘚瑟"),
    ("humor_taunt_undo.mp3", "瞅你那熊样，让你一步得了"),
    ("humor_reject_undo.mp3", "想悔棋？门儿都没有，窗户也给你钉死了"),
    ("humor_win_black.mp3", "黑方赢啦，白方你得加把劲儿啊"),
    ("humor_win_white.mp3", "白方赢啦，黑方你可长点儿心吧"),
]


async def generate(filename: str, text: str, voice: str) -> None:
    path = os.path.join(AUDIO_DIR, filename)
    communicate = edge_tts.Communicate(text, voice)
    print(f"[{voice}] {filename} -> {text}")
    await communicate.save(path)


async def main() -> None:
    tasks = []
    tasks.extend(generate(f, t, VOICE_STANDARD) for f, t in phrases_standard)
    tasks.extend(generate(f, t, VOICE_HUMOR) for f, t in phrases_humor)
    await asyncio.gather(*tasks)
    print("All done! 标准版+幽默版全部生成完毕")


if __name__ == "__main__":
    if sys.platform == "win32":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(main())
