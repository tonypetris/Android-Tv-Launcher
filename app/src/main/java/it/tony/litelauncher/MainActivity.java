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
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.graphics.Outline;
import android.graphics.Typeface;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lite Launcher: cartelle 3D con apertura a zoom, pulsante TV e meteo locale (Open-Meteo).
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

    private LinearLayout content;
    private LinearLayout foldersBox;
    private TextView greeting;
    private FrameLayout overlay;
    private LinearLayout panel;

    private TextView wIcon, wTemp, wDesc, wSub;
    private LinearLayout wForecast;
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

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(30), dp(56), dp(28));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        // --- intestazione
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        header.setClipChildren(false);
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        header.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        greeting = text(15, 0xFF8A93A3);
        left.addView(greeting);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.BOTTOM);
        left.addView(clockRow);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        clockRow.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextColor(0xFF8A93A3);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = dp(16);
        dlp.bottomMargin = dp(6);
        clockRow.addView(date, dlp);

        header.addView(pill("📺  TV", v -> launchTv()));
        header.addView(pill("☰  App", v -> showAllApps()));
        header.addView(pill("⚙️", v -> showLauncherMenu()));

        // --- cartelle
        foldersBox = new LinearLayout(this);
        foldersBox.setOrientation(LinearLayout.VERTICAL);
        foldersBox.setClipChildren(false);
        foldersBox.setClipToPadding(false);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(30);
        content.addView(foldersBox, flp);

        content.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        // --- meteo
        content.addView(buildWeatherCard(), new LinearLayout.LayoutParams(-1, -2));

        // --- sovrapposizione cartella aperta
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xC806080C);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> closeFolder(true));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setClipChildren(false);
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF222835, 0xFF191E28});
        pbg.setCornerRadius(dp(18));
        pbg.setStroke(dp(1), 0x1FFFFFFF);
        panel.setBackground(pbg);
        panel.setElevation(dp(24));
        panel.setPadding(dp(26), dp(20), dp(26), dp(24));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(panelWidth(), -2, Gravity.CENTER);
        overlay.addView(panel, plp);

        setContentView(root);
        updateGreeting();
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            if (oldF != null && newF != null && System.currentTimeMillis() > muteTicksUntil) play(sTick);
        });
    }

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(22), dp(14), dp(26), dp(14) + dp(5));
        style3d(card, DARK, dp(14));
        card.setOnClickListener(v -> {
            play(sClick);
            askCity();
        });
        card.setOnLongClickListener(v -> {
            askCity();
            return true;
        });

        wIcon = text(36, Color.WHITE);
        card.addView(wIcon);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1);
        clp.leftMargin = dp(16);
        card.addView(col, clp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        col.addView(row);
        wTemp = text(30, Color.WHITE);
        wTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        wTemp.setIncludeFontPadding(false);
        row.addView(wTemp);
        wDesc = text(16, 0xFFAEB5C2);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = dp(10);
        row.addView(wDesc, dlp);

        wSub = text(13, 0xFF8A93A3);
        col.addView(wSub);

        wForecast = new LinearLayout(this);
        wForecast.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(wForecast);

        wIcon.setText("🌤️");
        wTemp.setText("");
        wDesc.setText("Meteo");
        wSub.setText("Premi OK qui per scegliere la tua città");
        return card;
    }

    private void buildFolders() {
        muteTicks(400);
        int focusIdx = -1;
        View f = getCurrentFocus();
        if (f != null && f.getTag() instanceof Integer && openIdx < 0) focusIdx = (Integer) f.getTag();

        foldersBox.removeAllViews();
        int cols = 3;
        LinearLayout row = null;
        for (int i = 0; i < folders.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(92));
                if (i > 0) rlp.topMargin = dp(24);
                foldersBox.addView(row, rlp);
            }
            View tile = folderTile(i);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -1, 1);
            if (i % cols > 0) tlp.leftMargin = dp(24);
            row.addView(tile, tlp);
        }
        // riempi l'ultima riga per mantenere le larghezze
        if (row != null) {
            for (int k = folders.size() % cols; k > 0 && k < cols; k++) {
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -1, 1);
                tlp.leftMargin = dp(24);
                row.addView(new View(this), tlp);
            }
        }

        if (openIdx >= 0) openFrom = findFolderView(openIdx);
        if (firstBuild && !folders.isEmpty()) {
            firstBuild = false;
            focusFolder(0);
        } else if (focusIdx >= 0 && !folders.isEmpty()) {
            focusFolder(Math.min(focusIdx, folders.size() - 1));
        }
    }

    private View folderTile(final int idx) {
        Folder fo = folders.get(idx);
        int[] c = COLORS[fo.color % COLORS.length];

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(22), 0, dp(16), dp(5));
        style3d(tile, c, dp(14));
        tile.setTag(idx);

        TextView icon = text(30, Color.WHITE);
        icon.setText(fo.icon);
        tile.addView(icon);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(16);
        tile.addView(col, lp);

        TextView name = text(20, Color.WHITE);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setText(fo.name);
        name.setSingleLine(true);
        col.addView(name);

        int n = visibleApps(fo).size();
        TextView count = text(13, 0x99FFFFFF);
        count.setText(n == 1 ? "1 app" : n + " app");
        col.addView(count);

        tile.setOnClickListener(v -> openFolder(idx, v));
        tile.setOnLongClickListener(v -> {
            folderOptions(idx);
            return true;
        });
        return tile;
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
        int gap = dp(16);
        int inner = panelWidth() - dp(52) - dp(20);
        int tileW = (inner - gap * (cols - 1)) / cols;
        int tileH = tileW * 9 / 16 + dp(5);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(dp(10), dp(12), dp(10), dp(12));

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
            View tile = i < list.size() ? appTile(list.get(i), c) : addTile();
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(tileW, tileH);
            if (i % cols > 0) tlp.leftMargin = gap;
            row.addView(tile, tlp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid);
        int rows = (total + cols - 1) / cols;
        int gridH = rows * tileH + (rows - 1) * gap + dp(24);
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Math.min(gridH, maxH));
        slp.topMargin = dp(10);
        panel.addView(scroll, slp);
    }

    private View appTile(final AppItem a, int[] c) {
        FrameLayout tile = new FrameLayout(this);
        style3d(tile, c, dp(10));
        tile.setTag(a.pkg);
        tile.setContentDescription(a.label);

        ImageView img = new ImageView(this);
        img.setImageDrawable(a.image);
        final int r = dp(10);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        img.setClipToOutline(true);

        if (a.banner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-1, -1);
            ilp.bottomMargin = dp(5);
            tile.addView(img, ilp);
        } else {
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
            appOptions(a);
            return true;
        });
        return tile;
    }

    private View addTile() {
        TextView t = text(15, 0xFFD3D1C7);
        t.setText("＋  Aggiungi");
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(5));
        style3d(t, GREY, dp(10));
        t.setTag("add");
        t.setOnClickListener(v -> {
            play(sClick);
            if (openIdx >= 0) chooseApps(openIdx);
        });
        return t;
    }

    private View pill(final String label, final View.OnClickListener click) {
        TextView t = text(16, Color.WHITE);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(48));
        t.setPadding(dp(18), dp(9), dp(18), dp(9) + dp(4));
        style3d(t, GREY, dp(24));
        t.setOnClickListener(v -> {
            if (!"⚙️".equals(label)) play(sClick);
            click.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(14);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ stile 3D

    /** Rilievo: faccia con sfumatura + bordo inferiore scuro, ombra, sollevamento al focus, affondamento alla pressione. */
    private void style3d(View v, int[] c, final float radius) {
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(c[2]);
        edge.setCornerRadius(radius);

        GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{c[0], c[1]});
        face.setCornerRadius(radius);
        face.setStroke(dp(1), 0x2EFFFFFF);

        LayerDrawable bg = new LayerDrawable(new Drawable[]{edge, face});
        bg.setLayerInset(1, 0, 0, 0, dp(5));
        v.setBackground(bg);

        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(3), Color.WHITE);
        ring.setCornerRadius(radius);
        StateListDrawable fg = new StateListDrawable();
        fg.addState(new int[]{android.R.attr.state_focused}, ring);
        fg.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        v.setForeground(fg);

        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setElevation(dp(6));

        StateListAnimator sla = new StateListAnimator();
        sla.addState(new int[]{android.R.attr.state_pressed}, lift(1.02f, dp(3), 0));
        sla.addState(new int[]{android.R.attr.state_focused}, lift(1.06f, -dp(5), dp(12)));
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
        String[] opts = {"Rimuovi dalla cartella", "Sposta in un'altra cartella",
                "Sposta all'inizio", "Sposta a sinistra", "Sposta a destra", "Info app"};
        dialog().setTitle(a.label).setItems(opts, (d, w) -> {
            switch (w) {
                case 0:
                    fo.apps.remove(a.pkg);
                    saveAndRefresh();
                    refreshOpenPanel();
                    break;
                case 1:
                    moveToOtherFolder(a, fo);
                    break;
                case 2:
                case 3:
                case 4:
                    reorder(fo, a.pkg, w);
                    break;
                case 5:
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
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=5";
                final JSONObject j = new JSONObject(httpGet(url));
                runOnUiThread(() -> showWeather(j, city));
            } catch (Exception e) {
                lastWeather = 0;
                runOnUiThread(() -> {
                    wDesc.setText("Meteo non disponibile");
                    wSub.setText(city + " · nessuna connessione");
                });
            }
        });
    }

    private void showWeather(JSONObject j, String city) {
        try {
            JSONObject cur = j.getJSONObject("current");
            int code = cur.getInt("weather_code");
            wIcon.setText(weatherEmoji(code));
            wTemp.setText(Math.round(cur.getDouble("temperature_2m")) + "°");
            wDesc.setText(weatherText(code));

            JSONObject d = j.getJSONObject("daily");
            JSONArray time = d.getJSONArray("time");
            JSONArray wc = d.getJSONArray("weather_code");
            JSONArray mx = d.getJSONArray("temperature_2m_max");
            JSONArray mn = d.getJSONArray("temperature_2m_min");
            wSub.setText(city + " · max " + Math.round(mx.getDouble(0)) + "°  min " + Math.round(mn.getDouble(0)) + "°");

            wForecast.removeAllViews();
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.ITALIAN);
            for (int k = 1; k < Math.min(5, time.length()); k++) {
                LinearLayout day = new LinearLayout(this);
                day.setOrientation(LinearLayout.VERTICAL);
                day.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), -2);
                wForecast.addView(day, lp);

                String name;
                try {
                    Date dt = in.parse(time.getString(k));
                    name = out.format(dt);
                    name = name.substring(0, 1).toUpperCase(Locale.ITALIAN) + name.substring(1).replace(".", "");
                } catch (Exception e) {
                    name = "";
                }
                TextView n = text(13, 0xFFAEB5C2);
                n.setText(name);
                day.addView(n);
                TextView ic = text(20, Color.WHITE);
                ic.setText(weatherEmoji(wc.getInt(k)));
                day.addView(ic);
                TextView t = text(13, 0xFFAEB5C2);
                t.setText(Math.round(mx.getDouble(k)) + "° " + Math.round(mn.getDouble(k)) + "°");
                day.addView(t);
            }
        } catch (Exception e) {
            wDesc.setText("Meteo non disponibile");
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
        play(sClick);
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

    // ------------------------------------------------------------------ utilità

    private void updateGreeting() {
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
