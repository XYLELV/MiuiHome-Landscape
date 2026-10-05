package com.hoshinoriji.miuihomelandscape.overlay;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPickerDialog {

    public interface OnPick {
        void onPick(List<ComponentKey> list);
    }

    public static Dialog show(Context ctx, final OnPick cb) {
        List<Entry> all = loadAll(ctx);
        Collections.sort(all, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
            }
        });
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(1);
        root.setBackgroundColor(Color.parseColor("#F0121212"));
        root.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16));
        TextView title = new TextView(ctx);
        title.setText("选择要添加的应用（可多选）");
        title.setTextColor(-1);
        title.setTextSize(16.0f);
        title.setPadding(0, 0, 0, dp(ctx, 12));
        root.addView(title);
        EditText search = new EditText(ctx);
        search.setHint("搜索");
        search.setSingleLine(true);
        search.setTextColor(-1);
        search.setHintTextColor(Color.parseColor("#888888"));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        final Set<ComponentKey> picked = new HashSet<>();
        final Adapter adapter = new Adapter(ctx, all, picked);
        ListView list = new ListView(ctx);
        list.setAdapter((ListAdapter) adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        listLp.topMargin = dp(ctx, 8);
        listLp.bottomMargin = dp(ctx, 8);
        root.addView(list, listLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setView(root);
        b.setPositiveButton("添加", new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                AppPickerDialog.lambda$show$0(picked, cb, dialogInterface, i);
            }
        });
        b.setNegativeButton("取消", (DialogInterface.OnClickListener) null);
        AlertDialog dlg = b.create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(-1, -1);
        }
        dlg.show();
        return dlg;
    }

    static void lambda$show$0(Set picked, OnPick cb, DialogInterface d, int w) {
        List<ComponentKey> out = new ArrayList<>(picked);
        cb.onPick(out);
    }

    public static class Entry {
        public final Drawable icon;
        public final ComponentKey key;
        public final CharSequence label;

        public Entry(ComponentKey k, CharSequence l, Drawable i) {
            this.key = k;
            this.label = l;
            this.icon = i;
        }
    }

    public static List<Entry> loadAll(Context ctx) {
        List<Entry> out = new ArrayList<>();
        LauncherApps la = (LauncherApps) ctx.getSystemService("launcherapps");
        UserManager um = (UserManager) ctx.getSystemService("user");
        if (la == null || um == null) {
            return out;
        }
        for (UserHandle u : um.getUserProfiles()) {
            long serial = um.getSerialNumberForUser(u);
            List<LauncherActivityInfo> list = la.getActivityList(null, u);
            if (list != null) {
                for (LauncherActivityInfo i : list) {
                    ComponentKey k = new ComponentKey(i.getComponentName(), serial);
                    CharSequence label = i.getLabel();
                    Drawable icon = i.getBadgedIcon(0);
                    out.add(new Entry(k, label, icon));
                }
            }
        }
        return out;
    }

    static class Adapter extends BaseAdapter implements Filterable {
        private final List<Entry> all;
        private final Context ctx;
        private AppFilter filter;
        private List<Entry> filtered;
        private final Set<ComponentKey> picked;

        Adapter(Context ctx, List<Entry> all, Set<ComponentKey> picked) {
            this.ctx = ctx;
            this.all = all;
            this.filtered = all;
            this.picked = picked;
        }

        @Override
        public int getCount() {
            return this.filtered.size();
        }

        @Override
        public Object getItem(int i) {
            return this.filtered.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            final Row row;
            if (convertView == null) {
                row = Row.create(this.ctx);
                convertView = row.view;
                convertView.setTag(row);
            } else {
                row = (Row) convertView.getTag();
            }
            final Entry e = this.filtered.get(pos);
            row.icon.setImageDrawable(e.icon);
            row.label.setText(e.label);
            row.cb.setOnCheckedChangeListener(null);
            row.cb.setChecked(this.picked.contains(e.key));
            row.cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                    this.f$0.m24xd77431b5(e, compoundButton, z);
                }
            });
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    row.cb.toggle();
                }
            });
            return convertView;
        }

        void m24xd77431b5(Entry e, CompoundButton buttonView, boolean isChecked) {
            Set<ComponentKey> set = this.picked;
            ComponentKey componentKey = e.key;
            if (isChecked) {
                set.add(componentKey);
            } else {
                set.remove(componentKey);
            }
        }

        @Override
        public Filter getFilter() {
            if (this.filter == null) {
                this.filter = new AppFilter();
            }
            return this.filter;
        }

        private class AppFilter extends Filter {
            private AppFilter() {
            }

            @Override
            protected Filter.FilterResults performFiltering(CharSequence cs) {
                Filter.FilterResults r = new Filter.FilterResults();
                if (cs == null || cs.length() == 0) {
                    r.values = Adapter.this.all;
                    r.count = Adapter.this.all.size();
                } else {
                    String q = cs.toString().toLowerCase();
                    List<Entry> m = new ArrayList<>();
                    for (Entry e : Adapter.this.all) {
                        String l = String.valueOf(e.label).toLowerCase();
                        String p = e.key.packageName.toLowerCase();
                        if (l.contains(q) || p.contains(q)) {
                            m.add(e);
                        }
                    }
                    r.values = m;
                    r.count = m.size();
                }
                return r;
            }

            @Override
            protected void publishResults(CharSequence cs, Filter.FilterResults rs) {
                Adapter.this.filtered = (List) rs.values;
                Adapter.this.notifyDataSetChanged();
            }
        }
    }

    static class Row {
        CheckBox cb;
        ImageView icon;
        TextView label;
        View view;

        private Row() {
        }

        static Row create(Context ctx) {
            Row r = new Row();
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(0);
            ll.setGravity(16);
            int pad = AppPickerDialog.dp(ctx, 8);
            ll.setPadding(pad, pad, pad, pad);
            r.icon = new ImageView(ctx);
            int sz = AppPickerDialog.dp(ctx, 40);
            ll.addView(r.icon, new LinearLayout.LayoutParams(sz, sz));
            r.label = new TextView(ctx);
            r.label.setTextColor(-1);
            r.label.setTextSize(14.0f);
            r.label.setPadding(AppPickerDialog.dp(ctx, 12), 0, AppPickerDialog.dp(ctx, 12), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            ll.addView(r.label, lp);
            r.cb = new CheckBox(ctx);
            ll.addView(r.cb);
            r.view = ll;
            return r;
        }
    }

    public static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
