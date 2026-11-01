package it.tony.litelauncher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lite Launcher: cartelle piatte con apertura a zoom, pulsante TV e meteo locale (Open-Meteo).
 * Interfaccia costruita interamente in codice, nessuna libreria esterna.
 */
public class MainActivity extends Activity {

    // ------------------------------------------------------------------ costanti

    /** Colori: {sopra, sotto, bordo 3D, accento} */
    private static final int[][] COLORS = {
            {0xFF3C3489, 0xFF26215C, 0xFF16123A, 0xFF7F77DD},
            {0xFF085041, 0xFF04342C, 0xFF021F1A, 0xFF5DCAA5},
            {0xFF72243E, 0xFF4B1528, 0xFF2E0C18, 0xFFED93B1},
            {0xFF0C447C, 0xFF042C53, 0xFF021A33, 0xFF85B7EB},
            {0xFF633806, 0xFF412402, 0xFF271500, 0xFFFAC775},
            {0xFF444441, 0xFF2C2C2A, 0xFF1A1A19, 0xFFD3D1C7},
            {0xFF791F1F, 0xFF501313, 0xFF300A0A, 0xFFF09595},
            {0xFF27500A, 0xFF173404, 0xFF0C1E02, 0xFF97C459},
    };
    private static final String[] COLOR_NAMES =
            {"Viola", "Verde acqua", "Rosa", "Blu", "Ambra", "Grigio", "Rosso", "Verde"};
    private static final int[] GREY = {0xFF3A4150, 0xFF262B35, 0xFF161A21, 0xFFD3D1C7};
    private static final int[] DARK = {0xFF232937, 0xFF1A1F29, 0xFF10131A, 0xFFD3D1C7};

    private static final String[] ICONS =
            {"🌐", "🖥️", "🎧", "📡", "📅", "⚙️", "🎬", "🎮", "⭐", "📁", "👶", "⚽"};

    private static final String TV_PACKAGE = "org.droidtv.playtv";

    /** Cartelle iniziali: chiave, nome, icona, colore, pacchetti riconosciuti automaticamente */
    private static final Object[][] DEFAULTS = {
            {"internet", "Streaming Internet", "🌐", 0, new String[]{
                    "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
                    "com.apple.atve.androidtv.appletv", "com.nowtv.it", "it.rainet.androidtv",
                    "it.mediaset.infinitytv", "it.telecomitalia.cubovision", "com.dazn",
                    "com.discovery.dplay", "com.google.android.youtube.tv", "tv.pluto.android",
                    "tv.wuaki.apptv", "com.google.android.videos", "com.nousguide.android.rbtv",
                    "com.ted.android.tv", "com.paramount.android.pplus", "com.wbd.stream"}},
            {"lan", "Streaming LAN", "🖥️", 1, new String[]{
                    "org.droidtv.contentexplorer", "org.videolan.vlc", "com.mxtech.videoplayer.ad",
                    "org.droidtv.dlna", "com.plexapp.android", "org.xbmc.kodi", "org.jellyfin.androidtv"}},
            {"audio", "Audio", "🎧", 2, new String[]{
                    "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.phorus.playfi.tv",
                    "deezer.android.tv", "com.amazon.music.tv", "tunein.player"}},
            {"tv", "Visione TV", "📡", 3, new String[]{
                    "org.droidtv.playtv", "org.droidtv.channels", "org.droidtv.xtv"}},
            {"guide", "Guida TV", "📅", 4, new String[]{
                    "org.droidtv.epg", "org.droidtv.epgdata"}},
            {"settings", "Impostazioni", "⚙️", 5, new String[]{
                    "org.droidtv.settings", "com.android.tv.settings", "com.android.vending"}},
    };

    // ------------------------------------------------------------------ modello

    static final class Folder {
        String key;
        String name;
        String icon;
        int color;
        List<String> apps = new ArrayList<>();
    }

    static final class AppItem {
        final String pkg;
        final CharSequence label;
        final Drawable image;
        final boolean banner;
        final Intent launch;

        AppItem(String pkg, CharSequence label, Drawable image, boolean banner, Intent launch) {
            this.pkg = pkg;
            this.label = label;
            this.image = image;
            this.banner = banner;
            this.launch = launch;
        }
    }

    // ------------------------------------------------------------------ stato

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private final List<Folder> folders = new ArrayList<>();
    private Map<String, AppItem> apps = new HashMap<>();

    private SkyView sky;
    private LinearLayout content;
    private LinearLayout foldersBox;
    private TextView greeting;
    private FrameLayout overlay;
    private LinearLayout panel;

    private TextView weatherLine;
    private TextView favLabel;
    private LinearLayout favBox;
    private final List<String> favs = new ArrayList<>();
    private long lastWeather = 0;

    private AudioTrack sTick, sClick, sOpen, sClose;
    private boolean soundOn = true;
    private long muteTicksUntil = 0;

