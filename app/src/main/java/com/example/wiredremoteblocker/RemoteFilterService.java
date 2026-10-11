package com.example.wiredremoteblocker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RemoteFilterService extends AccessibilityService {
    public static final String PREFS = "filter_config";
    public static final String ENABLED = "enabled";
    public static final String BLOCK_VOLUME = "block_volume";
    public static final String BLOCK_ASSIST = "block_assist";
    public static final String LAST_EVENT = "last_event";
    public static final String LAST_DEVICE = "last_device_token";
    public static final String LAST_DEVICE_LABEL = "last_device_label";
    public static final String TARGET_DEVICE = "target_device_token";
    public static final String TARGET_LABEL = "target_device_label";
    public static final String PHONE_DEVICE = "phone_device_token";
    public static final String PHONE_LABEL = "phone_device_label";
    public static final String RECENT_EVENTS = "recent_events";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean mediaKey = isMediaKey(code);
        boolean volumeKey = isVolumeKey(code);
        boolean assistKey = code == KeyEvent.KEYCODE_VOICE_ASSIST
                || code == KeyEvent.KEYCODE_ASSIST;
        if (!mediaKey && !volumeKey && !assistKey) return false;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        InputDevice device = event.getDevice();
        String token = identity(event, device);
        String deviceName = device == null ? "未知输入设备" : device.getName();
        String readableLabel = deviceName + " (id=" + event.getDeviceId() + ")";

        // 未明确指定耳机输入设备时，绝不屏蔽任何键。
        String selected = prefs.getString(TARGET_DEVICE, "");
        String phone = prefs.getString(PHONE_DEVICE, "");
        boolean selectedMatches = !selected.isEmpty() && selected.equals(token);
        boolean isPhoneDevice = !phone.isEmpty() && phone.equals(token);
        boolean consume = prefs.getBoolean(ENABLED, true)
                && DeviceUtils.hasWiredAudio(this)
                && selectedMatches && !isPhoneDevice
                && (mediaKey
                || (volumeKey && prefs.getBoolean(BLOCK_VOLUME, false))
                || (assistKey && prefs.getBoolean(BLOCK_ASSIST, true)));

        // 保持 DOWN 和 UP 的返回值一致，避免不完整按键流。
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
            String line = timestamp + " " + KeyEvent.keyCodeToString(code)
                    + "  " + readableLabel
                    + (consume ? "  [已拦截]" : "  [已放行]");
            String history = prefs.getString(RECENT_EVENTS, "");
            String[] old = history.isEmpty() ? new String[0] : history.split("\n");
            StringBuilder sb = new StringBuilder(line);
            for (int i = 0; i < old.length && i < 14; i++) sb.append('\n').append(old[i]);
            prefs.edit()
                    .putString(LAST_EVENT, line)
                    .putString(LAST_DEVICE, token)
                    .putString(LAST_DEVICE_LABEL, readableLabel)
                    .putString(RECENT_EVENTS, sb.toString())
                    .apply();
        }
        return consume;
    }

    // descriptor 在同一输入设备重连时通常比临时 deviceId 稳定。
    // 同一设备被 Android 合并时无法区分耳机和机身音量键。
    static String identity(KeyEvent event, InputDevice device) {
        if (event.getDeviceId() <= 0 || device == null) return "";
        String descriptor = device.getDescriptor();
        if (descriptor != null && !descriptor.isEmpty()) return "descriptor:" + descriptor;
        return "session-id:" + event.getDeviceId();
    }

    private static boolean isVolumeKey(int code) {
        return code == KeyEvent.KEYCODE_VOLUME_UP
                || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    private static boolean isMediaKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
                return true;
            default:
                return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
