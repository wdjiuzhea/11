package com.example.headsetkeyblocker

import android.accessibilityservice.AccessibilityService
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 内存里保存最近的按键记录，供 MainActivity 显示（同一进程）。 */
object KeyLog {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        lines.addFirst(line)
        while (lines.size > 40) lines.removeLast()
    }

    @Synchronized
    fun dump(): String =
        if (lines.isEmpty()) "（暂无记录：先开启无障碍服务，再按线控按钮）"
        else lines.joinToString("\n\n")
}

/**
 * 拦截来自有线耳机 / 转接头的线控按键。
 * 手机自带的物理音量键、电源键来自别的输入设备，不受影响。
 */
class HeadsetKeyBlockerService : AccessibilityService() {

    companion object {
        // 要拦截的按键（线控常见键）
        private val BLOCKED_KEYS = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_CALL,
        )

        // 设备名关键字（小写匹配）。名字对不上时，看 App 里的记录再增删。
        private val DEVICE_NAME_HINTS = listOf(
            "headset", "jack", "headphone", "earphone", "adapter", "usb-c", "usb audio"
        )

        // 可选：按 vendorId/productId 精确匹配，比名字更稳。
        private val DEVICE_IDS = emptySet<Pair<Int, Int>>()
    }

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val device = InputDevice.getDevice(event.deviceId)
        val block = event.keyCode in BLOCKED_KEYS && isHeadsetDevice(device)

        if (event.action == KeyEvent.ACTION_DOWN) {
            KeyLog.add(
                "${timeFmt.format(Date())}  ${KeyEvent.keyCodeToString(event.keyCode)}\n" +
                    "设备: ${device?.name}  vid=${device?.vendorId} pid=${device?.productId}\n" +
                    if (block) "→ 已拦截" else "→ 放行"
            )
        }

        // true = 消费事件；DOWN/UP 判定条件相同，不会卡键
        return block
    }

    private fun isHeadsetDevice(device: InputDevice?): Boolean {
        device ?: return false // null = 合成事件，不拦
        if ((device.vendorId to device.productId) in DEVICE_IDS) return true
        val name = device.name.lowercase()
        return DEVICE_NAME_HINTS.any { name.contains(it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
