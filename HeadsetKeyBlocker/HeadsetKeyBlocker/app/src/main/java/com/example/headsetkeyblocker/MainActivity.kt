package com.example.headsetkeyblocker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val info = TextView(this).apply {
            text = "1. 点“打开无障碍设置”，开启 Headset Key Blocker\n" +
                "2. 插上耳机，按线控按钮，再点“刷新记录”\n" +
                "“已拦截”= 按键被吞掉；“放行”= 该设备没被识别为耳机，" +
                "需要按记录里的设备名调整关键字"
        }
        val open = Button(this).apply {
            text = "打开无障碍设置"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val refresh = Button(this).apply {
            text = "刷新记录"
            setOnClickListener { logView.text = KeyLog.dump() }
        }
        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(logView) }

        root.addView(info)
        root.addView(open)
        root.addView(refresh)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        logView.text = KeyLog.dump()
    }
}