    private int openIdx = -1;
    private View openFrom;
    private boolean firstBuild = true;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            if (sky != null) sky.invalidate();
            refreshWeather(false);
            handler.postDelayed(this, 60_000);
        }
    };

    // ------------------------------------------------------------------ ciclo di vita

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        soundOn = prefs.getBoolean("sound", true);
        loadFolders();
        loadFavs();
        buildUi();
        initSounds();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        reloadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        muteTicks(600);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        loader.shutdownNow();
        net.shutdownNow();
        for (AudioTrack t : new AudioTrack[]{sTick, sClick, sOpen, sClose}) {
            if (t != null) t.release();
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Tasto Home: chiude la cartella aperta e torna alla prima cartella
        if (openIdx >= 0) closeFolder(false);
        if (foldersBox.getChildCount() > 0) focusFolder(0);
    }

    @Override
    public void onBackPressed() {
        if (openIdx >= 0) closeFolder(true);
        // altrimenti niente: siamo la Home
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.isLongClickable()) {
                f.performLongClick();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ interfaccia

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF10131A);
        sky = new SkyView(this);
        sky.setSun(prefs.getInt("sr", 7 * 60), prefs.getInt("ss", 18 * 60));
        root.addView(sky, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(30), dp(56), dp(26));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        // --- intestazione: data + meteo, orologio gigante, icone rotonde
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        header.setClipChildren(false);
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        header.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(-2, -2);
        drp.leftMargin = dp(6);
        left.addView(dateRow, drp);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextColor(0xF2FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        date.setSingleLine(true);
        shadow(date);
        dateRow.addView(date);

        weatherLine = text(18, 0xF2FFFFFF);
        weatherLine.setSingleLine(true);
        shadow(weatherLine);
        weatherLine.setText(prefs.contains("w_lat") ? "" : "  ·  imposta il meteo dal menu ⚙️");
        dateRow.addView(weatherLine);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 150);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        // effetto vetro: sfumatura dal bianco pieno al bianco trasparente
        time.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int hgt = b - t;
            if (hgt > 0) {
                ((TextView) v).getPaint().setShader(new LinearGradient(0, 0, 0, hgt,
                        0xF5FFFFFF, 0x66FFFFFF, Shader.TileMode.CLAMP));
                v.invalidate();
            }
        });
        left.addView(time, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout icons = new LinearLayout(this);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        icons.setClipChildren(false);
        header.addView(icons, new LinearLayout.LayoutParams(-2, -2));

        icons.addView(roundBtn(iconText("📺", 20), v -> launchTv()));
        icons.addView(roundBtn(iconText("☰", 22), v -> showAllApps()));
        icons.addView(roundBtn(iconGlyph(false), v -> powerOff()));
        icons.addView(roundBtn(iconGlyph(true), v -> restart()));
        icons.addView(roundBtn(iconText("⚙️", 20), v -> showLauncherMenu()));

        content.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        // --- preferiti
        favLabel = text(15, 0xF2FFFFFF);
        favLabel.setText("Preferiti");
        shadow(favLabel);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-2, -2);
        flp.leftMargin = dp(4);
        flp.bottomMargin = dp(10);
        content.addView(favLabel, flp);

        favBox = new LinearLayout(this);
        favBox.setOrientation(LinearLayout.HORIZONTAL);
        favBox.setClipChildren(false);
        content.addView(favBox, new LinearLayout.LayoutParams(-1, -2));

        // --- dock delle cartelle
        foldersBox = new LinearLayout(this);
        foldersBox.setOrientation(LinearLayout.HORIZONTAL);
        foldersBox.setClipChildren(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(74));
        dlp.topMargin = dp(24);
        content.addView(foldersBox, dlp);

        // --- sovrapposizione cartella aperta
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x8C06080C);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> closeFolder(true));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xD91A1F2C);
        pbg.setCornerRadius(dp(20));
        pbg.setStroke(dp(1), 0x40FFFFFF);
        panel.setBackground(pbg);
        panel.setPadding(dp(26), dp(20), dp(26), dp(24));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(panelWidth(), -2, Gravity.CENTER);
        overlay.addView(panel, plp);

        setContentView(root);
        updateGreeting();
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            if (oldF != null && newF != null && System.currentTimeMillis() > muteTicksUntil) play(sTick);
        });
    }

    private TextView iconText(String s, float sp) {
        TextView t = text(sp, Color.WHITE);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    private ImageView iconGlyph(boolean restart) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new GlyphDrawable(restart, dp(22)));
        return iv;
    }

    /** Pulsante rotondo in vetro con un'icona al centro. */
    private View roundBtn(View inner, final View.OnClickListener click) {
        FrameLayout b = new FrameLayout(this);
        styleGlass(b, dp(27), 0x141824, true);
        b.addView(inner, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        b.setOnClickListener(v -> {
            play(sClick);
            click.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(54));
        lp.leftMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    /** Vetro: fondo semitrasparente (leggermente colorato), bordo chiaro, al focus si schiarisce e si ingrandisce. */
    private void styleGlass(View v, final float radius, int tint, boolean pop) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor((tint & 0x00FFFFFF) | 0x66000000);
        normal.setStroke(dp(1), 0x47FFFFFF);
        normal.setCornerRadius(radius);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x4DFFFFFF);
        focused.setStroke(dp(1), 0x8CFFFFFF);
        focused.setCornerRadius(radius);
        StateListDrawable bg = new StateListDrawable();
        bg.setEnterFadeDuration(150);
        bg.setExitFadeDuration(150);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[0], normal);
        v.setBackground(bg);

        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                outline.setAlpha(0f); // niente ombra sotto il vetro
            }
        });
        v.setElevation(0);

        StateListAnimator sla = new StateListAnimator();
        sla.addState(new int[]{android.R.attr.state_pressed}, lift(pop ? 1.03f : 0.97f, 0, dp(4)));
        sla.addState(new int[]{android.R.attr.state_focused}, lift(pop ? 1.10f : 1.05f, 0, dp(8)));
        sla.addState(new int[0], lift(1f, 0, 0));
        v.setStateListAnimator(sla);

        v.setFocusable(true);
        v.setClickable(true);
        v.setLongClickable(true);
        v.setSoundEffectsEnabled(false);
    }

    private void buildFolders() {
        muteTicks(400);
        int focusIdx = -1;
        View f = getCurrentFocus();
        if (f != null && f.getTag() instanceof Integer && openIdx < 0) focusIdx = (Integer) f.getTag();

        foldersBox.removeAllViews();
        for (int i = 0; i < folders.size(); i++) {
            View tile = folderTile(i);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) tlp.leftMargin = dp(16);
            foldersBox.addView(tile, tlp);
        }
        buildFavs();

        if (openIdx >= 0) openFrom = findFolderView(openIdx);
        if (firstBuild && !folders.isEmpty()) {
            firstBuild = false;
            focusFolder(0);
        } else if (focusIdx >= 0 && !folders.isEmpty()) {
            focusFolder(Math.min(focusIdx, folders.size() - 1));
        }
    }

    /** Elemento del dock: icona sopra, nome sotto, vetro tinto col colore della cartella. */
    private View folderTile(final int idx) {
        Folder fo = folders.get(idx);
        int[] c = COLORS[fo.color % COLORS.length];

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(4), dp(8), dp(4));
        styleGlass(tile, dp(18), c[0], true);
        tile.setTag(idx);

        TextView icon = text(24, Color.WHITE);
        icon.setText(fo.icon);
        icon.setIncludeFontPadding(false);
        tile.addView(icon);

        TextView name = text(14, Color.WHITE);
        name.setText(fo.name);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(4);
        tile.addView(name, nlp);

        tile.setOnClickListener(v -> openFolder(idx, v));
        tile.setOnLongClickListener(v -> {
            folderOptions(idx);
            return true;
        });
        return tile;
    }

    // ------------------------------------------------------------------ preferiti

    private static final int MAX_FAVS = 5;
    private static final String[] DEFAULT_FAVS = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "it.rainet.androidtv",
            "com.amazon.amazonvideo.livingroom", "com.spotify.tv.android"};

    private void loadFavs() {
        favs.clear();
        String raw = prefs.getString("favs", null);
        if (raw == null) {
            favs.addAll(Arrays.asList(DEFAULT_FAVS));
        } else if (!raw.isEmpty()) {
            favs.addAll(Arrays.asList(raw.split(",")));
        }
    }

    private void saveFavs() {
        prefs.edit().putString("favs", android.text.TextUtils.join(",", favs)).apply();
    }

    private List<AppItem> visibleFavs() {
        List<AppItem> list = new ArrayList<>();
        for (String p : favs) {
            AppItem a = apps.get(p);
            if (a != null && list.size() < MAX_FAVS) list.add(a);
        }
        return list;
    }

    private void buildFavs() {
        String focusedPkg = null;
        View f = getCurrentFocus();
        if (f != null && f.getParent() == favBox && f.getTag() instanceof String) focusedPkg = (String) f.getTag();

        favBox.removeAllViews();
        List<AppItem> list = visibleFavs();
        int vis = list.isEmpty() ? View.GONE : View.VISIBLE;
        favLabel.setVisibility(vis);
        favBox.setVisibility(vis);

        int gap = dp(20);
        int inner = getResources().getDisplayMetrics().widthPixels - dp(112);
        int w = (inner - gap * (MAX_FAVS - 1)) / MAX_FAVS;
        int h = w * 9 / 16;
        View toFocus = null;
        for (int i = 0; i < list.size(); i++) {
            View t = appTile(list.get(i), GREY, w, h, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            if (i > 0) lp.leftMargin = gap;
            favBox.addView(t, lp);
            if (list.get(i).pkg.equals(focusedPkg)) toFocus = t;
        }
        if (toFocus != null) toFocus.requestFocus();
    }

    private void toggleFav(String pkg) {
        if (favs.remove(pkg)) {
            toast("Tolta dai preferiti");
        } else {
            if (visibleFavs().size() >= MAX_FAVS) {
                toast("Puoi avere al massimo " + MAX_FAVS + " preferiti");
                return;
            }
            favs.add(pkg);
            toast("Aggiunta ai preferiti");
        }
        saveFavs();
        buildFavs();
    }

    private void moveFav(String pkg, int dir) {
        List<String> vis = new ArrayList<>();
        for (String p : favs) if (apps.containsKey(p)) vis.add(p);
        int i = vis.indexOf(pkg), j = i + dir;
        if (i < 0 || j < 0 || j >= vis.size()) return;
        Collections.swap(vis, i, j);
        for (String p : favs) if (!apps.containsKey(p)) vis.add(p);
        favs.clear();
        favs.addAll(vis);
        saveFavs();
        buildFavs();
        View v = favBox.findViewWithTag(pkg);
        if (v != null) v.requestFocus();
    }

    private void favOptions(final AppItem a) {
        String[] opts = {"Rimuovi dai preferiti", "Sposta a sinistra", "Sposta a destra", "Info app"};
        dialog().setTitle(a.label).setItems(opts, (d, w) -> {
            if (w == 0) toggleFav(a.pkg);
            else if (w == 1) moveFav(a.pkg, -1);
            else if (w == 2) moveFav(a.pkg, 1);
            else safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }).show();
    }

    private void buildPanel(final Folder fo) {
        panel.removeAllViews();
        int[] c = COLORS[fo.color % COLORS.length];

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

        TextView icon = text(22, Color.WHITE);
        icon.setText(fo.icon);
        head.addView(icon);
        TextView name = text(21, Color.WHITE);
        name.setText(fo.name);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.leftMargin = dp(12);
        head.addView(name, nlp);
        List<AppItem> list = visibleApps(fo);
        TextView count = text(14, 0xFF8A93A3);
        count.setText(list.size() == 1 ? "1 app" : list.size() + " app");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1);
        clp.leftMargin = dp(12);
        head.addView(count, clp);
        TextView hint = text(13, 0xFF8A93A3);
        hint.setText("Indietro per chiudere · tieni premuto OK per le opzioni");
        head.addView(hint);

        int cols = 5;
        int gap = dp(18);
        int margin = dp(20); // spazio attorno alla griglia: l'app ingrandita non tocca mai il bordo
        int inner = panelWidth() - dp(52) - margin * 2;
        int tileW = (inner - gap * (cols - 1)) / cols;
        int tileH = tileW * 9 / 16;

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(margin, margin, margin, margin);
        grid.setClipToPadding(false); // senza questo Android taglia l'app ingrandita (angoli squadrati)

        int total = list.size() + 1; // + riquadro "Aggiungi"
        LinearLayout row = null;
        for (int i = 0; i < total; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, tileH);
                if (i > 0) rlp.topMargin = gap;
                grid.addView(row, rlp);
            }
            View tile = i < list.size() ? appTile(list.get(i), c, tileW, tileH, false) : addTile();
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(tileW, tileH);
            if (i % cols > 0) tlp.leftMargin = gap;
            row.addView(tile, tlp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(grid);
        int rows = (total + cols - 1) / cols;
        int gridH = rows * tileH + (rows - 1) * gap + margin * 2;
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Math.min(gridH, maxH));
        slp.topMargin = dp(10);
        panel.addView(scroll, slp);
    }

    private View appTile(final AppItem a, int[] c, int w, int h, final boolean fav) {
        FrameLayout tile = new FrameLayout(this);
        style3d(tile, c, dp(10), false);
        tile.setTag(a.pkg);
        tile.setContentDescription(a.label);

        ImageView img = new ImageView(this);
        if (a.banner) {
            // Banner già arrotondato nell'immagine: gli angoli restano perfetti anche durante l'effetto pop
            tile.setBackground(null);
            img.setImageDrawable(roundedBanner(a.image, w, h, dp(10)));
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setImageDrawable(a.image);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(44), dp(44),
                    Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            ilp.topMargin = dp(10);
            tile.addView(img, ilp);
            TextView label = text(12, Color.WHITE);
            label.setText(a.label);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            label.setPadding(dp(6), 0, dp(6), 0);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            llp.bottomMargin = dp(11);
            tile.addView(label, llp);
        }

        tile.setOnClickListener(v -> launch(a));
        tile.setOnLongClickListener(v -> {
            if (fav) favOptions(a);
            else appOptions(a);
            return true;
        });
        return tile;
    }

    /** Disegna il banner (ritaglio centrale) in una bitmap con angoli arrotondati. */
    private Drawable roundedBanner(Drawable src, int w, int h, float r) {
        if (w <= 0 || h <= 0) return src;
        try {
            Bitmap raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(raw);
            Rect old = new Rect(src.getBounds());
            int iw = src.getIntrinsicWidth(), ih = src.getIntrinsicHeight();
            if (iw > 0 && ih > 0) {
                float sc = Math.max(w / (float) iw, h / (float) ih);
                int dw = Math.round(iw * sc), dh = Math.round(ih * sc);
                int l = (w - dw) / 2, t = (h - dh) / 2;
                src.setBounds(l, t, l + dw, t + dh);
            } else {
                src.setBounds(0, 0, w, h);
            }
            src.draw(c);
            src.setBounds(old);

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setShader(new BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            new Canvas(out).drawRoundRect(new RectF(0, 0, w, h), r, r, p);
            raw.recycle();
            return new BitmapDrawable(getResources(), out);
        } catch (Throwable e) {
            return src;
        }
    }

    private View addTile() {
        TextView t = text(15, 0xFFD3D1C7);
        t.setText("＋  Aggiungi");
        t.setGravity(Gravity.CENTER);
        style3d(t, GREY, dp(10), false);
        t.setTag("add");
        t.setOnClickListener(v -> {
            play(sClick);
            if (openIdx >= 0) chooseApps(openIdx);
        });
        return t;
    }

    private View pill(final String label, final View.OnClickListener click) {
        return pill(label, null, click);
    }

    private View pill(final String label, Drawable icon, final View.OnClickListener click) {
        TextView t = text(16, Color.WHITE);
        t.setText(label);
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            t.setCompoundDrawablesRelative(icon, null, null, null);
            t.setCompoundDrawablePadding(dp(8));
        }
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(48));
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        style3d(t, GREY, dp(24));
        t.setOnClickListener(v -> {
            if (!"⚙️".equals(label)) play(sClick);
            click.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(12);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ stile 3D

    /** Stile piatto: tinta unita, bordo bianco e leggero ingrandimento al focus. */
    private void style3d(View v, int[] c, final float radius) {
        style3d(v, c, radius, true);
    }

    /** ring = false: niente bordo bianco, solo ingrandimento "pop" (usato per le app dentro le cartelle). */
    private void style3d(View v, int[] c, final float radius, boolean ring) {
        GradientDrawable face = new GradientDrawable();
        face.setColor((c[0] & 0x00FFFFFF) | 0xB8000000); // semitrasparente: il cielo si intravede
        face.setStroke(dp(1), 0x40FFFFFF);
        face.setCornerRadius(radius);
        v.setBackground(face);

        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setStroke(dp(3), Color.WHITE);
        border.setCornerRadius(radius);
        if (ring) {
            StateListDrawable fg = new StateListDrawable();
            fg.addState(new int[]{android.R.attr.state_focused}, border);
            fg.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
            v.setForeground(fg);
        }

        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setElevation(0);

        StateListAnimator sla = new StateListAnimator();
        if (ring) {
            sla.addState(new int[]{android.R.attr.state_pressed}, lift(0.97f, 0, 0));
            sla.addState(new int[]{android.R.attr.state_focused}, lift(1.05f, 0, 0));
        } else {
            sla.addState(new int[]{android.R.attr.state_pressed}, lift(1.05f, 0, dp(6)));
            sla.addState(new int[]{android.R.attr.state_focused}, lift(1.12f, 0, dp(10)));
        }
        sla.addState(new int[0], lift(1f, 0, 0));
        v.setStateListAnimator(sla);

        v.setFocusable(true);
        v.setClickable(true);
        v.setLongClickable(true);
        v.setSoundEffectsEnabled(false);
    }

    private Animator lift(float scale, float ty, float tz) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder((Object) null,
                PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scale),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, ty),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Z, tz));
        a.setDuration(160);
        a.setInterpolator(new DecelerateInterpolator());
        return a;
    }

    // ------------------------------------------------------------------ apri / chiudi cartella

    private void openFolder(int idx, View from) {
        if (idx < 0 || idx >= folders.size()) return;
        openIdx = idx;
        openFrom = from;
        play(sOpen);
        muteTicks(500);
        buildPanel(folders.get(idx));
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        panel.setAlpha(0f);
        panel.post(() -> {
            panel.setScaleX(1f);
            panel.setScaleY(1f);
            if (openFrom != null) {
                int[] fl = new int[2];
                int[] pl = new int[2];
                openFrom.getLocationInWindow(fl);
                panel.getLocationInWindow(pl);
                panel.setPivotX(fl[0] + openFrom.getWidth() / 2f - pl[0]);
                panel.setPivotY(fl[1] + openFrom.getHeight() / 2f - pl[1]);
            }
            panel.setScaleX(0.15f);
            panel.setScaleY(0.15f);
            overlay.animate().alpha(1f).setDuration(240).start();
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(340)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            focusFirstInPanel();
        });
    }

    private void closeFolder(boolean animate) {
        if (openIdx < 0) return;
        final int idx = openIdx;
        openIdx = -1;
        if (animate) play(sClose);
        muteTicks(400);
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (animate) {
            panel.animate().scaleX(0.15f).scaleY(0.15f).alpha(0f).setDuration(220)
                    .setInterpolator(new AccelerateInterpolator()).start();
            overlay.animate().alpha(0f).setDuration(240)
                    .withEndAction(() -> overlay.setVisibility(View.GONE)).start();
        } else {
            overlay.setVisibility(View.GONE);
        }
        focusFolder(idx);
    }

    private void refreshOpenPanel() {
        if (openIdx < 0) return;
        muteTicks(400);
        buildPanel(folders.get(openIdx));
        panel.setScaleX(1f);
        panel.setScaleY(1f);
        panel.setAlpha(1f);
        panel.post(this::focusFirstInPanel);
    }

    private void focusFirstInPanel() {
        View first = findFocusableIn(panel);
        if (first != null) first.requestFocus();
    }

    private View findFocusableIn(View v) {
        if (v.isFocusable() && v.getTag() != null) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findFocusableIn(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private View findFolderView(int idx) {
        return foldersBox.findViewWithTag(idx);
    }

    private void focusFolder(int idx) {
        View v = findFolderView(idx);
        if (v != null) v.requestFocus();
    }

    // ------------------------------------------------------------------ opzioni

    private void folderOptions(final int idx) {
        final Folder fo = folders.get(idx);
        String[] opts = {"Aggiungi o togli app", "Rinomina", "Cambia colore", "Cambia icona",
                "Sposta prima", "Sposta dopo", "Nuova cartella", "Elimina cartella"};
        dialog().setTitle(fo.name).setItems(opts, (d, w) -> {
            switch (w) {
                case 0:
                    chooseApps(idx);
                    break;
                case 1:
                    askText("Rinomina cartella", fo.name, s -> {
                        fo.name = s;
                        saveAndRefresh();
                    });
                    break;
                case 2:
                    dialog().setTitle("Colore").setItems(COLOR_NAMES, (d2, c) -> {
                        fo.color = c;
                        saveAndRefresh();
                    }).show();
                    break;
                case 3:
                    dialog().setTitle("Icona").setItems(ICONS, (d2, c) -> {
                        fo.icon = ICONS[c];
                        saveAndRefresh();
                    }).show();
                    break;
                case 4:
                    if (idx > 0) {
                        Collections.swap(folders, idx, idx - 1);
                        saveAndRefresh();
                        focusFolder(idx - 1);
                    }
                    break;
                case 5:
                    if (idx < folders.size() - 1) {
                        Collections.swap(folders, idx, idx + 1);
                        saveAndRefresh();
                        focusFolder(idx + 1);
                    }
                    break;
                case 6:
                    askText("Nuova cartella", "", s -> {
                        Folder nf = new Folder();
                        nf.key = "custom" + System.currentTimeMillis();
                        nf.name = s;
                        nf.icon = "📁";
                        nf.color = 5;
                        folders.add(nf);
                        saveAndRefresh();
                        focusFolder(folders.size() - 1);
                    });
                    break;
                case 7:
                    dialog().setTitle("Eliminare \"" + fo.name + "\"?")
                            .setMessage("Le app restano installate sul TV.")
                            .setPositiveButton("Elimina", (d2, x) -> {
                                folders.remove(fo);
                                saveAndRefresh();
                                if (!folders.isEmpty()) focusFolder(Math.max(0, idx - 1));
                            })
                            .setNegativeButton("Annulla", null).show();
                    break;
            }
        }).show();
    }

    private void appOptions(final AppItem a) {
        if (openIdx < 0) return;
        final Folder fo = folders.get(openIdx);
        String[] opts = {favs.contains(a.pkg) ? "Rimuovi dai preferiti" : "Aggiungi ai preferiti",
                "Rimuovi dalla cartella", "Sposta in un'altra cartella",
                "Sposta all'inizio", "Sposta a sinistra", "Sposta a destra", "Info app"};
        dialog().setTitle(a.label).setItems(opts, (d, w) -> {
            switch (w) {
                case 0:
                    toggleFav(a.pkg);
                    break;
                case 1:
                    fo.apps.remove(a.pkg);
                    saveAndRefresh();
                    refreshOpenPanel();
                    break;
                case 2:
                    moveToOtherFolder(a, fo);
                    break;
                case 3:
                case 4:
                case 5:
                    reorder(fo, a.pkg, w - 1);
                    break;
                case 6:
                    safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
            }
        }).show();
    }

    private void reorder(Folder fo, String pkg, int action) {
        List<String> vis = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String p : fo.apps) (apps.containsKey(p) ? vis : rest).add(p);
        int i = vis.indexOf(pkg);
        if (i < 0) return;
        if (action == 2) {
            vis.remove(i);
            vis.add(0, pkg);
        } else if (action == 3 && i > 0) {
            Collections.swap(vis, i, i - 1);
        } else if (action == 4 && i < vis.size() - 1) {
            Collections.swap(vis, i, i + 1);
        }
        vis.addAll(rest);
        fo.apps = vis;
        saveAndRefresh();
        refreshOpenPanel();
        panel.post(() -> {
            View v = panel.findViewWithTag(pkg);
            if (v != null) v.requestFocus();
        });
    }

    private void moveToOtherFolder(final AppItem a, final Folder from) {
        final List<Folder> others = new ArrayList<>();
        for (Folder f : folders) if (f != from) others.add(f);
        if (others.isEmpty()) return;
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++) names[i] = others.get(i).icon + "  " + others.get(i).name;
        dialog().setTitle("Sposta in…").setItems(names, (d, w) -> {
            from.apps.remove(a.pkg);
            Folder to = others.get(w);
            if (!to.apps.contains(a.pkg)) to.apps.add(a.pkg);
            saveAndRefresh();
            refreshOpenPanel();
        }).show();
    }

    /** Elenco di tutte le app con spunta: quelle spuntate stanno nella cartella. */
    private void chooseApps(final int idx) {
        final Folder fo = folders.get(idx);
        final List<AppItem> all = sortedApps();
        String[] labels = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).label.toString();
            checked[i] = fo.apps.contains(all.get(i).pkg);
        }
        dialog().setTitle(fo.icon + "  " + fo.name)
                .setMultiChoiceItems(labels, checked, (d, w, isChecked) -> checked[w] = isChecked)
                .setPositiveButton("Salva", (d, x) -> {
                    List<String> result = new ArrayList<>();
                    Set<String> wanted = new HashSet<>();
                    for (int i = 0; i < all.size(); i++) if (checked[i]) wanted.add(all.get(i).pkg);
                    for (String p : fo.apps) {
                        if (wanted.remove(p) || !apps.containsKey(p)) result.add(p);
                    }
                    for (AppItem a : all) if (wanted.contains(a.pkg)) result.add(a.pkg);
                    fo.apps = result;
                    saveAndRefresh();
                    refreshOpenPanel();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void showAllApps() {
        final List<AppItem> all = sortedApps();
        if (all.isEmpty()) return;
        String[] labels = new String[all.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = all.get(i).label.toString();
        dialog().setTitle("Tutte le app").setItems(labels, (d, w) -> launch(all.get(w))).show();
    }

    // ------------------------------------------------------------------ app

    private void reloadApps() {
        final PackageManager pm = getPackageManager();
        final String self = getPackageName();
        final Set<String> extra = new HashSet<>();
        for (Folder f : folders) extra.addAll(f.apps);
        for (Object[] d : DEFAULTS) extra.addAll(Arrays.asList((String[]) d[4]));

        loader.execute(() -> {
            Map<String, AppItem> map = new HashMap<>();
            collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, map, self);
            collect(pm, Intent.CATEGORY_LAUNCHER, map, self);
            for (String pkg : extra) {
                if (map.containsKey(pkg)) continue;
                try {
                    Intent li = pm.getLaunchIntentForPackage(pkg);
                    if (li == null) continue;
                    ResolveInfo ri = pm.resolveActivity(li, 0);
                    if (ri == null || ri.activityInfo == null) continue;
                    map.put(pkg, makeItem(pm, ri, li));
                } catch (Exception ignored) {
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                apps = map;
                addNewKnownApps();
                buildFolders();
                refreshOpenPanel();
            });
        });
    }

    private static void collect(PackageManager pm, String category, Map<String, AppItem> out, String self) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName) || out.containsKey(ai.packageName)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(ai.packageName, ai.name);
            out.put(ai.packageName, makeItem(pm, ri, launch));
        }
    }

    private static AppItem makeItem(PackageManager pm, ResolveInfo ri, Intent launch) {
        ActivityInfo ai = ri.activityInfo;
        Drawable img = null;
        try {
            img = ai.loadBanner(pm);
        } catch (Exception ignored) {
        }
        boolean banner = img != null;
        if (img == null) img = ri.loadIcon(pm);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return new AppItem(ai.packageName, ri.loadLabel(pm), img, banner, launch);
    }

    /** Le app appena installate che conosco finiscono da sole nella cartella giusta. */
    private void addNewKnownApps() {
        Set<String> seen = new HashSet<>(prefs.getStringSet("seen", Collections.<String>emptySet()));
        boolean changed = false;
        for (String pkg : apps.keySet()) {
            if (!seen.add(pkg)) continue;
            changed = true;
            for (Object[] d : DEFAULTS) {
                if (!Arrays.asList((String[]) d[4]).contains(pkg)) continue;
                for (Folder f : folders) {
                    if (d[0].equals(f.key) && !f.apps.contains(pkg)) f.apps.add(pkg);
                }
            }
        }
        if (changed) {
            prefs.edit().putStringSet("seen", seen).apply();
            saveFolders();
        }
    }

    private List<AppItem> visibleApps(Folder fo) {
        List<AppItem> list = new ArrayList<>();
        for (String p : fo.apps) {
            AppItem a = apps.get(p);
            if (a != null) list.add(a);
        }
        return list;
    }

    private List<AppItem> sortedApps() {
        List<AppItem> all = new ArrayList<>(apps.values());
        Collections.sort(all, (x, y) -> x.label.toString().compareToIgnoreCase(y.label.toString()));
        return all;
    }

    private void launch(AppItem a) {
        play(sClick);
        if (!safeStart(a.launch)) {
            Intent li = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (li == null || !safeStart(li)) toast("Impossibile aprire " + a.label);
        }
    }

    private void launchTv() {
        AppItem tv = apps.get(TV_PACKAGE);
        if (tv != null) {
            launch(tv);
            return;
        }
        Intent li = getPackageManager().getLeanbackLaunchIntentForPackage(TV_PACKAGE);
        if (li == null) li = getPackageManager().getLaunchIntentForPackage(TV_PACKAGE);
        if (li == null || !safeStart(li)) toast("App TV non trovata: usa il tasto Sorgenti");
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ salvataggio cartelle

    private void loadFolders() {
        folders.clear();
        String raw = prefs.getString("folders", null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Folder f = new Folder();
                    f.key = o.optString("key");
                    f.name = o.optString("name");
                    f.icon = o.optString("icon", "📁");
                    f.color = o.optInt("color", 5);
                    JSONArray a = o.optJSONArray("apps");
                    if (a != null) for (int k = 0; k < a.length(); k++) f.apps.add(a.getString(k));
                    folders.add(f);
                }
                return;
            } catch (Exception ignored) {
                folders.clear();
            }
        }
        for (Object[] d : DEFAULTS) {
            Folder f = new Folder();
            f.key = (String) d[0];
            f.name = (String) d[1];
            f.icon = (String) d[2];
            f.color = (Integer) d[3];
            folders.add(f);
        }
        prefs.edit().remove("seen").apply();
        saveFolders();
    }

    private void saveFolders() {
        try {
            JSONArray arr = new JSONArray();
            for (Folder f : folders) {
                JSONObject o = new JSONObject();
                o.put("key", f.key);
                o.put("name", f.name);
                o.put("icon", f.icon);
                o.put("color", f.color);
                o.put("apps", new JSONArray(f.apps));
                arr.put(o);
            }
            prefs.edit().putString("folders", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void saveAndRefresh() {
        saveFolders();
        buildFolders();
    }

    // ------------------------------------------------------------------ meteo (Open-Meteo, gratuito, senza chiave)

    private void askCity() {
        askText("La tua città", prefs.getString("w_city", ""), city -> net.execute(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=it&format=json&name="
                        + URLEncoder.encode(city, "UTF-8");
                JSONObject j = new JSONObject(httpGet(url));
                JSONArray res = j.optJSONArray("results");
                if (res == null || res.length() == 0) {
                    runOnUiThread(() -> toast("Città non trovata: " + city));
                    return;
                }
                JSONObject r = res.getJSONObject(0);
                prefs.edit()
                        .putString("w_city", r.optString("name", city))
                        .putString("w_lat", String.valueOf(r.getDouble("latitude")))
                        .putString("w_lon", String.valueOf(r.getDouble("longitude")))
                        .apply();
                runOnUiThread(() -> refreshWeather(true));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Nessuna connessione per cercare la città"));
            }
        }));
    }

    private void refreshWeather(boolean force) {
        if (!prefs.contains("w_lat")) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastWeather < 30 * 60 * 1000L) return;
        lastWeather = now;
        final String lat = prefs.getString("w_lat", "0");
        final String lon = prefs.getString("w_lon", "0");
        final String city = prefs.getString("w_city", "");
        net.execute(() -> {
            try {
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                        + "&timezone=auto&forecast_days=5";
                final JSONObject j = new JSONObject(httpGet(url));
                runOnUiThread(() -> showWeather(j, city));
            } catch (Exception e) {
                lastWeather = 0;
                runOnUiThread(() -> weatherLine.setText("  ·  meteo non disponibile"));
            }
        });
    }

    private void showWeather(JSONObject j, String city) {
        try {
            JSONObject cur = j.getJSONObject("current");
            int code = cur.getInt("weather_code");
            weatherLine.setText("  ·  " + weatherEmoji(code) + " " + Math.round(cur.getDouble("temperature_2m"))
                    + "° " + weatherText(code));
            JSONObject d = j.getJSONObject("daily");
            int sr = hm(d.getJSONArray("sunrise").getString(0));
            int ss = hm(d.getJSONArray("sunset").getString(0));
            prefs.edit().putInt("sr", sr).putInt("ss", ss).apply();
            sky.setSun(sr, ss);
        } catch (Exception e) {
            if (weatherLine.getText().length() == 0) weatherLine.setText("  ·  meteo non disponibile");
        }
    }

    private static String weatherEmoji(int c) {
        if (c == 0) return "☀️";
        if (c == 1) return "🌤️";
        if (c == 2) return "⛅";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 57) return "🌦️";
        if (c >= 61 && c <= 67) return "🌧️";
        if (c >= 71 && c <= 77) return "❄️";
        if (c >= 80 && c <= 82) return "🌦️";
        if (c == 85 || c == 86) return "🌨️";
        if (c >= 95) return "⛈️";
        return "🌡️";
    }

    private static String weatherText(int c) {
        if (c == 0) return "Sereno";
        if (c == 1) return "Quasi sereno";
        if (c == 2) return "Poco nuvoloso";
        if (c == 3) return "Nuvoloso";
        if (c == 45 || c == 48) return "Nebbia";
        if (c >= 51 && c <= 57) return "Pioviggine";
        if (c >= 61 && c <= 67) return "Pioggia";
        if (c >= 71 && c <= 77) return "Neve";
        if (c >= 80 && c <= 82) return "Rovesci";
        if (c == 85 || c == 86) return "Rovesci di neve";
        if (c >= 95) return "Temporale";
        return "";
    }

    private static String httpGet(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------------ suoni (generati in codice, nessun file audio)

    private static final int SR = 44100;

    private void initSounds() {
        sTick = makeTrack(tone(1400f, 1250f, 32, 0.08f, 7f));
        sClick = makeTrack(tone(700f, 1050f, 70, 0.13f, 5f));
        sOpen = makeTrack(tone(420f, 860f, 170, 0.11f, 2.6f));
        sClose = makeTrack(tone(860f, 420f, 150, 0.09f, 2.8f));
    }

    /** Sinusoide morbida con glissando e decadimento esponenziale. */
    private static short[] tone(float f0, float f1, int ms, float vol, float decay) {
        int n = SR * ms / 1000;
        short[] out = new short[n];
        double ph = 0;
        int attack = (int) (SR * 0.004);
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double f = f0 + (f1 - f0) * t;
            ph += 2 * Math.PI * f / SR;
            double env = Math.min(1.0, i / (double) attack) * Math.exp(-decay * t) * (1.0 - t);
            double v = Math.sin(ph) * 0.85 + Math.sin(ph * 2) * 0.15;
            out[i] = (short) (v * env * vol * 32767);
        }
        return out;
    }

    private static AudioTrack makeTrack(short[] pcm) {
        try {
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(pcm, 0, pcm.length);
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private void play(AudioTrack t) {
        if (!soundOn || t == null) return;
        try {
            if (t.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) t.stop();
            t.reloadStaticData();
            t.play();
        } catch (Exception ignored) {
        }
    }

    private void muteTicks(long ms) {
        muteTicksUntil = Math.max(muteTicksUntil, System.currentTimeMillis() + ms);
    }

    private void showLauncherMenu() {
        String[] opts = {
                "Impostazioni del TV",
                soundOn ? "Suoni del launcher: attivi (premi per spegnere)" : "Suoni del launcher: spenti (premi per accendere)",
                "Cambia città del meteo"
        };
        dialog().setTitle("Lite Launcher").setItems(opts, (d, w) -> {
            if (w == 0) {
                safeStart(new Intent(Settings.ACTION_SETTINGS));
            } else if (w == 1) {
                soundOn = !soundOn;
                prefs.edit().putBoolean("sound", soundOn).apply();
                play(sClick);
                toast(soundOn ? "Suoni attivi" : "Suoni spenti");
            } else {
                askCity();
            }
        }).show();
    }

    /** "2026-10-30T06:41" -> minuti dalla mezzanotte */
    private static int hm(String iso) {
        int t = iso.indexOf('T');
        return Integer.parseInt(iso.substring(t + 1, t + 3)) * 60 + Integer.parseInt(iso.substring(t + 4, t + 6));
    }

    private void shadow(TextView t) {
        t.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
    }

    // ------------------------------------------------------------------ cielo che cambia con l'ora

    /**
     * Sfondo: paesaggio di montagne e lago disegnato in codice. Cielo, sole, luna e stelle seguono l'ora
     * del giorno (alba e tramonto reali presi dal meteo). Ridisegnato una volta al minuto a mezza risoluzione.
     */
    static final class SkyView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final float[] stars = new float[120 * 4];
        private int srMin = 7 * 60;
        private int ssMin = 18 * 60;
        private Bitmap scene, frame;
        private int frameMinute = -1;
        private final Rect dst = new Rect();

        SkyView(Context c) {
            super(c);
            Random r = new Random(7);
            for (int i = 0; i < stars.length; i += 4) {
                stars[i] = r.nextFloat();
                stars[i + 1] = r.nextFloat();
                stars[i + 2] = 0.5f + r.nextFloat() * 1.2f;
                stars[i + 3] = 0.3f + r.nextFloat() * 0.7f;
            }
        }

        void setSun(int sunrise, int sunset) {
            if (sunrise > 120 && sunset > sunrise + 120 && sunset < 23 * 60) {
                srMin = sunrise;
                ssMin = sunset;
            }
            frameMinute = -1;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            Calendar cal = Calendar.getInstance();
            int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            int bw = Math.max(1, w / 2), bh = Math.max(1, h / 2);
            if (frame == null || frame.getWidth() != bw || frame.getHeight() != bh || frameMinute != m) {
                render(m, bw, bh);
                frameMinute = m;
            }
            dst.set(0, 0, w, h);
            canvas.drawBitmap(frame, null, dst, bmp);
        }

        private void render(int m, int w, int h) {
            if (scene == null || scene.getWidth() != w || scene.getHeight() != h) {
                if (scene != null) scene.recycle();
                if (frame != null) frame.recycle();
                scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            Canvas c = new Canvas(scene);
            int sr = srMin, ss = ssMin, noon = (sr + ss) / 2;
            float hz = h * 0.70f;

            // {minuto, colore alto, colore basso}
            int[][] f = {
                    {0, 0xFF050916, 0xFF0B1230},
                    {sr - 90, 0xFF070B1E, 0xFF161636},
                    {sr - 25, 0xFF1C2452, 0xFF8A4A68},
                    {sr + 25, 0xFF33548E, 0xFFF2A26C},
                    {sr + 120, 0xFF3A74BC, 0xFFA6D0EE},
                    {noon, 0xFF2766C0, 0xFF8CC4F2},
                    {ss - 120, 0xFF2C62AA, 0xFF9EC2E0},
                    {ss - 20, 0xFF4A3C88, 0xFFF08A70},
                    {ss + 30, 0xFF2A1E56, 0xFFC0507A},
                    {ss + 100, 0xFF0A0E26, 0xFF1A1838},
                    {24 * 60, 0xFF050916, 0xFF0B1230}};
            int k = 0;
            while (k < f.length - 2 && m >= f[k + 1][0]) k++;
            float span = Math.max(1, f[k + 1][0] - f[k][0]);
            float t = clamp((m - f[k][0]) / span);
            int top = lerp(f[k][1], f[k + 1][1], t);
            int bottom = lerp(f[k][2], f[k + 1][2], t);
            paint.setShader(new LinearGradient(0, 0, 0, hz, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            float night;
            if (m < sr - 90 || m > ss + 90) night = 1f;
            else if (m < sr - 20) night = (sr - 20 - m) / 70f;
            else if (m > ss + 20) night = (m - ss - 20) / 70f;
            else night = 0f;
            night = clamp(night);

            if (night > 0) {
                paint.setColor(Color.WHITE);
                float unit = w / 960f;
                for (int i = 0; i < stars.length; i += 4) {
                    paint.setAlpha((int) (255 * night * stars[i + 3]));
                    c.drawCircle(stars[i] * w, stars[i + 1] * hz * 0.85f, Math.max(0.6f, stars[i + 2] * unit * 1.4f), paint);
                }
                paint.setAlpha(255);
            }

            // sole (dietro le montagne)
            if (m > sr - 25 && m < ss + 25) {
                float p = (m - sr) / (float) (ss - sr);
                float elev = (float) Math.sin(Math.PI * clamp(p));
                float x = w * (0.06f + 0.88f * clamp(p));
                float y = hz - elev * h * 0.62f;
                float a = clamp(Math.min((m - (sr - 25)) / 50f, ((ss + 25) - m) / 50f));
                int col = lerp(0xFFFF8A40, 0xFFFFF3C8, clamp(elev * 1.8f));
                float glowR = w * 0.28f * (1.2f - elev * 0.4f);
                paint.setShader(new RadialGradient(x, y, glowR,
                        new int[]{withAlpha(col, (int) (150 * a)), withAlpha(col, (int) (50 * a)), withAlpha(col, 0)},
                        new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, hz, paint);
                paint.setShader(null);
                paint.setColor(withAlpha(col, (int) (255 * a)));
                c.drawCircle(x, y, w * 0.045f, paint);
            }

            // luna
            if (night > 0) {
                float start = ss + 30, end = sr - 30 + 24 * 60;
                float mm = m < sr ? m + 24 * 60 : m;
                float p = clamp((mm - start) / (end - start));
                float elev = (float) Math.sin(Math.PI * p);
                float x = w * (0.08f + 0.84f * p);
                float y = hz - elev * h * 0.55f;
                paint.setShader(new RadialGradient(x, y, w * 0.15f,
                        new int[]{withAlpha(0xFFDCE4FF, (int) (60 * night)), withAlpha(0xFFDCE4FF, 0)},
                        null, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, w * 0.15f, paint);
                paint.setShader(null);
                paint.setColor(withAlpha(0xFFE9EDF7, (int) (255 * night)));
                c.drawCircle(x, y, w * 0.03f, paint);
            }

            // montagne: tre catene, le lontane prendono il colore del cielo
            int dark = 0xFF0B0E1A;
            float[][] layers = {
                    {1.3f, hz - h * 0.05f, h * 0.34f, 0.35f},
                    {4.1f, hz - h * 0.01f, h * 0.22f, 0.62f},
                    {7.7f, hz + 1, h * 0.12f, 0.86f}};
            for (float[] l : layers) {
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(0, hz + 2);
                for (int x = 0; x <= w; x += 2) path.lineTo(x, ridge(x / (float) w, l[0], l[1], l[2]));
                path.lineTo(w, hz + 2);
                path.close();
                paint.setColor(lerp(bottom, dark, l[3]));
                c.drawPath(path, paint);
            }

            // lago: base scura + riflesso capovolto + velatura
            Canvas fc = new Canvas(frame);
            fc.drawBitmap(scene, 0, 0, null);
            int lake = (int) (h - hz);
            paint.setColor(lerp(bottom, dark, 0.7f));
            fc.drawRect(0, hz, w, h, paint);
            fc.save();
            fc.scale(1, -1, 0, hz);
            Paint refl = new Paint(Paint.FILTER_BITMAP_FLAG);
            refl.setAlpha(150);
            Rect src = new Rect(0, (int) hz - lake, w, (int) hz);
            fc.drawBitmap(scene, src, src, refl);
            fc.restore();
            paint.setShader(new LinearGradient(0, hz, 0, h,
                    withAlpha(lerp(bottom, dark, 0.5f), 90), withAlpha(dark, 215), Shader.TileMode.CLAMP));
            fc.drawRect(0, hz, w, h, paint);
            paint.setShader(null);
            paint.setColor(0x14FFFFFF);
            for (int i = 0; i < 14; i++) {
                float y = hz + 3 + i * lake / 15f;
                float x0 = w * (0.1f + ((i * 37) % 60) / 100f);
                fc.drawRect(x0, y, x0 + w * 0.12f, y + 1, paint);
            }
        }

        private static float ridge(float x, float s, float base, float amp) {
            double a = Math.abs(Math.sin(x * 2.1 + s)) * 0.55 + Math.abs(Math.sin(x * 5.3 + s * 1.7)) * 0.28
                    + Math.abs(Math.sin(x * 11.7 + s * 2.3)) * 0.12 + Math.sin(x * 23 + s) * 0.03;
            return base - amp * (float) a;
        }

        private static float clamp(float v) {
            return v < 0 ? 0 : (v > 1 ? 1 : v);
        }

        private static int withAlpha(int c, int a) {
            return (Math.max(0, Math.min(255, a)) << 24) | (c & 0x00FFFFFF);
        }

        private static int lerp(int a, int b, float t) {
            int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
            int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
            return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
        }
    }

    // ------------------------------------------------------------------ spegni / riavvia

    private void powerOff() {
        PowerService ps = PowerService.instance;
        if (ps == null) {
            explainPowerService();
            return;
        }
        if (!ps.standby()) toast("Il TV non ha accettato il comando di spegnimento");
    }

    private void restart() {
        final PowerService ps = PowerService.instance;
        if (ps == null) {
            explainPowerService();
            return;
        }
        dialog().setTitle("Riavviare il TV?")
                .setPositiveButton("Riavvia", (d, w) -> {
                    if (!ps.reboot()) toast("Il TV non ha accettato il comando di riavvio");
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void explainPowerService() {
        dialog().setTitle("Attiva Spegni e Riavvia")
                .setMessage("Android permette di spegnere o riavviare il TV solo tramite un servizio di accessibilità.\n\n"
                        + "Attivalo una volta: Impostazioni → Accessibilità → \"Lite Launcher – Spegni e riavvia\" → Attiva.\n\n"
                        + "Oppure da PC con ADB:\n"
                        + "adb shell settings put secure enabled_accessibility_services it.tony.litelauncher/.PowerService\n"
                        + "adb shell settings put secure accessibility_enabled 1")
                .setPositiveButton("Apri Accessibilità", (d, w) -> safeStart(
                        new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                .setNegativeButton("Chiudi", null)
                .show();
    }

    /** Icone disegnate in codice: simbolo di accensione e freccia circolare di riavvio. */
    static final class GlyphDrawable extends Drawable {
        private final boolean restart;
        private final int size;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlyphDrawable(boolean restart, int size) {
            this.restart = restart;
            this.size = size;
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(size * 0.11f);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY(), r = b.width() * 0.36f;
            RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
            if (!restart) {
                c.drawArc(oval, -55, 290, false, p);
                c.drawLine(cx, cy - r * 1.2f, cx, cy - r * 0.15f, p);
            } else {
                float start = -40, sweep = 290;
                c.drawArc(oval, start, sweep, false, p);
                double th = Math.toRadians(start + sweep);
                float ex = cx + r * (float) Math.cos(th), ey = cy + r * (float) Math.sin(th);
                double dx = -Math.sin(th), dy = Math.cos(th); // direzione di avanzamento
                float L = r * 0.62f;
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double a = Math.toRadians(35 * sgn);
                    double wx = dx * Math.cos(a) - dy * Math.sin(a), wy = dx * Math.sin(a) + dy * Math.cos(a);
                    c.drawLine(ex, ey, ex - L * (float) wx, ey - L * (float) wy, p);
                }
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
            p.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ utilità

    private void updateGreeting() {
        if (greeting == null) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 5 && h < 13 ? "Buongiorno" : h >= 13 && h < 18 ? "Buon pomeriggio" : "Buonasera");
    }

    interface TextResult {
        void onText(String s);
    }

    private void askText(String title, String initial, final TextResult result) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setText(initial);
        et.setSelection(et.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et);
        dialog().setTitle(title).setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) result.onText(s);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private TextView text(float sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int panelWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
