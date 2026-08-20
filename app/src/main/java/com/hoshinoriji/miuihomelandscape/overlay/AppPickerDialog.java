package com.hoshinoriji.miuihomelandscape.overlay;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.hoshinoriji.miuihomelandscape.model.ComponentKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 全屏多选弹窗：读 LauncherApps 所有应用，支持搜索 + 多选。
 * 确认 → 回调 List<ComponentKey>。
 */
public class AppPickerDialog {

    public interface OnPick { void onPick(List<ComponentKey> picks); }

    public static Dialog show(Context ctx, OnPick cb) {
        List<Entry> all = loadAll(ctx);
        Collections.sort(all, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
            }
        });

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F0121212"));
        root.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16));

        TextView title = new TextView(ctx);
        title.setText("选择要添加的应用（可多选）");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(0, 0, 0, dp(ctx, 12));
        root.addView(title);

        EditText search = new EditText(ctx);
        search.setHint("搜索");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.parseColor("#888888"));
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Set<ComponentKey> picked = new HashSet<>();
        final Adapter adapter = new Adapter(ctx, all, picked);

        ListView list = new ListView(ctx);
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = dp(ctx, 8);
        listLp.bottomMargin = dp(ctx, 8);
        root.addView(list, listLp);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setView(root);
        b.setPositiveButton("添加", (d, w) -> {
            List<ComponentKey> out = new ArrayList<>(picked);
            cb.onPick(out);
        });
        b.setNegativeButton("取消", null);
        AlertDialog dlg = b.create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dlg.show();
        return dlg;
    }

    // ────── data ──────

    public static class Entry {
        public final ComponentKey key;
        public final CharSequence label;
        public final Drawable icon;
        public Entry(ComponentKey k, CharSequence l, Drawable i) {
            key = k; label = l; icon = i;
        }
    }

    public static List<Entry> loadAll(Context ctx) {
        List<Entry> out = new ArrayList<>();
        LauncherApps la = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
        if (la == null || um == null) return out;

        for (UserHandle u : um.getUserProfiles()) {
            long serial = um.getSerialNumberForUser(u);
            List<LauncherActivityInfo> list = la.getActivityList(null, u);
            if (list == null) continue;
            for (LauncherActivityInfo i : list) {
                ComponentKey k = new ComponentKey(i.getComponentName(), serial);
                CharSequence label = i.getLabel();
                Drawable icon = UniformIconDrawable.wrap(ctx, i.getBadgedIcon(
                        ctx.getResources().getDisplayMetrics().densityDpi));
                out.add(new Entry(k, label, icon));
            }
        }
        return out;
    }

    // ────── adapter ──────

    private static class Adapter extends BaseAdapter implements Filterable {
        private final Context ctx;
        private final List<Entry> all;
        private List<Entry> filtered;
        private final Set<ComponentKey> picked;
        private AppFilter filter;

        Adapter(Context ctx, List<Entry> all, Set<ComponentKey> picked) {
            this.ctx = ctx; this.all = all; this.filtered = all; this.picked = picked;
        }

        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int i) { return filtered.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                row = Row.create(ctx);
                convertView = row.view;
                convertView.setTag(row);
            } else {
                row = (Row) convertView.getTag();
            }
            Entry e = filtered.get(pos);
            row.icon.setImageDrawable(e.icon);
            row.label.setText(e.label);
            row.cb.setOnCheckedChangeListener(null);
            row.cb.setChecked(picked.contains(e.key));
            row.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) picked.add(e.key); else picked.remove(e.key);
            });
            convertView.setOnClickListener(v -> row.cb.toggle());
            return convertView;
        }

        @Override public Filter getFilter() {
            if (filter == null) filter = new AppFilter();
            return filter;
        }

        private class AppFilter extends Filter {
            @Override protected FilterResults performFiltering(CharSequence cs) {
                FilterResults r = new FilterResults();
                if (cs == null || cs.length() == 0) {
                    r.values = all; r.count = all.size();
                } else {
                    String q = cs.toString().toLowerCase(Locale.getDefault());
                    List<Entry> m = new ArrayList<>();
                    for (Entry e : all) {
                        String l = String.valueOf(e.label).toLowerCase(Locale.getDefault());
                        String p = e.key.packageName.toLowerCase(Locale.ROOT);
                        if (l.contains(q) || p.contains(q)) m.add(e);
                    }
                    r.values = m; r.count = m.size();
                }
                return r;
            }
            @SuppressWarnings("unchecked")
            @Override protected void publishResults(CharSequence cs, FilterResults rs) {
                filtered = (List<Entry>) rs.values;
                notifyDataSetChanged();
            }
        }
    }

    private static class Row {
        View view;
        ImageView icon;
        TextView label;
        CheckBox cb;
        static Row create(Context ctx) {
            Row r = new Row();
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            ll.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(ctx, 8);
            ll.setPadding(pad, pad, pad, pad);

            r.icon = new ImageView(ctx);
            int sz = dp(ctx, 40);
            ll.addView(r.icon, new LinearLayout.LayoutParams(sz, sz));

            r.label = new TextView(ctx);
            r.label.setTextColor(Color.WHITE);
            r.label.setTextSize(14);
            r.label.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ll.addView(r.label, lp);

            r.cb = new CheckBox(ctx);
            ll.addView(r.cb);

            r.view = ll;
            return r;
        }
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
