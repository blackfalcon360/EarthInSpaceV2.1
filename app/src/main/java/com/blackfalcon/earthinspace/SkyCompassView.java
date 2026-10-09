package com.blackfalcon.earthinspace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/** Compass dial. Hold the phone flat: the markers show which way things are in your real sky. */
public class SkyCompassView extends View {

    private static final int RED = 0xFFFF3B30, GREEN = 0xFF2ECC71;
    private static final int[] COLORS = {0xFFFFC107, 0xFF2ECC71, 0xFFFFFFFF, 0xFF7EC8FF, 0xFFFF9AD5, 0xFFFFE08A};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float heading = 0f;
    private Sky.Target[] targets;

    public SkyCompassView(Context c) { super(c); tp.setTextAlign(Paint.Align.CENTER); }

    public void setHeading(float h) { heading = h; invalidate(); }
    public void setTargets(Sky.Target[] t) { targets = t; invalidate(); }

    private float px(float cx, float r, double deg) { return cx + r * (float) Math.sin(Math.toRadians(deg)); }
    private float py(float cy, float r, double deg) { return cy - r * (float) Math.cos(Math.toRadians(deg)); }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        tp.setTextSize(size);
        tp.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.22f);
        tp.setColor(0xFF02030A);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(color);
        c.drawText(s, x, y, tp);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight(), unit = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f + unit * 0.02f, R = unit * 0.34f;
        c.drawColor(0xFF02030A);

        // ring + ticks (turn with the phone so N always points to true north)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(unit * 0.007f);
        p.setColor(Color.WHITE);
        c.drawCircle(cx, cy, R, p);
        p.setColor(0xFF3A3A60);
        p.setStrokeWidth(unit * 0.003f);
        c.drawCircle(cx, cy, R * 0.9f, p);
        c.drawCircle(cx, cy, R * 0.4f, p);
        for (int d = 0; d < 360; d += 5) {
            double a = d - heading;
            float len = (d % 30 == 0) ? R * 0.08f : R * 0.04f;
            p.setStrokeWidth(unit * (d % 30 == 0 ? 0.005f : 0.0025f));
            p.setColor(Color.WHITE);
            c.drawLine(px(cx, R, a), py(cy, R, a), px(cx, R + len, a), py(cy, R + len, a), p);
        }
        String[] card = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double a = i * 90 - heading;
            float size = i == 0 ? R * 0.2f : R * 0.14f;
            text(c, card[i], px(cx, R * 1.2f, a), py(cy, R * 1.2f, a) + size * 0.35f, size, i == 0 ? RED : Color.WHITE, true);
        }

        if (targets != null) {
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < targets.length; i++) {
                    Sky.Target t = targets[i];
                    boolean up = t.alt > 0;
                    if ((pass == 0) == up) continue;
                    float r = up ? R * (0.9f - 0.5f * (float) (t.alt / 90.0)) : R * 0.97f;
                    float x = px(cx, r, t.az - heading), y = py(cy, r, t.az - heading);
                    float s = unit * 0.07f;
                    tp.setTypeface(Typeface.DEFAULT);
                    tp.setStyle(Paint.Style.FILL);
                    tp.setTextSize(s);
                    tp.setColor(Color.WHITE);
                    tp.setAlpha(up ? 255 : 110);
                    c.drawText(t.emoji, x, y + s * 0.35f, tp);
                    tp.setAlpha(255);
                    int col = COLORS[Math.min(i, COLORS.length - 1)];
                    text(c, t.shortName, x, y + s * 0.95f, unit * 0.026f, up ? col : 0xFF8A8AA8, false);
                }
            }
        }

        // centre = straight up
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF6666AA);
        c.drawCircle(cx, cy, unit * 0.008f, p);

        // marker for the top of the phone
        Path tri = new Path();
        float tw = unit * 0.028f, ty = cy - R * 1.42f;
        tri.moveTo(cx, ty + tw * 1.5f);
        tri.lineTo(cx - tw, ty);
        tri.lineTo(cx + tw, ty);
        tri.close();
        p.setColor(GREEN);
        c.drawPath(tri, p);
    }
}
