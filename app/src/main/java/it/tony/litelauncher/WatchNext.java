package it.tony.litelauncher;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fila "Continua a guardare": legge i programmi che le app (Netflix, YouTube, Prime Video…)
 * scrivono nel canale Watch Next di Android TV. Serve il permesso READ_TV_LISTINGS,
 * lo stesso che usano gli altri launcher alternativi.
 */
final class WatchNext {

    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final int MAX = 12;

    static final class Item {
        String key;        // per nasconderlo: pacchetto + id interno (o titolo)
        String pkg;
        String title;
        String subtitle;
        String image;
        String intentUri;
        float progress;    // 0..1, oppure -1 se non noto
    }

    private WatchNext() {
    }

    static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    static boolean granted(Context c) {
        return supported() && c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    static Uri uri() {
        return TvContract.WatchNextPrograms.CONTENT_URI;
    }

    /** Elenco ordinato dal più recente. Restituisce null se non è possibile leggerlo. */
    static List<Item> load(Context ctx, Set<String> installed, Set<String> hidden) {
        if (!granted(ctx)) return null;
        String[] cols = {
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
        };
        List<Item> out = new ArrayList<>();
        List<Long> when = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(uri(), cols, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                String pkg = c.getString(0);
                if (pkg == null || !installed.contains(pkg)) continue;
                if (!c.isNull(12) && c.getInt(12) == 0) continue; // nascosto dall'app stessa
                String intent = c.getString(8);
                String title = c.getString(2);
                if (intent == null || title == null || title.trim().isEmpty()) continue;

                Item it = new Item();
                it.pkg = pkg;
                String id = c.getString(1);
                it.key = pkg + "|" + (id != null ? id : title);
                if (hidden.contains(it.key)) continue;
                it.title = title.trim();
                it.subtitle = subtitle(c.getString(4), c.getString(5), c.getString(3));
                it.image = c.getString(6) != null ? c.getString(6) : c.getString(7);
                it.intentUri = intent;
                long pos = c.isNull(9) ? 0 : c.getLong(9);
                long dur = c.isNull(10) ? 0 : c.getLong(10);
                it.progress = pos > 0 && dur > 0 ? Math.min(1f, pos / (float) dur) : -1f;
                long t = c.isNull(11) ? 0 : c.getLong(11);

                // inserimento ordinato (più recente prima)
                int at = 0;
                while (at < when.size() && when.get(at) >= t) at++;
                when.add(at, t);
                out.add(at, it);
            }
        } catch (Exception e) {
            return null;
        }
        return out.size() > MAX ? new ArrayList<>(out.subList(0, MAX)) : out;
    }

    private static String subtitle(String season, String episode, String epTitle) {
        StringBuilder s = new StringBuilder();
        if (season != null && !season.isEmpty()) s.append('S').append(season);
        if (episode != null && !episode.isEmpty()) {
            if (s.length() > 0) s.append(' ');
            s.append('E').append(episode);
        }
        if (epTitle != null && !epTitle.trim().isEmpty()) {
            if (s.length() > 0) s.append(" · ");
            s.append(epTitle.trim());
        }
        return s.length() > 0 ? s.toString() : null;
    }

    /** L'intent che l'app ha scritto per riprendere proprio quel titolo. */
    static Intent intent(Item it) {
        Intent i = null;
        try {
            i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
        } catch (Exception ignored) {
        }
        if (i == null) {
            try {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(it.intentUri));
            } catch (Exception ignored) {
                return null;
            }
        }
        if (i.getPackage() == null && i.getComponent() == null) i.setPackage(it.pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Scarica e riduce l'immagine alla dimensione della scheda (niente librerie esterne). */
    static Bitmap loadImage(Context ctx, String src, int w, int h) {
        if (src == null) return null;
        try {
            byte[] data;
            Uri u = Uri.parse(src);
            String scheme = u.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                HttpURLConnection con = (HttpURLConnection) new URL(src).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                con.setInstanceFollowRedirects(true);
                try (InputStream in = con.getInputStream()) {
                    data = readAll(in);
                } finally {
                    con.disconnect();
                }
            } else {
                ContentResolver cr = ctx.getContentResolver();
                try (InputStream in = cr.openInputStream(u)) {
                    if (in == null) return null;
                    data = readAll(in);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > 6 * 1024 * 1024) break; // immagine troppo grande: basta così
        }
        return b.toByteArray();
    }
}
