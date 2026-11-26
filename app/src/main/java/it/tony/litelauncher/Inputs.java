package it.tony.litelauncher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingressi del TV (HDMI, componenti, SCART…) trasformati in "app": si mettono nei preferiti
 * o in una cartella e si aprono con un OK, senza passare dal menu Sorgenti.
 */
final class Inputs {

    static final String PREFIX = "input:";

    static final class Input {
        String key;      // "input:<id>"
        String id;
        String label;    // "HDMI 1 · PlayStation 5"
        String badge;    // "HDMI", "AV"…
        Intent intent;
    }

    private Inputs() {
    }

    static boolean isInput(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    static List<Input> list(Context ctx) {
        List<Input> out = new ArrayList<>();
        TvInputManager tim;
        try {
            tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        } catch (Throwable e) {
            return out;
        }
        if (tim == null) return out;
        List<TvInputInfo> all;
        try {
            all = tim.getTvInputList();
        } catch (Throwable e) {
            return out;
        }
        // nomi dei dispositivi collegati (HDMI-CEC), agganciati alla loro porta
        Map<String, String> devices = new HashMap<>();
        for (TvInputInfo i : all) {
            try {
                if (i.getParentId() == null || !i.isPassthroughInput()) continue;
                CharSequence n = label(ctx, i);
                if (n != null && !devices.containsKey(i.getParentId())) devices.put(i.getParentId(), n.toString());
            } catch (Throwable ignored) {
            }
        }
        for (TvInputInfo i : all) {
            try {
                if (!i.isPassthroughInput() || i.getParentId() != null) continue;
                if (i.isHidden(ctx)) continue;
                Input in = new Input();
                in.id = i.getId();
                in.key = PREFIX + in.id;
                CharSequence n = label(ctx, i);
                String name = n != null ? n.toString().trim() : "Ingresso";
                String dev = devices.get(in.id);
                in.label = dev != null && !dev.equalsIgnoreCase(name) ? name + " · " + dev : name;
                in.badge = badge(i.getType());
                in.intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.id))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                out.add(in);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static CharSequence label(Context ctx, TvInputInfo i) {
        CharSequence c = null;
        try {
            c = i.loadCustomLabel(ctx); // il nome dato dall'utente nelle impostazioni del TV
        } catch (Throwable ignored) {
        }
        if (c == null || c.toString().trim().isEmpty()) {
            try {
                c = i.loadLabel(ctx);
            } catch (Throwable ignored) {
            }
        }
        return c;
    }

    private static String badge(int type) {
        switch (type) {
            case TvInputInfo.TYPE_HDMI:
                return "HDMI";
            case TvInputInfo.TYPE_COMPONENT:
                return "YPbPr";
            case TvInputInfo.TYPE_COMPOSITE:
                return "AV";
            case TvInputInfo.TYPE_SCART:
                return "SCART";
            case TvInputInfo.TYPE_VGA:
                return "VGA";
            case TvInputInfo.TYPE_DVI:
                return "DVI";
            case TvInputInfo.TYPE_DISPLAY_PORT:
                return "DP";
            default:
                return "IN";
        }
    }

    /** Icona disegnata: una presa stilizzata con la sigla dell'ingresso. */
    static final class BadgeDrawable extends Drawable {
        private final String text;
        private final int size;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);

        BadgeDrawable(String text, int size) {
            this.text = text;
            this.size = size;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(size * 0.06f);
            fill.setColor(0x33FFFFFF);
            txt.setColor(Color.WHITE);
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            float pw = w * 0.86f, ph = h * 0.46f;
            float l = b.left + (w - pw) / 2, t = b.top + (h - ph) / 2;
            // presa trapezoidale, come quella HDMI
            android.graphics.Path p = new android.graphics.Path();
            float bev = ph * 0.32f;
            p.moveTo(l, t);
            p.lineTo(l + pw, t);
            p.lineTo(l + pw, t + ph - bev);
            p.lineTo(l + pw - bev, t + ph);
            p.lineTo(l + bev, t + ph);
            p.lineTo(l, t + ph - bev);
            p.close();
            c.drawPath(p, fill);
            c.drawPath(p, stroke);
            float ts = ph * (text.length() > 4 ? 0.42f : 0.52f);
            txt.setTextSize(ts);
            c.drawText(text, b.exactCenterX(), t + ph / 2 - (txt.descent() + txt.ascent()) / 2, txt);
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
            stroke.setAlpha(alpha);
            txt.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
