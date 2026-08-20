package com.hoshinoriji.miuihomelandscape;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.hoshinoriji.miuihomelandscape.core.ModuleSettings;
import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import com.hoshinoriji.miuihomelandscape.ui.LiquidBackgroundView;
import com.hoshinoriji.miuihomelandscape.ui.LiquidGlassDrawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Responsive blue-white liquid-glass control center for the vili module. */
public final class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(20, 118, 242);
    private static final int BLUE_DARK = Color.rgb(20, 72, 132);
    private static final int TEXT = Color.rgb(18, 52, 84);
    private static final int SECONDARY = Color.rgb(78, 112, 143);

    private final ExecutorService appLoader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mihl-settings-apps");
        thread.setDaemon(true);
        return thread;
    });
    private final List<AppEntry> allApps = new ArrayList<>();
    private final Set<String> enabledComponents = new HashSet<>();
    private final Set<String> pendingComponents = new HashSet<>();
    private final Map<String, Switch> visibleAppSwitches = new HashMap<>();
    private final Map<String, String> toggleNonces = new HashMap<>();

    private TextView statusView;
    private TextView appSummary;
    private LinearLayout appRows;
    private EditText appSearch;
    private final TextView[] rotationSegments = new TextView[3];
    private final TextView[] dockAppearanceSegments = new TextView[3];
    private final TextView[] dockSizeSegments = new TextView[3];
    private Switch recentsSwitch;
    private Switch hideGestureSwitch;
    private Switch dimWallpaperSwitch;
    private Switch labelsSwitch;
    private Switch dockEnabledSwitch;
    private Switch allowUnsupportedSwitch;
    private BroadcastReceiver resultReceiver;
    private boolean binding;
    private boolean appStatesReady;
    private boolean appsLoaded;
    private int rotationMode;
    private int dockAppearance = ModuleSettings.DOCK_APPEARANCE_AUTO;
    private int dockSize = ModuleSettings.DOCK_SIZE_STANDARD;
    private long lastPongAt;
    private String pendingNonce;
    private String appStateNonce;
    private String currentQuery = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        registerResultReceiver();
        setContentView(buildContent());
        configureLightSystemBars();
        bindSettings(ModuleSettings.load(this));
        loadLauncherApps();
        pingHome();
        queryAppStates();
    }

    @Override protected void onResume() {
        super.onResume();
        if (statusView != null) queryAppStates();
    }

    @Override protected void onDestroy() {
        if (resultReceiver != null) {
            try { unregisterReceiver(resultReceiver); }
            catch (Throwable ignored) {}
            resultReceiver = null;
        }
        appLoader.shutdownNow();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarDividerColor(Color.TRANSPARENT);
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(false);
        window.setDecorFitsSystemWindows(false);
    }

    private void configureLightSystemBars() {
        View decor = getWindow().getDecorView();
        if (decor == null) return;
        WindowInsetsController controller = decor.getWindowInsetsController();
        if (controller != null) {
            int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(lightBars, lightBars);
        }
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new LiquidBackgroundView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(12), dp(18), dp(18));
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(dp(18) + bars.left, dp(12) + bars.top,
                    dp(18) + bars.right, dp(18) + bars.bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(8), dp(4), dp(8), dp(12));
        TextView eyebrow = text("VILI CONTROL CENTER", 11, BLUE);
        eyebrow.setLetterSpacing(0.13f);
        header.addView(eyebrow, matchWrap());
        TextView title = text("横屏桌面", 30, TEXT);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, matchWrap());
        TextView subtitle = text("MIUI Home · Android 13 · Liquid Glass", 13, SECONDARY);
        subtitle.setPadding(0, dp(2), 0, dp(7));
        header.addView(subtitle, matchWrap());
        statusView = text("正在连接桌面进程…", 13, Color.rgb(180, 70, 55));
        header.addView(statusView, matchWrap());
        shell.addView(header, matchWrap());

        boolean wide = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (wide) {
            LinearLayout columns = new LinearLayout(this);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            shell.addView(columns, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            ScrollView settingsScroll = scrollFor(buildSettingsColumn());
            ScrollView appsScroll = scrollFor(buildAppsColumn());
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.45f);
            left.rightMargin = dp(7);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.55f);
            right.leftMargin = dp(7);
            columns.addView(settingsScroll, left);
            columns.addView(appsScroll, right);
        } else {
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.addView(buildSettingsColumn(), matchWrap());
            content.addView(buildAppsColumn(), matchWrap());
            ScrollView scroll = scrollFor(content);
            shell.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return root;
    }

    private LinearLayout buildSettingsColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(2), dp(2), dp(2), dp(18));

        LinearLayout connection = glassCard("连接", "模块状态与桌面进程握手");
        TextView ping = glassButton("重新检测模块");
        ping.setOnClickListener(v -> pingHome());
        connection.addView(ping, buttonLp());
        column.addView(connection, cardLp());

        LinearLayout rotation = glassCard("显示与旋转", "横竖屏切换由单一会话状态机管理");
        rotation.addView(buildRotationSegments(), matchWrap());
        labelsSwitch = addSwitchRow(rotation, "显示应用名称", "横屏网格显示图标标签", true);
        dimWallpaperSwitch = addSwitchRow(rotation, "增强壁纸对比", "用轻微暗层提升文字可读性", true);
        hideGestureSwitch = addSwitchRow(rotation, "隐藏手势提示条", "仅横屏主页生效，退出后恢复", false);
        column.addView(rotation, cardLp());

        LinearLayout dock = glassCard("Dock", "无色透明玻璃悬浮栏 · 9 个位置");
        dockEnabledSwitch = addSwitchRow(dock, "启用 Dock", "关闭后网格自动使用释放的空间", true);
        dock.addView(buildDockChoice("玻璃外观", "自动会根据壁纸亮度保持图标清晰",
                new String[]{"自动", "常规", "透明"}, dockAppearanceSegments, true), matchWrap());
        dock.addView(buildDockChoice("Dock 尺寸", "同时调整栏高、宽度与图标大小",
                new String[]{"紧凑", "标准", "大号"}, dockSizeSegments, false), matchWrap());
        column.addView(dock, cardLp());

        LinearLayout recents = glassCard("后台与系统", "优先横屏呈现，异常时自动退回 MIUI");
        recentsSwitch = addSwitchRow(recents, "横屏后台任务", "任务卡、单项关闭与全部清理", true);
        column.addView(recents, cardLp());

        LinearLayout data = glassCard("布局数据", "应用由开关逐个加入或移除");
        TextView reset = glassButton("恢复默认横屏布局");
        reset.setTextColor(Color.rgb(193, 45, 64));
        reset.setOnClickListener(v -> confirmResetLayout());
        data.addView(reset, buttonLp());
        column.addView(data, cardLp());

        LinearLayout compatibility = glassCard("兼容性保护",
                "锁定 vili、Android 13 与 MIUI Home 439126764");
        allowUnsupportedSwitch = addSwitchRow(compatibility, "允许未验证系统",
                "仅用于调试，版本不匹配可能导致桌面崩溃", false);
        allowUnsupportedSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (!checked) {
                settingsChanged();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("确认绕过兼容性保护？")
                    .setMessage("系统或桌面版本不匹配时，可能反复崩溃。")
                    .setPositiveButton("仍然启用", (dialog, which) -> settingsChanged())
                    .setNegativeButton("取消", (dialog, which) -> setCheckedSilently(
                            allowUnsupportedSwitch, false))
                    .setOnCancelListener(dialog -> setCheckedSilently(
                            allowUnsupportedSwitch, false))
                    .show();
        });
        column.addView(compatibility, cardLp());
        return column;
    }

    private LinearLayout buildAppsColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(2), dp(2), dp(2), dp(18));
        LinearLayout apps = glassCard("添加应用", "开关决定该应用是否出现在横屏模式");

        appSearch = new EditText(this);
        appSearch.setSingleLine(true);
        appSearch.setHint("搜索应用或包名");
        appSearch.setHintTextColor(Color.rgb(115, 145, 170));
        appSearch.setTextColor(TEXT);
        appSearch.setTextSize(15);
        appSearch.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setColor(0xAFFFFFFF);
        searchBackground.setCornerRadius(dp(18));
        searchBackground.setStroke(dp(1), 0xB8FFFFFF);
        appSearch.setBackground(searchBackground);
        appSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                renderApps();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        searchLp.topMargin = dp(8);
        apps.addView(appSearch, searchLp);

        appSummary = text("正在读取应用与横屏布局…", 13, SECONDARY);
        appSummary.setPadding(dp(2), dp(10), dp(2), dp(7));
        apps.addView(appSummary, matchWrap());
        appRows = new LinearLayout(this);
        appRows.setOrientation(LinearLayout.VERTICAL);
        apps.addView(appRows, matchWrap());
        column.addView(apps, cardLp());
        return column;
    }

    private View buildRotationSegments() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setPadding(0, dp(10), 0, dp(8));
        String[] labels = {"自动", "强制横屏", "跟随系统"};
        for (int index = 0; index < labels.length; index++) {
            final int value = index;
            TextView segment = text(labels[index], 13, TEXT);
            segment.setGravity(Gravity.CENTER);
            segment.setClickable(true);
            segment.setFocusable(true);
            segment.setOnClickListener(v -> {
                rotationMode = value;
                updateRotationSegments();
                settingsChanged();
            });
            rotationSegments[index] = segment;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, dp(38), 1f);
            if (index > 0) lp.leftMargin = dp(6);
            group.addView(segment, lp);
        }
        updateRotationSegments();
        return group;
    }

    private void updateRotationSegments() {
        for (int index = 0; index < rotationSegments.length; index++) {
            TextView segment = rotationSegments[index];
            if (segment == null) continue;
            boolean selected = index == rotationMode;
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected ? BLUE : 0x66FFFFFF);
            background.setCornerRadius(dp(16));
            background.setStroke(dp(1), selected ? 0x99FFFFFF : 0x88FFFFFF);
            segment.setBackground(background);
            segment.setTextColor(selected ? Color.WHITE : TEXT);
        }
    }

    private View buildDockChoice(String title, String summary, String[] labels,
            TextView[] targets, boolean appearanceChoice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(4));
        row.addView(text(title, 15, TEXT), matchWrap());
        TextView description = text(summary, 12, SECONDARY);
        description.setPadding(0, dp(2), 0, dp(7));
        row.addView(description, matchWrap());
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < labels.length; index++) {
            final int value = index;
            TextView segment = text(labels[index], 13, TEXT);
            segment.setGravity(Gravity.CENTER);
            segment.setClickable(true);
            segment.setFocusable(true);
            segment.setOnClickListener(v -> {
                if (appearanceChoice) dockAppearance = value;
                else dockSize = value;
                updateDockSegments();
                settingsChanged();
            });
            targets[index] = segment;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (index > 0) lp.leftMargin = dp(6);
            group.addView(segment, lp);
        }
        row.addView(group, matchWrap());
        return row;
    }

    private void updateDockSegments() {
        updateSegments(dockAppearanceSegments, dockAppearance);
        updateSegments(dockSizeSegments, dockSize);
    }

    private void updateSegments(TextView[] segments, int selectedIndex) {
        for (int index = 0; index < segments.length; index++) {
            TextView segment = segments[index];
            if (segment == null) continue;
            boolean selected = index == selectedIndex;
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected ? BLUE : 0x66FFFFFF);
            background.setCornerRadius(dp(16));
            background.setStroke(dp(1), selected ? 0x99FFFFFF : 0x88FFFFFF);
            segment.setBackground(background);
            segment.setTextColor(selected ? Color.WHITE : TEXT);
        }
    }

    private void bindSettings(ModuleSettings settings) {
        if (settings == null) return;
        binding = true;
        rotationMode = settings.rotationMode();
        updateRotationSegments();
        recentsSwitch.setChecked(settings.recentsEnabled());
        hideGestureSwitch.setChecked(settings.hideGestureHandle());
        dimWallpaperSwitch.setChecked(settings.dimWallpaper());
        labelsSwitch.setChecked(settings.labelsEnabled());
        dockEnabledSwitch.setChecked(settings.dockEnabled());
        dockAppearance = settings.dockAppearance();
        dockSize = settings.dockSize();
        updateDockSegments();
        allowUnsupportedSwitch.setChecked(settings.allowUnsupported());
        binding = false;
    }

    private void settingsChanged() {
        if (binding || recentsSwitch == null || dockEnabledSwitch == null) return;
        Intent command = command(LandscapeBridge.ACTION_SETTINGS_UPDATE);
        pendingNonce = UUID.randomUUID().toString();
        lastPongAt = 0L;
        command.putExtra(LandscapeBridge.EXTRA_NONCE, pendingNonce);
        command.putExtra(LandscapeBridge.EXTRA_ROTATION_MODE, rotationMode);
        command.putExtra(LandscapeBridge.EXTRA_GRID_COLUMNS, LandscapeBridge.GRID_COLUMNS);
        command.putExtra(LandscapeBridge.EXTRA_GRID_ROWS, LandscapeBridge.GRID_ROWS);
        command.putExtra(LandscapeBridge.EXTRA_DOCK_SLOTS, LandscapeBridge.DOCK_SLOTS);
        command.putExtra(LandscapeBridge.EXTRA_RECENTS_ENABLED, recentsSwitch.isChecked());
        command.putExtra(LandscapeBridge.EXTRA_HIDE_GESTURE_HANDLE,
                hideGestureSwitch.isChecked());
        command.putExtra(LandscapeBridge.EXTRA_DIM_WALLPAPER, dimWallpaperSwitch.isChecked());
        command.putExtra(LandscapeBridge.EXTRA_LABELS_ENABLED, labelsSwitch.isChecked());
        command.putExtra(LandscapeBridge.EXTRA_DOCK_ENABLED, dockEnabledSwitch.isChecked());
        command.putExtra(LandscapeBridge.EXTRA_DOCK_APPEARANCE, dockAppearance);
        command.putExtra(LandscapeBridge.EXTRA_DOCK_SIZE, dockSize);
        command.putExtra(LandscapeBridge.EXTRA_ALLOW_UNSUPPORTED,
                allowUnsupportedSwitch.isChecked());
        ModuleSettings.updateFromIntent(this, command);
        sendBroadcast(command);
        setStatus(false, "设置已发送，等待桌面确认…");
        scheduleAckTimeout(pendingNonce);
    }

    private void pingHome() {
        lastPongAt = 0L;
        pendingNonce = UUID.randomUUID().toString();
        setStatus(false, "正在连接 com.miui.home…");
        Intent ping = command(LandscapeBridge.ACTION_PING);
        ping.putExtra(LandscapeBridge.EXTRA_NONCE, pendingNonce);
        sendBroadcast(ping);
        scheduleAckTimeout(pendingNonce);
    }

    private void queryAppStates() {
        appStatesReady = false;
        appStateNonce = UUID.randomUUID().toString();
        Intent query = command(LandscapeBridge.ACTION_QUERY_APP_STATES);
        query.putExtra(LandscapeBridge.EXTRA_NONCE, appStateNonce);
        sendBroadcast(query);
        if (appRows != null) renderApps();
    }

    private void toggleApp(AppEntry entry, boolean enabled) {
        String encoded = entry.key.encode();
        if (pendingComponents.contains(encoded)) return;
        pendingComponents.add(encoded);
        Switch control = visibleAppSwitches.get(encoded);
        if (control != null) control.setEnabled(false);
        String nonce = UUID.randomUUID().toString();
        toggleNonces.put(encoded, nonce);
        Intent command = command(LandscapeBridge.ACTION_SET_APP_ENABLED);
        command.putExtra(LandscapeBridge.EXTRA_NONCE, nonce);
        command.putExtra(LandscapeBridge.EXTRA_COMPONENT_KEYS, encoded);
        command.putExtra(LandscapeBridge.EXTRA_ENABLED, enabled);
        sendBroadcast(command);
        if (statusView != null) {
            statusView.postDelayed(() -> {
                if (!pendingComponents.remove(encoded) || isFinishing()) return;
                toggleNonces.remove(encoded);
                queryAppStates();
                Toast.makeText(this, "桌面未及时确认，已重新同步", Toast.LENGTH_SHORT).show();
            }, 2600L);
        }
    }

    private void scheduleAckTimeout(String nonce) {
        statusView.postDelayed(() -> {
            if (lastPongAt == 0L && nonce != null && nonce.equals(pendingNonce)
                    && !isFinishing()) {
                setStatus(false, "未收到桌面响应；请在 LSPosed 启用模块后重启桌面");
            }
        }, 1800L);
    }

    private void registerResultReceiver() {
        resultReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                if (LandscapeBridge.ACTION_PONG.equals(action)) {
                    handlePong(intent);
                } else if (LandscapeBridge.ACTION_APP_STATES_RESULT.equals(action)) {
                    handleAppStates(intent);
                } else if (LandscapeBridge.ACTION_APP_TOGGLE_RESULT.equals(action)) {
                    handleAppToggleResult(intent);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(LandscapeBridge.ACTION_PONG);
        filter.addAction(LandscapeBridge.ACTION_APP_STATES_RESULT);
        filter.addAction(LandscapeBridge.ACTION_APP_TOGGLE_RESULT);
        registerReceiver(resultReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    private void handlePong(Intent intent) {
        String nonce = intent.getStringExtra(LandscapeBridge.EXTRA_NONCE);
        if (pendingNonce == null || !pendingNonce.equals(nonce)) return;
        lastPongAt = System.currentTimeMillis();
        pendingNonce = null;
        String version = intent.getStringExtra(LandscapeBridge.EXTRA_VERSION);
        ModuleSettings applied = ModuleSettings.updateFromIntent(this, intent);
        bindSettings(applied);
        boolean profileEnabled = intent.getBooleanExtra(
                LandscapeBridge.EXTRA_PROFILE_ENABLED, false);
        String profile = intent.getStringExtra(LandscapeBridge.EXTRA_PROFILE_SUMMARY);
        if (profileEnabled) {
            setStatus(true, "模块已连接 · " + (version == null ? "设置已同步" : version));
        } else {
            setStatus(false, "兼容性门禁未通过"
                    + (profile == null ? "" : " · " + profile));
        }
    }

    private void handleAppStates(Intent intent) {
        String nonce = intent.getStringExtra(LandscapeBridge.EXTRA_NONCE);
        if (appStateNonce == null || !appStateNonce.equals(nonce)) return;
        appStateNonce = null;
        appStatesReady = intent.getBooleanExtra(LandscapeBridge.EXTRA_SUCCESS, false);
        enabledComponents.clear();
        ArrayList<String> values = intent.getStringArrayListExtra(
                LandscapeBridge.EXTRA_COMPONENT_KEYS);
        if (values != null) enabledComponents.addAll(values);
        pendingComponents.clear();
        toggleNonces.clear();
        renderApps();
    }

    private void handleAppToggleResult(Intent intent) {
        String encoded = intent.getStringExtra(LandscapeBridge.EXTRA_COMPONENT_KEYS);
        String nonce = intent.getStringExtra(LandscapeBridge.EXTRA_NONCE);
        if (encoded == null || nonce == null || !nonce.equals(toggleNonces.get(encoded))
                || !pendingComponents.remove(encoded)) return;
        toggleNonces.remove(encoded);
        boolean actual = intent.getBooleanExtra(LandscapeBridge.EXTRA_ENABLED, false);
        boolean success = intent.getBooleanExtra(LandscapeBridge.EXTRA_SUCCESS, false);
        if (actual) enabledComponents.add(encoded); else enabledComponents.remove(encoded);
        if (!success) Toast.makeText(this, "布局保存失败或桌面已满", Toast.LENGTH_SHORT).show();
        appStatesReady = true;
        renderApps();
    }

    private void loadLauncherApps() {
        appLoader.execute(() -> {
            List<AppEntry> found = enumerateLauncherApps();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allApps.clear();
                allApps.addAll(found);
                appsLoaded = true;
                renderApps();
            });
        });
    }

    private List<AppEntry> enumerateLauncherApps() {
        List<AppEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        if (launcherApps != null && users != null) {
            for (UserHandle profile : users.getUserProfiles()) {
                try {
                    long serial = users.getSerialNumberForUser(profile);
                    if (serial < 0L) continue;
                    for (LauncherActivityInfo info : launcherApps.getActivityList(null, profile)) {
                        ComponentKey key = new ComponentKey(info.getComponentName(), serial);
                        if (!seen.add(key.encode())) continue;
                        result.add(new AppEntry(key, info.getLabel(),
                                info.getBadgedIcon(getResources().getDisplayMetrics().densityDpi)));
                    }
                } catch (Throwable ignored) {
                    // Managed profiles may be hidden from a non-launcher settings application.
                }
            }
        }
        if (result.isEmpty()) {
            PackageManager pm = getPackageManager();
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            long serial = users == null ? 0L : users.getSerialNumberForUser(Process.myUserHandle());
            for (ResolveInfo info : pm.queryIntentActivities(query,
                    PackageManager.ResolveInfoFlags.of(0L))) {
                if (info.activityInfo == null) continue;
                ComponentKey key = new ComponentKey(info.activityInfo.packageName,
                        info.activityInfo.name, Math.max(0L, serial));
                if (!seen.add(key.encode())) continue;
                result.add(new AppEntry(key, info.loadLabel(pm), info.loadIcon(pm)));
            }
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort(Comparator.comparing(entry -> entry.label.toString(), collator));
        return result;
    }

    private void renderApps() {
        if (appRows == null || appSummary == null) return;
        appRows.removeAllViews();
        visibleAppSwitches.clear();
        if (!appsLoaded) {
            appSummary.setText("正在读取设备上的应用…");
            return;
        }
        if (!appStatesReady) {
            appSummary.setText(String.format(Locale.getDefault(),
                    "已找到 %d 个应用 · 等待桌面布局状态", allApps.size()));
        } else {
            appSummary.setText(String.format(Locale.getDefault(),
                    "横屏已启用 %d 个 · 共 %d 个应用",
                    enabledComponents.size(), allApps.size()));
        }
        String needle = currentQuery.trim().toLowerCase(Locale.ROOT);
        int visible = 0;
        for (AppEntry entry : allApps) {
            String haystack = (entry.label + " " + entry.key.packageName)
                    .toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !haystack.contains(needle)) continue;
            appRows.addView(buildAppRow(entry), matchWrap());
            visible++;
        }
        if (visible == 0) {
            TextView empty = text("没有匹配的应用", 14, SECONDARY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            appRows.addView(empty, matchWrap());
        }
    }

    private View buildAppRow(AppEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(9), dp(2), dp(9));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(entry.icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(entry.label.toString(), 15, TEXT);
        name.setSingleLine(true);
        labels.addView(name, matchWrap());
        TextView pkg = text(entry.key.packageName, 11, SECONDARY);
        pkg.setSingleLine(true);
        labels.addView(pkg, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String encoded = entry.key.encode();
        Switch toggle = styledSwitch();
        toggle.setContentDescription(entry.label + " 添加到横屏模式");
        toggle.setChecked(enabledComponents.contains(encoded));
        toggle.setEnabled(appStatesReady && !pendingComponents.contains(encoded));
        toggle.setOnCheckedChangeListener((button, checked) -> toggleApp(entry, checked));
        visibleAppSwitches.put(encoded, toggle);
        row.addView(toggle, wrapWrap());

        View divider = new View(this);
        divider.setBackgroundColor(0x32FFFFFF);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row, matchWrap());
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.leftMargin = dp(56);
        wrapper.addView(divider, dividerLp);
        return wrapper;
    }

    private LinearLayout glassCard(String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(15));
        card.setBackground(new LiquidGlassDrawable(dp(26)));
        card.setElevation(dp(7));
        TextView heading = text(title, 19, TEXT);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(heading, matchWrap());
        TextView detail = text(subtitle, 12, SECONDARY);
        detail.setPadding(0, dp(2), 0, dp(3));
        card.addView(detail, matchWrap());
        return card;
    }

    private Switch addSwitchRow(LinearLayout card, String title, String subtitle,
            boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(5));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, TEXT), matchWrap());
        labels.addView(text(subtitle, 11, SECONDARY), matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch control = styledSwitch();
        control.setChecked(defaultValue);
        control.setOnCheckedChangeListener((button, checked) -> settingsChanged());
        row.addView(control, wrapWrap());
        card.addView(row, matchWrap());
        return control;
    }

    private Switch styledSwitch() {
        Switch control = new Switch(this);
        int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        control.setTrackTintList(new ColorStateList(states,
                new int[]{Color.rgb(73, 157, 255), Color.rgb(163, 188, 207)}));
        control.setThumbTintList(new ColorStateList(states,
                new int[]{Color.WHITE, Color.rgb(244, 249, 252)}));
        return control;
    }

    private TextView glassButton(String label) {
        TextView button = text(label, 14, BLUE_DARK);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(new LiquidGlassDrawable(dp(17)));
        return button;
    }

    private void confirmResetLayout() {
        new AlertDialog.Builder(this)
                .setTitle("恢复默认横屏布局？")
                .setMessage("将重建横屏页面、文件夹和 Dock 排列，并重新导入可用应用。"
                        + "竖屏桌面不会改变。")
                .setPositiveButton("恢复默认", (dialog, which) -> {
                    sendBroadcast(command(LandscapeBridge.ACTION_RESET_LAYOUT));
                    Toast.makeText(this, "已发送重置请求", Toast.LENGTH_SHORT).show();
                    statusView.postDelayed(this::queryAppStates, 700L);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private Intent command(String action) {
        return new Intent(action).setPackage(LandscapeBridge.HOME_PKG);
    }

    @SuppressLint("SetTextI18n")
    private void setStatus(boolean active, String value) {
        if (statusView == null) return;
        statusView.setText((active ? "● " : "○ ") + value);
        statusView.setTextColor(active ? Color.rgb(31, 145, 102) : Color.rgb(180, 70, 55));
    }

    private void setCheckedSilently(Switch control, boolean checked) {
        binding = true;
        control.setChecked(checked);
        binding = false;
    }

    private ScrollView scrollFor(View content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        final ComponentKey key;
        final CharSequence label;
        final Drawable icon;

        AppEntry(ComponentKey key, CharSequence label, Drawable icon) {
            this.key = key;
            this.label = label == null ? key.packageName : label;
            this.icon = icon;
        }
    }
}
