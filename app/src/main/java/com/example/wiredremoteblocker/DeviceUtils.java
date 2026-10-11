package com.example.wiredremoteblocker;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

public final class DeviceUtils {
    private DeviceUtils() { }

    // 只判断有线音频设备是否存在，无法在所有机型上可靠识别按键的来源。
    public static boolean hasWiredAudio(Context context) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return false;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
}
