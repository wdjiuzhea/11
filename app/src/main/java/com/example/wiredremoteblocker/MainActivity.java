package com.example.wiredremoteblocker;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public class MainActivity extends Activity {
    private static final int BLACK = Color.rgb(28, 40, 57);
    private static final int SECONDARY = Color.rgb(88, 99, 117);
    private static final int BLUE = Color.rgb(38, 107, 193);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AudioDeviceCallback audioCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            mainHandler.post(() -> refreshStatus());
        }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            mainHandler.post(() -> refreshStatus());
        }
    };
    private AudioManager audioManager;
    private TextView serviceState, headsetState, lastEvent, targetState, phoneState;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(RemoteFilterService.PREFS, MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 252));
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(20), dp(26), dp(20), dp(24));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        TextView title = text("耳机线控屏蔽器", 27, BLACK, true);
        content.addView(title);
        TextView subtitle = text("Wired Remote Blocker · 无需 Root", 13, SECONDARY, false);
        addWithTop(content, subtitle, 6);
        TextView lead = text("v2：先识别耳机线控与手机机身按键的来源；未指定耳机设备时不会屏蔽任何按键。", 15, SECONDARY, false);
        addWithTop(content, lead, 17);

        LinearLayout statusCard = card();
        serviceState = text("无障碍服务：检测中", 16, BLACK, true);
        statusCard.addView(serviceState);
        headsetState = text("有线音频设备：检测中", 14, SECONDARY, false);
        addWithTop(statusCard, headsetState, 9);
        addWithTop(content, statusCard, 23);

        Button settings = button("打开无障碍设置 · 启用屏蔽服务");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addWithTop(content, settings, 16);

        LinearLayout settingsCard = card();
        settingsCard.addView(text("屏蔽设置", 18, BLACK, true));
        Switch master = makeSwitch("启用过滤", "未选中耳机设备时，即使开启也不会拦截", RemoteFilterService.ENABLED, true);
        addWithTop(settingsCard, master, 14);
        Switch assist = makeSwitch("尝试屏蔽语音助手键", "部分机型会在应用收到事件前启动助手", RemoteFilterService.BLOCK_ASSIST, true);
        addWithTop(settingsCard, assist, 18);
        Switch volume = makeSwitch("同时屏蔽音量加减", "仅针对选择的耳机输入设备；若系统把来源合并则无法独立屏蔽", RemoteFilterService.BLOCK_VOLUME, false);
        addWithTop(settingsCard, volume, 18);
        addWithTop(content, settingsCard, 16);

        LinearLayout targetCard = card();
        targetCard.addView(text("按键来源识别（重要）", 18, BLACK, true));
        TextView guidance = text("步骤 1：插耳机，按一次手机机身音量键，立刻返回点击【记为手机按键】。\n步骤 2：触发耳机线控（轻按，或等误触），立刻返回点击【记为耳机线控】。\n步骤 3：只有两个来源不同，才可以只屏蔽耳机线控。", 14, SECONDARY, false);
        addWithTop(targetCard, guidance, 10);
        phoneState = text("机身按键：未记录", 14, SECONDARY, false);
        addWithTop(targetCard, phoneState, 10);
        targetState = text("耳机来源：未选择（全部放行）", 14, SECONDARY, false);
        addWithTop(targetCard, targetState, 10);
        Button phone = button("把最近一次按键记为手机机身按键");
        phone.setOnClickListener(v -> saveRecentDeviceAs(false));
        addWithTop(targetCard, phone, 10);
        Button earphone = button("把最近一次按键记为耳机线控");
        earphone.setOnClickListener(v -> saveRecentDeviceAs(true));
        addWithTop(targetCard, earphone, 10);
        Button clear = button("清除设备识别 / 停止按来源拦截");
        clear.setOnClickListener(v -> {
            prefs.edit().remove(RemoteFilterService.TARGET_DEVICE)
                .remove(RemoteFilterService.TARGET_LABEL)
                .remove(RemoteFilterService.PHONE_DEVICE)
                .remove(RemoteFilterService.PHONE_LABEL).apply();
            refreshStatus();
        });
        addWithTop(targetCard, clear, 10);
        addWithTop(content, targetCard, 16);

        LinearLayout logsCard = card();
        logsCard.addView(text("最近 15 条按键记录（设备名 / ID）", 18, BLACK, true));
        lastEvent = text("暂无记录", 13, SECONDARY, false);
        addWithTop(logsCard, lastEvent, 10);
        Button refresh = button("刷新状态 / 最近按键");
        refresh.setOnClickListener(v -> refreshStatus());
        addWithTop(logsCard, refresh, 12);
        addWithTop(content, logsCard, 16);

        TextView notice = text("安全说明：v2 未设置耳机输入来源时默认放行所有按键。若手机键与耳机事件显示同一来源，将拒绝同时指定：该机型无法用普通无障碍服务准确区分。杂音属于音频链路问题，此 App 无法消除。", 13, SECONDARY, false);
        addWithTop(content, notice, 18);
        setContentView(scroll);
    }

    private Switch makeSwitch(String label, String desc, String key, boolean defaultValue) {
        // 原生 Switch 不支持副标题：以换行显示说明文字。
        Switch sw = new Switch(this);
        sw.setText(label + "\n" + desc);
        sw.setTextColor(BLACK);
        sw.setTextSize(14);
        sw.setPadding(0, dp(3), 0, dp(3));
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(key, isChecked).apply());
        return sw;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(233, 236, 241));
        card.setBackground(bg);
        return card;
    }

    private Button button(String title) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BLUE);
        bg.setCornerRadius(dp(12));
        button.setBackground(bg);
        button.setMinimumHeight(dp(48));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void addWithTop(LinearLayout parent, View child, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onResume() {
        super.onResume();
        if (audioManager != null) audioManager.registerAudioDeviceCallback(audioCallback, mainHandler);
        refreshStatus();
    }

    @Override protected void onPause() {
        if (audioManager != null) audioManager.unregisterAudioDeviceCallback(audioCallback);
        super.onPause();
    }

    private void refreshStatus() {
        if (serviceState == null) return;
        boolean accessible = isServiceEnabled();
        serviceState.setText("无障碍服务：" + (accessible ? "已开启 ✓" : "未开启"));
        serviceState.setTextColor(accessible ? Color.rgb(24, 118, 76) : BLACK);
        boolean hasWired = DeviceUtils.hasWiredAudio(this);
        headsetState.setText("有线音频设备：" + (hasWired ? "已连接 ✓" : "未检测到"));
        lastEvent.setText(prefs.getString(RemoteFilterService.RECENT_EVENTS, "暂无记录。按一下机身键或耳机键，再点刷新。"));
        if (targetState != null) {
            String target = prefs.getString(RemoteFilterService.TARGET_DEVICE, "");
            targetState.setText(target.isEmpty() ? "耳机来源：未选择（全部放行）"
                    : "耳机来源：" + prefs.getString(RemoteFilterService.TARGET_LABEL, "未知") + " ✓");
        }
        if (phoneState != null) {
            String phone = prefs.getString(RemoteFilterService.PHONE_DEVICE, "");
            phoneState.setText(phone.isEmpty() ? "机身按键：未记录"
                    : "机身按键：" + prefs.getString(RemoteFilterService.PHONE_LABEL, "未知") + " ✓");
        }
    }

    private void saveRecentDeviceAs(boolean headset) {
        String token = prefs.getString(RemoteFilterService.LAST_DEVICE, "");
        String label = prefs.getString(RemoteFilterService.LAST_DEVICE_LABEL, "未知");
        if (token.isEmpty()) {
            android.widget.Toast.makeText(this, "未识别到有效的按键输入设备，请先按键", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String other = prefs.getString(headset ? RemoteFilterService.PHONE_DEVICE
                : RemoteFilterService.TARGET_DEVICE, "");
        if (!other.isEmpty() && other.equals(token)) {
            android.widget.Toast.makeText(this,
                "两个按键来源相同，无法安全地只拦截耳机。请停止软件拦截并检查转接头。",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (headset) {
            prefs.edit().putString(RemoteFilterService.TARGET_DEVICE, token)
                .putString(RemoteFilterService.TARGET_LABEL, label).apply();
        } else {
            prefs.edit().putString(RemoteFilterService.PHONE_DEVICE, token)
                .putString(RemoteFilterService.PHONE_LABEL, label).apply();
        }
        refreshStatus();
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                if (getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                        && RemoteFilterService.class.getName().equals(info.getResolveInfo().serviceInfo.name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
