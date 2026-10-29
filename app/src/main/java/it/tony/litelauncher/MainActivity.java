package it.tony.litelauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher minimale per Android TV: una griglia di app, orologio, impostazioni.
 * Nessuna libreria esterna, nessun servizio in background, nessuna raccomandazione.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "launcher";
    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";

    static final class AppItem {
        final String pkg;
        final String cls;
        final CharSequence label;
        final Drawable image;
        final boolean banner;
        final boolean leanback;

        AppItem(String pkg, String cls, CharSequence label, Drawable image, boolean banner, boolean leanback) {
            this.pkg = pkg;
            this.cls = cls;
            this.label = label;
            this.image = image;
            this.banner = banner;
            this.leanback = leanback;
        }
    }

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<AppItem> all = new ArrayList<>();
    private final List<AppItem> visible = new ArrayList<>();

    private GridView grid;
    private AppAdapter adapter;
    private SharedPreferences prefs;
    private boolean firstLoad = true;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        grid = findViewById(R.id.grid);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener((parent, view, position, id) -> launch(visible.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showOptions(position);
            return true;
        });

        findViewById(R.id.btn_settings).setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
        findViewById(R.id.btn_hidden).setOnClickListener(v -> showHidden());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        reload();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Tasto Home premuto mentre il launcher è già aperto: torna alla prima app
        if (!visible.isEmpty()) grid.setSelection(0);
        grid.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // Siamo la Home: il tasto Indietro non deve chiudere il launcher
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            int pos = grid.getSelectedItemPosition();
            if (grid.hasFocus() && pos >= 0 && pos < visible.size()) {
                showOptions(pos);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        loader.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- caricamento app

    private void reload() {
        final PackageManager pm = getPackageManager();
        final String self = getPackageName();
        loader.execute(() -> {
            List<AppItem> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            seen.add(self);
            addApps(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, list, seen);
            addApps(pm, Intent.CATEGORY_LAUNCHER, false, list, seen);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                all.clear();
                all.addAll(list);
                applyOrder();
            });
        });
    }

    private static void addApps(PackageManager pm, String category, boolean leanback,
                                List<AppItem> out, Set<String> seen) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> infos = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || !seen.add(ai.packageName)) continue;
            Drawable img = null;
            try {
                img = ai.loadBanner(pm);
            } catch (Exception ignored) {
            }
            boolean isBanner = img != null;
            if (img == null) img = ri.loadIcon(pm);
            out.add(new AppItem(ai.packageName, ai.name, ri.loadLabel(pm), img, isBanner, leanback));
        }
    }

    private void applyOrder() {
        Set<String> hidden = getHidden();
        Map<String, AppItem> remaining = new HashMap<>();
        for (AppItem a : all) remaining.put(a.pkg, a);

        visible.clear();
        for (String pkg : getOrder()) {
            AppItem a = remaining.remove(pkg);
            if (a != null && !hidden.contains(pkg)) visible.add(a);
        }

        List<AppItem> rest = new ArrayList<>();
        for (AppItem a : all) {
            if (remaining.containsKey(a.pkg) && !hidden.contains(a.pkg)) rest.add(a);
        }
        Collections.sort(rest, (x, y) -> x.label.toString().compareToIgnoreCase(y.label.toString()));
        visible.addAll(rest);

        adapter.notifyDataSetChanged();
        if (firstLoad) {
            firstLoad = false;
            grid.requestFocus();
        }
    }

    // ---------------------------------------------------------------- preferenze

    private List<String> getOrder() {
        String raw = prefs.getString(KEY_ORDER, "");
        if (TextUtils.isEmpty(raw)) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private void saveOrder() {
        List<String> pkgs = new ArrayList<>();
        for (AppItem a : visible) pkgs.add(a.pkg);
        prefs.edit().putString(KEY_ORDER, TextUtils.join(",", pkgs)).apply();
    }

    private Set<String> getHidden() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, Collections.<String>emptySet()));
    }

    private void setHidden(Set<String> hidden) {
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
    }

    // ---------------------------------------------------------------- azioni

    private void launch(AppItem a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(a.pkg, a.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!safeStart(i)) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (fallback == null || !safeStart(fallback)) {
                Toast.makeText(this, "Impossibile aprire " + a.label, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void move(int from, int to) {
        if (from < 0 || from >= visible.size() || to < 0 || to >= visible.size() || from == to) return;
        AppItem a = visible.remove(from);
        visible.add(to, a);
        saveOrder();
        adapter.notifyDataSetChanged();
        grid.setSelection(to);
    }

    private void showOptions(final int position) {
        if (position < 0 || position >= visible.size()) return;
        final AppItem a = visible.get(position);
        String[] options = {
                "Sposta all'inizio",
                "Sposta a sinistra",
                "Sposta a destra",
                "Nascondi",
                "Info app"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            move(position, 0);
                            break;
                        case 1:
                            move(position, position - 1);
                            break;
                        case 2:
                            move(position, position + 1);
                            break;
                        case 3:
                            Set<String> hidden = getHidden();
                            hidden.add(a.pkg);
                            setHidden(hidden);
                            applyOrder();
                            break;
                        case 4:
                            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + a.pkg))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            break;
                    }
                })
                .show();
    }

    private void showHidden() {
        final Set<String> hidden = getHidden();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Nessuna app nascosta", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> pkgs = new ArrayList<>(hidden);
        Collections.sort(pkgs);
        Map<String, CharSequence> labels = new HashMap<>();
        for (AppItem a : all) labels.put(a.pkg, a.label);
        String[] names = new String[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) {
            CharSequence l = labels.get(pkgs.get(i));
            names[i] = (l != null ? l : pkgs.get(i)) + "  —  mostra";
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("App nascoste")
                .setItems(names, (dialog, which) -> {
                    hidden.remove(pkgs.get(which));
                    setHidden(hidden);
                    applyOrder();
                })
                .show();
    }

    // ---------------------------------------------------------------- griglia

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public Object getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_app, parent, false);
                view.setClipToOutline(true);
            }

            int w = grid.getColumnWidth();
            if (w > 0) {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                int h = w * 9 / 16;
                if (lp != null && lp.height != h) {
                    lp.height = h;
                    view.setLayoutParams(lp);
                }
            }

            AppItem a = visible.get(position);
            ImageView img = view.findViewById(R.id.img);
            TextView label = view.findViewById(R.id.label);
            img.setImageDrawable(a.image);
            view.setContentDescription(a.label);

            if (a.banner) {
                img.setPadding(0, 0, 0, 0);
                label.setVisibility(View.GONE);
            } else {
                int p = dp(10);
                img.setPadding(p, p, p, dp(30));
                label.setText(a.label);
                label.setVisibility(View.VISIBLE);
            }
            return view;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
