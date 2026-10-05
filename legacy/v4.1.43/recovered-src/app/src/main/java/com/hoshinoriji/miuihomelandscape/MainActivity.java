package com.hoshinoriji.miuihomelandscape;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import com.hoshinoriji.miuihomelandscape.overlay.AppPickerDialog;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final String KEY_HIDE_GESTURE_HANDLE = "hide_gesture_handle";
    private static final String PREFS = "miui_home_landscape_module_settings";
    private BroadcastReceiver bridgeReceiver;
    private Switch hideGestureSwitch;
    private long lastPongAt;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerBridgeReceiver();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setGravity(1);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView title = new TextView(this);
        title.setText("MiuiHome Landscape");
        title.setTextColor(-1);
        title.setTextSize(22.0f);
        title.setGravity(17);
        root.addView(title, matchWrap());
        this.status = new TextView(this);
        this.status.setTextSize(14.0f);
        this.status.setGravity(17);
        this.status.setPadding(0, dp(8), 0, dp(10));
        root.addView(this.status, matchWrap());
        Button refresh = new Button(this);
        refresh.setText("重新检测模块状态");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                this.f$0.m6lambda$onCreate$0$comhoshinorijimiuihomelandscapeMainActivity(view);
            }
        });
        root.addView(refresh, matchWrap());
        addDivider(root, dp(14), dp(12));
        addInfoRow(root, "作用域", "com.miui.home");
        addInfoRow(root, "版本", MiuiHomeLandscapeModule.VERSION);
        addInfoRow(root, "路线", "竖屏保持 MIUI 原版，横屏使用独立 overlay 桌面");
        addDivider(root, dp(12), dp(10));
        this.hideGestureSwitch = new Switch(this);
        this.hideGestureSwitch.setText("横屏隐藏底部小白条");
        this.hideGestureSwitch.setTextColor(-1);
        this.hideGestureSwitch.setTextSize(15.0f);
        this.hideGestureSwitch.setPadding(0, dp(4), 0, dp(8));
        this.hideGestureSwitch.setChecked(readHideGestureHandle());
        this.hideGestureSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                this.f$0.m7lambda$onCreate$1$comhoshinorijimiuihomelandscapeMainActivity(compoundButton, z);
            }
        });
        root.addView(this.hideGestureSwitch, matchWrap());
        addDivider(root, dp(10), dp(10));
        TextView addTitle = new TextView(this);
        addTitle.setText("添加应用到横屏桌面");
        addTitle.setTextColor(-1);
        addTitle.setTextSize(17.0f);
        addTitle.setGravity(3);
        root.addView(addTitle, matchWrap());
        TextView tip = new TextView(this);
        tip.setText("这里只写入模块自己的横屏 overlay 布局，不会修改竖屏桌面。若点击后没有响应，请先打开一次 MIUI 桌面再回到这里。");
        tip.setTextColor(Color.parseColor("#A0A0A0"));
        tip.setTextSize(12.0f);
        tip.setPadding(0, dp(4), 0, dp(8));
        root.addView(tip, matchWrap());
        Button importAll = new Button(this);
        importAll.setText("补齐导入全部应用到横屏");
        importAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                this.f$0.m8lambda$onCreate$2$comhoshinorijimiuihomelandscapeMainActivity(view);
            }
        });
        root.addView(importAll, matchWrap());
        List<AppPickerDialog.Entry> apps = AppPickerDialog.loadAll(this);
        Collections.sort(apps, new Comparator<AppPickerDialog.Entry>() {
            @Override
            public int compare(AppPickerDialog.Entry a, AppPickerDialog.Entry b) {
                return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
            }
        });
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setCacheColorHint(0);
        list.setAdapter((ListAdapter) new AppAdapter(apps));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        setContentView(root);
        pingHome();
    }

    void m6lambda$onCreate$0$comhoshinorijimiuihomelandscapeMainActivity(View v) {
        pingHome();
    }

    void m7lambda$onCreate$1$comhoshinorijimiuihomelandscapeMainActivity(CompoundButton buttonView, boolean isChecked) {
        getSharedPreferences(PREFS, 0).edit().putBoolean("hide_gesture_handle", isChecked).apply();
        sendSettingsUpdate();
        Toast.makeText(this, isChecked ? "已请求横屏隐藏小白条" : "已请求横屏显示小白条", 0).show();
    }

    void m8lambda$onCreate$2$comhoshinorijimiuihomelandscapeMainActivity(View v) {
        requestImportAll();
    }

    @Override
    protected void onDestroy() {
        try {
            if (this.bridgeReceiver != null) {
                unregisterReceiver(this.bridgeReceiver);
            }
        } catch (Throwable th) {
        }
        this.bridgeReceiver = null;
        super.onDestroy();
    }

    public static boolean isModuleActive() {
        return false;
    }

    private void registerBridgeReceiver() {
        this.bridgeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                if (LandscapeBridge.ACTION_PONG.equals(action)) {
                    MainActivity.this.lastPongAt = System.currentTimeMillis();
                    String version = intent.getStringExtra(LandscapeBridge.EXTRA_VERSION);
                    boolean hideGesture = intent.getBooleanExtra("hide_gesture_handle", MainActivity.this.readHideGestureHandle());
                    MainActivity.this.syncHideGestureSwitch(hideGesture);
                    MainActivity.this.setStatus(true, "模块已激活，桌面进程已回应" + (version == null ? "" : " v" + version));
                    return;
                }
                if (LandscapeBridge.ACTION_ADD_RESULT.equals(action)) {
                    String msg = intent.getStringExtra(LandscapeBridge.EXTRA_MESSAGE);
                    Toast.makeText(MainActivity.this, msg == null ? "横屏添加请求已处理" : msg, 0).show();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(LandscapeBridge.ACTION_PONG);
        filter.addAction(LandscapeBridge.ACTION_ADD_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.bridgeReceiver, filter, 2);
        } else {
            registerReceiver(this.bridgeReceiver, filter);
        }
    }

    private void pingHome() {
        this.lastPongAt = 0L;
        setStatus(false, "正在检测桌面进程中的模块...");
        Intent ping = new Intent(LandscapeBridge.ACTION_PING);
        ping.setPackage("com.miui.home");
        sendBroadcast(ping);
        sendSettingsUpdate();
        this.status.postDelayed(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m9lambda$pingHome$3$comhoshinorijimiuihomelandscapeMainActivity();
            }
        }, 1600L);
    }

    void m9lambda$pingHome$3$comhoshinorijimiuihomelandscapeMainActivity() {
        if (this.lastPongAt == 0) {
            setStatus(false, "未收到桌面回应：请确认 LSPosed 已启用，并重启桌面进程");
        }
    }

    public void requestAddToLandscape(AppPickerDialog.Entry entry) {
        if (entry == null || entry.key == null) {
            return;
        }
        ComponentKey key = entry.key;
        Intent intent = new Intent(LandscapeBridge.ACTION_ADD_TO_LANDSCAPE);
        intent.setPackage("com.miui.home");
        intent.putExtra(LandscapeBridge.EXTRA_PACKAGE, key.packageName);
        intent.putExtra(LandscapeBridge.EXTRA_CLASS, key.className);
        intent.putExtra(LandscapeBridge.EXTRA_USER_SERIAL, key.userSerial);
        sendBroadcast(intent);
        Toast.makeText(this, "已发送添加请求", 0).show();
    }

    private void requestImportAll() {
        Intent intent = new Intent(LandscapeBridge.ACTION_IMPORT_ALL);
        intent.setPackage("com.miui.home");
        sendBroadcast(intent);
        Toast.makeText(this, "已发送补齐导入请求", 0).show();
    }

    public boolean readHideGestureHandle() {
        return getSharedPreferences(PREFS, 0).getBoolean("hide_gesture_handle", false);
    }

    public void syncHideGestureSwitch(boolean enabled) {
        getSharedPreferences(PREFS, 0).edit().putBoolean("hide_gesture_handle", enabled).apply();
        if (this.hideGestureSwitch == null || this.hideGestureSwitch.isChecked() == enabled) {
            return;
        }
        this.hideGestureSwitch.setOnCheckedChangeListener(null);
        this.hideGestureSwitch.setChecked(enabled);
        this.hideGestureSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                this.f$0.m10x6bcec18f(compoundButton, z);
            }
        });
    }

    void m10x6bcec18f(CompoundButton buttonView, boolean isChecked) {
        getSharedPreferences(PREFS, 0).edit().putBoolean("hide_gesture_handle", isChecked).apply();
        sendSettingsUpdate();
        Toast.makeText(this, isChecked ? "已请求横屏隐藏小白条" : "已请求横屏显示小白条", 0).show();
    }

    private void sendSettingsUpdate() {
        Intent intent = new Intent(LandscapeBridge.ACTION_SETTINGS_UPDATE);
        intent.setPackage("com.miui.home");
        intent.putExtra("hide_gesture_handle", readHideGestureHandle());
        sendBroadcast(intent);
    }

    public void setStatus(boolean active, String text) {
        int color;
        if (this.status == null) {
            return;
        }
        this.status.setText((active ? "[OK] " : "[--] ") + text);
        TextView textView = this.status;
        if (active) {
            color = Color.parseColor("#66BB6A");
        } else {
            color = Color.parseColor("#EF5350");
        }
        textView.setTextColor(color);
    }

    private void addInfoRow(LinearLayout linearLayout, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView lbl = new TextView(this);
        lbl.setText(label + ": ");
        lbl.setTextColor(Color.parseColor("#AAAAAA"));
        lbl.setTextSize(13.0f);
        lbl.setMinWidth(dp(70));
        row.addView(lbl, wrapWrap());
        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(Color.parseColor("#DDDDDD"));
        val.setTextSize(13.0f);
        row.addView(val, new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout.addView(row, matchWrap());
    }

    private void addDivider(LinearLayout parent, int top, int bottom) {
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = top;
        lp.bottomMargin = bottom;
        parent.addView(line, lp);
    }

    final class AppAdapter extends BaseAdapter {
        private final List<AppPickerDialog.Entry> apps;

        AppAdapter(List<AppPickerDialog.Entry> apps) {
            this.apps = apps;
        }

        @Override
        public int getCount() {
            return this.apps.size();
        }

        @Override
        public Object getItem(int position) {
            return this.apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                row = Row.create(MainActivity.this);
                convertView = row.view;
                convertView.setTag(row);
            } else {
                row = (Row) convertView.getTag();
            }
            final AppPickerDialog.Entry entry = this.apps.get(position);
            row.icon.setImageDrawable(entry.icon);
            row.label.setText(entry.label);
            row.pkg.setText(entry.key.packageName);
            row.add.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    this.f$0.m11xea372cba(entry, view);
                }
            });
            return convertView;
        }

        void m11xea372cba(AppPickerDialog.Entry entry, View v) {
            MainActivity.this.requestAddToLandscape(entry);
        }
    }

    private static final class Row {
        Button add;
        ImageView icon;
        TextView label;
        TextView pkg;
        View view;

        private Row() {
        }

        static Row create(Context ctx) {
            Row row = new Row();
            LinearLayout outer = new LinearLayout(ctx);
            outer.setOrientation(0);
            outer.setGravity(16);
            outer.setPadding(MainActivity.dp(ctx, 6), MainActivity.dp(ctx, 7), MainActivity.dp(ctx, 6), MainActivity.dp(ctx, 7));
            row.icon = new ImageView(ctx);
            int iconSize = MainActivity.dp(ctx, 42);
            outer.addView(row.icon, new LinearLayout.LayoutParams(iconSize, iconSize));
            LinearLayout texts = new LinearLayout(ctx);
            texts.setOrientation(1);
            texts.setPadding(MainActivity.dp(ctx, 12), 0, MainActivity.dp(ctx, 8), 0);
            row.label = new TextView(ctx);
            row.label.setTextColor(-1);
            row.label.setTextSize(14.0f);
            texts.addView(row.label, new LinearLayout.LayoutParams(-1, -2));
            row.pkg = new TextView(ctx);
            row.pkg.setTextColor(Color.parseColor("#888888"));
            row.pkg.setTextSize(11.0f);
            row.pkg.setSingleLine(true);
            texts.addView(row.pkg, new LinearLayout.LayoutParams(-1, -2));
            outer.addView(texts, new LinearLayout.LayoutParams(0, -2, 1.0f));
            row.add = new Button(ctx);
            row.add.setText("添加横屏");
            outer.addView(row.add, new LinearLayout.LayoutParams(-2, -2));
            row.view = outer;
            return row;
        }
    }

    private int dp(int value) {
        return dp(this, value);
    }

    public static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }
}
