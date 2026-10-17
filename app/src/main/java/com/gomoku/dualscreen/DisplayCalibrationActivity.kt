package com.gomoku.dualscreen

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.io.File

/** Operator observes physical panel marks; no enumeration order is treated as identity. */
class DisplayCalibrationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = BattleDisplays.calibrationKey
        val id = display?.displayId ?: return finish()
        var correction = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32); setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "↑ 基准上边 ↑\n左上                         右上\n\n逻辑显示 $id\n$key\n\n先在 0° 时给实体面板贴 A/B 与上边标记。\n每种状态都按原标记选本屏身份。\n调整箭头与标记对齐后保存；未知状态必须重新核验。"
            textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
        })
        root.addView(Button(this).apply {
            text = "方向补偿 0° / 180°"
            setOnClickListener { correction = 180 - correction; root.rotation = correction.toFloat() }
        })
        fun save(panel: String) {
            if (key.isEmpty() || key.contains("null") || key != BattleDisplays.calibrationKey) {
                Toast.makeText(this, "无法读取固件状态，不能保存标定", Toast.LENGTH_LONG).show(); return
            }
            val file = File(filesDir, "battle-display.json")
            val config = try { JSONObject(file.readText()) } catch (_: Exception) { JSONObject() }
            if (config.optString("fingerprint") != Build.FINGERPRINT) config.put("states", JSONObject())
            config.put("fingerprint", Build.FINGERPRINT)
            val states = config.optJSONObject("states") ?: JSONObject().also { config.put("states", it) }
            val state = states.optJSONObject(key) ?: JSONObject().also { states.put(key, it) }
            state.put(panel, JSONObject().put("displayId", id).put("rotation", correction)
                .put("displayName", display?.name))
            val atomic = android.util.AtomicFile(file)
            val output = atomic.startWrite()
            try { output.write(config.toString(2).toByteArray()); atomic.finishWrite(output) }
            catch (e: Exception) { atomic.failWrite(output); throw e }
            finish()
        }
        listOf("A" to "本实体屏是 A · 黑方", "B" to "本实体屏是 B · 白方").forEach { (panel, label) ->
            root.addView(Button(this).apply { text = label; setOnClickListener { save(panel) } })
        }
        root.addView(TextView(this).apply { text = "左下                         右下"; gravity = Gravity.CENTER })
        setContentView(root)
    }
}
