package com.blackfalcon.earthinspace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Draws three views of where Earth is: the solar system, the nearby stars, the Milky Way. */
public class SpaceView extends View {

    public interface Listener { void onSelect(int mode, int index); }

    public static final int SOLAR = 0, STARS = 1, GALAXY = 2;
    public static final int SUN = 8; // selection index of the Sun in the solar view

    private static final int BG = 0xFF02030A;
    private static final int GOLD = 0xFFFFC107, GREEN = 0xFF2ECC71, GRAYLINE = 0xFF3A3A60;
    private static final int[] PCOLOR = {0xFFB0B0B0, 0xFFE8CDA2, 0xFF3FA7FF, 0xFFE2563B, 0xFFD9A066, 0xFFE3C77D, 0xFF7DE3E3, 0xFF4B70DD};
    private static final float[] PSIZE = {0.016f, 0.021f, 0.024f, 0.019f, 0.034f, 0.030f, 0.024f, 0.024f};
    private static final String[] QNAME = {"Mar equinox", "Jun solstice", "Sep equinox", "Dec solstice"};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private Listener listener;
    private int mode = SOLAR;
    private float zoom = 0.4f;
    private int selected = 2;

    // solar data (polar: theta radians, r AU)
    private final double[] pTheta = new double[8], pR = new double[8];
    private double[][] orbTheta, orbR;
    private double[] trTheta, trR;
    private final double[] qTheta = new double[4], qR = new double[4];
    private final double[] apTheta = new double[2], apR = new double[2]; // perihelion, aphelion
    private boolean haveSolar = false;

    // one slot per star (and per planet); sized from the star list so adding stars can never overflow it
    private final float[] hitX = new float[Math.max(16, Cosmos.STARS.length + 8)], hitY = new float[Math.max(16, Cosmos.STARS.length + 8)];
    private int hitN = 0;

    public SpaceView(Context c) { super(c); }

    public void setListener(Listener l) { listener = l; }
    public void setMode(int m) { mode = m; selected = m == SOLAR ? 2 : -1; invalidate(); }
    public void setZoom(float z) { zoom = Math.max(0f, Math.min(1f, z)); invalidate(); }
    public void setSelected(int i) { selected = i; invalidate(); }
    public int getSelected() { return selected; }

    public static double solarRmax(float zoom) { return 2.0 * Math.pow(16.0, zoom); }   // 2 .. 32 AU
    public static double starsDmax(float zoom) { return 15.0 * Math.pow(2000.0 / 15.0, zoom); } // 15 .. 2000 ly
    public static double galaxyScale(float zoom) { return Math.pow(8.0, zoom); }         // x1 .. x8

    public void setSolar(double[][] planetsXyz, double[][] orbits, double[][] trail, double[][] quarters, double[][] periAp) {
        for (int i = 0; i < 8; i++) { pTheta[i] = Math.atan2(planetsXyz[i][1], planetsXyz[i][0]); pR[i] = Math.hypot(planetsXyz[i][0], planetsXyz[i][1]); }
        orbTheta = new double[8][]; orbR = new double[8][];
        for (int i = 0; i < 8; i++) {
            int n = orbits[i].length / 2;
            orbTheta[i] = new double[n]; orbR[i] = new double[n];
            for (int j = 0; j < n; j++) { orbTheta[i][j] = orbits[i][2 * j]; orbR[i][j] = orbits[i][2 * j + 1]; }
        }
        int n = trail[0].length;
        trTheta = new double[n]; trR = new double[n];
        for (int j = 0; j < n; j++) { trTheta[j] = Math.atan2(trail[1][j], trail[0][j]); trR[j] = Math.hypot(trail[0][j], trail[1][j]); }
        for (int q = 0; q < 4; q++) { qTheta[q] = Math.atan2(quarters[q][1], quarters[q][0]); qR[q] = Math.hypot(quarters[q][0], quarters[q][1]); }
        for (int k = 0; k < 2; k++) { apTheta[k] = Math.atan2(periAp[k][1], periAp[k][0]); apR[k] = Math.hypot(periAp[k][0], periAp[k][1]); }
        haveSolar = true;
        invalidate();
    }

    // ---------------------------------------------------------------- helpers
    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, Paint.Align al) {
        tp.setTextAlign(al);
        tp.setTextSize(size);
        tp.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.22f);
        tp.setColor(BG);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(color);
        c.drawText(s, x, y, tp);
    }

    private void backdrop(Canvas c, float w, float h) {
        c.drawColor(BG);
        Random r = new Random(11);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 70; i++) {
            p.setColor(Color.argb(60 + r.nextInt(120), 255, 255, 255));
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 0.8f + r.nextFloat() * 1.2f, p);
        }
    }

    private void ring(Canvas c, float cx, float cy, float rad, int color, float stroke) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setColor(color);
        c.drawCircle(cx, cy, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        hitN = 0;
        backdrop(c, w, h);
        if (mode == SOLAR) drawSolar(c, w, h);
        else if (mode == STARS) drawStars(c, w, h);
        else drawGalaxy(c, w, h);
    }

    // ------------------------------------------------------------ solar system
    private double rmaxCur = 4;
    private float rViewCur = 100;

    private float radSolar(double r) { return (float) (rViewCur * Math.log(1 + r / 0.25) / Math.log(1 + rmaxCur / 0.25)); }

    private void drawSolar(Canvas c, float w, float h) {
        float unit = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        rViewCur = unit * 0.46f;
        rmaxCur = solarRmax(zoom);
        if (!haveSolar) return;

        // orbits
        for (int i = 0; i < 8; i++) {
            if (Orbits.AXIS[i] > rmaxCur * 1.03) continue;
            path.reset();
            for (int j = 0; j < orbTheta[i].length; j++) {
                float rr = radSolar(orbR[i][j]);
                float x = cx + rr * (float) Math.cos(orbTheta[i][j]), y = cy - rr * (float) Math.sin(orbTheta[i][j]);
                if (j == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(unit * (i == 2 ? 0.004f : 0.0026f));
            p.setColor(i == 2 ? 0xAA2ECC71 : GRAYLINE);
            c.drawPath(path, p);
        }

        // the part of its orbit Earth has already travelled this year
        if (trTheta != null && trTheta.length > 1) {
            path.reset();
            for (int j = 0; j < trTheta.length; j++) {
                float rr = radSolar(trR[j]);
                float x = cx + rr * (float) Math.cos(trTheta[j]), y = cy - rr * (float) Math.sin(trTheta[j]);
                if (j == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(unit * 0.011f);
            p.setColor(GREEN);
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);

        // equinoxes, solstices, perihelion, aphelion
        float er = radSolar(1.0);
        for (int q = 0; q < 4; q++) {
            float rr = radSolar(qR[q]);
            float x = cx + rr * (float) Math.cos(qTheta[q]), y = cy - rr * (float) Math.sin(qTheta[q]);
            p.setColor(Color.WHITE);
            c.drawRect(x - unit * 0.007f, y - unit * 0.007f, x + unit * 0.007f, y + unit * 0.007f, p);
            if (rmaxCur < 6) {
                float ox = (float) Math.cos(qTheta[q]) * unit * 0.02f, oy = -(float) Math.sin(qTheta[q]) * unit * 0.02f;
                text(c, QNAME[q], x + ox * 1.2f, y + oy * 1.2f + unit * 0.01f, unit * 0.026f, 0xFFB8B8D0, false,
                        Math.cos(qTheta[q]) > 0.3 ? Paint.Align.LEFT : (Math.cos(qTheta[q]) < -0.3 ? Paint.Align.RIGHT : Paint.Align.CENTER));
            }
        }
        for (int k = 0; k < 2; k++) {
            float rr = radSolar(apR[k]);
            float x = cx + rr * (float) Math.cos(apTheta[k]), y = cy - rr * (float) Math.sin(apTheta[k]);
            p.setColor(0xFFFF8A65);
            c.drawCircle(x, y, unit * 0.006f, p);
            if (rmaxCur < 6) text(c, k == 0 ? "P" : "A", x + unit * 0.012f, y - unit * 0.008f, unit * 0.024f, 0xFFFF8A65, true, Paint.Align.LEFT);
        }

        // Sun
        p.setShader(new RadialGradient(cx, cy, unit * 0.06f, 0xFFFFE082, 0x00FFC107, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, unit * 0.06f, p);
        p.setShader(null);
        p.setColor(GOLD);
        c.drawCircle(cx, cy, unit * 0.026f, p);
        text(c, "Sun", cx, cy + unit * 0.062f, unit * 0.027f, Color.WHITE, false, Paint.Align.CENTER);
        hitX[SUN] = cx; hitY[SUN] = cy; hitN = Math.max(hitN, SUN + 1);

        // planets
        for (int i = 0; i < 8; i++) {
            hitX[i] = -1000; hitY[i] = -1000;
            if (pR[i] > rmaxCur * 1.03) continue;
            float rr = radSolar(pR[i]);
            float x = cx + rr * (float) Math.cos(pTheta[i]), y = cy - rr * (float) Math.sin(pTheta[i]);
            hitX[i] = x; hitY[i] = y;
            float rad = unit * PSIZE[i];
            p.setStyle(Paint.Style.FILL);
            p.setColor(BG);
            c.drawCircle(x, y, rad + unit * 0.005f, p);
            p.setColor(PCOLOR[i]);
            c.drawCircle(x, y, rad, p);
            if (i == 5) { // Saturn's ring
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(unit * 0.004f);
                c.save();
                c.rotate(-20f, x, y);
                c.drawOval(x - rad * 2f, y - rad * 0.6f, x + rad * 2f, y + rad * 0.6f, p);
                c.restore();
                p.setStyle(Paint.Style.FILL);
            }
            if (i == 2) {
                ring(c, x, y, rad + unit * 0.014f, GREEN, unit * 0.005f);
                text(c, "\uD83D\uDCCD Earth \u2014 you are here", x, y - rad - unit * 0.03f, unit * 0.03f, GREEN, true, Paint.Align.CENTER);
            } else {
                text(c, Orbits.NAMES[i], x, y + rad + unit * 0.034f, unit * 0.026f, Color.WHITE, false, Paint.Align.CENTER);
            }
        }
        // Earth's direction of travel
        if (hitX[2] > -500) {
            double th = pTheta[2];
            float dx = (float) -Math.sin(th), dy = (float) -Math.cos(th);
            float ex = hitX[2], ey = hitY[2];
            float x0 = ex + dx * unit * 0.04f, y0 = ey + dy * unit * 0.04f;
            float x1 = ex + dx * unit * 0.13f, y1 = ey + dy * unit * 0.13f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(unit * 0.007f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(GREEN);
            c.drawLine(x0, y0, x1, y1, p);
            double ang = Math.atan2(dy, dx);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double a2 = ang + Math.PI + sgn * 0.5;
                c.drawLine(x1, y1, x1 + (float) Math.cos(a2) * unit * 0.03f, y1 + (float) Math.sin(a2) * unit * 0.03f, p);
            }
            p.setStyle(Paint.Style.FILL);
            text(c, "Earth moves this way", x1 + dx * unit * 0.02f, y1 + dy * unit * 0.02f + unit * 0.008f, unit * 0.022f, GREEN, false,
                    dx > 0.3f ? Paint.Align.LEFT : (dx < -0.3f ? Paint.Align.RIGHT : Paint.Align.CENTER));
        }
        hitN = 9;

        if (selected >= 0 && selected <= 8) ring(c, hitX[selected], hitY[selected], unit * 0.05f, GOLD, unit * 0.004f);

        text(c, "top view \u2022 distances squeezed so the inner planets fit", cx, h - unit * 0.015f, unit * 0.024f, 0xFF8A8AA8, false, Paint.Align.CENTER);
        text(c, "\u2192 March-equinox direction", w - unit * 0.015f, cy - unit * 0.012f, unit * 0.022f, 0xFF8A8AA8, false, Paint.Align.RIGHT);
    }

    // ------------------------------------------------------------ nearby stars
    private final float[][] used = new float[80][4];
    private int usedN = 0;

    private boolean overlaps(float l, float t, float r, float b) {
        for (int i = 0; i < usedN; i++) {
            if (l < used[i][2] && r > used[i][0] && t < used[i][3] && b > used[i][1]) return true;
        }
        return false;
    }

    private void drawStars(Canvas c, float w, float h) {
        float unit = Math.min(w, h), cx = w / 2f, cy = h / 2f, R = unit * 0.45f;
        double dmax = starsDmax(zoom), d0 = 2.0, k = Math.log(1 + dmax / d0);

        double[] marks = {5, 10, 25, 50, 100, 250, 500, 1000, 2000};
        for (double m : marks) {
            if (m > dmax * 1.02) continue;
            float rr = (float) (R * Math.log(1 + m / d0) / k);
            ring(c, cx, cy, rr, GRAYLINE, unit * 0.0025f);
            text(c, (int) m + " ly", cx + rr * 0.707f + unit * 0.01f, cy - rr * 0.707f - unit * 0.005f, unit * 0.022f, 0xFF8A8AA8, false, Paint.Align.LEFT);
        }

        // Galactic centre is straight up, galactic rotation points left
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(unit * 0.003f);
        p.setColor(0xFF5A5A88);
        c.drawLine(cx, cy, cx, cy - R, p);
        c.drawLine(cx, cy, cx - R, cy, p);
        p.setStyle(Paint.Style.FILL);
        text(c, "\u2191 towards the Galactic centre", cx, cy - R - unit * 0.01f, unit * 0.024f, 0xFFB8B8D0, false, Paint.Align.CENTER);
        text(c, "\u2190 Sun's path round the galaxy", cx - R - unit * 0.005f, cy + unit * 0.035f, unit * 0.022f, 0xFFB8B8D0, false, Paint.Align.LEFT);

        int n = Cosmos.STARS.length;
        // dots first
        for (int i = 0; i < n; i++) {
            Cosmos.Star s = Cosmos.STARS[i];
            hitX[i] = -1000; hitY[i] = -1000;
            if (s.ly > dmax) continue;
            double dxy = Math.hypot(s.x, s.y);
            double rr = R * Math.log(1 + dxy / d0) / k;
            double ux = dxy < 1e-9 ? 0 : s.x / dxy, uy = dxy < 1e-9 ? 0 : s.y / dxy;
            float x = cx - (float) (uy * rr), y = cy - (float) (ux * rr);
            hitX[i] = x; hitY[i] = y;
            float rad = unit * (s.visible() ? 0.010f : 0.007f);
            if (s.kind == 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(unit * 0.003f);
                p.setColor(s.color);
                c.drawCircle(x, y, rad * 2.4f, p);
                p.setStyle(Paint.Style.FILL);
            }
            p.setColor(Color.argb(s.visible() ? 70 : 30, Color.red(s.color), Color.green(s.color), Color.blue(s.color)));
            c.drawCircle(x, y, rad * 2f, p);
            p.setColor(s.color);
            c.drawCircle(x, y, rad, p);
        }
        hitN = n;

        // labels, brightest first; a label that would sit on top of another one is skipped
        usedN = 0;
        float ls = unit * 0.024f, ss = unit * 0.018f;
        int sel = selected;
        for (int oi = -1; oi < n; oi++) {
            int i = oi < 0 ? sel : Cosmos.ORDER[oi];
            if (i < 0 || i >= n || (oi >= 0 && i == sel)) continue;
            if (hitX[i] < -500) continue;
            Cosmos.Star s = Cosmos.STARS[i];
            float x = hitX[i], y = hitY[i];
            float rad = unit * 0.010f;
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextSize(ls);
            float nw = tp.measureText(s.name);
            String sub = s.visible() ? "visible" : "telescope only";
            tp.setTypeface(Typeface.DEFAULT);
            tp.setTextSize(ss);
            float sw = tp.measureText(sub);
            float wd = Math.max(nw, sw);
            boolean left = x + rad + unit * 0.008f + wd > w - 2;
            float x0 = left ? x - rad - unit * 0.008f - wd : x + rad + unit * 0.008f;
            float top = y - ls * 0.9f, bottom = y + ss * 1.5f;
            if (i != sel && overlaps(x0 - 2, top, x0 + wd + 2, bottom)) continue;
            if (usedN < used.length) { used[usedN][0] = x0; used[usedN][1] = top; used[usedN][2] = x0 + wd; used[usedN][3] = bottom; usedN++; }
            Paint.Align al = left ? Paint.Align.RIGHT : Paint.Align.LEFT;
            float tx = left ? x0 + wd : x0;
            text(c, s.name, tx, y + unit * 0.004f, ls, i == sel ? GOLD : Color.WHITE, true, al);
            text(c, sub, tx, y + unit * 0.004f + ss * 1.25f, ss, s.visible() ? GREEN : 0xFF8A8AA8, false, al);
            if (i == sel) ring(c, x, y, unit * 0.03f, GOLD, unit * 0.004f);
        }

        // you
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, unit * 0.05f, 0xFFFFE082, 0x00FFC107, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, unit * 0.05f, p);
        p.setShader(null);
        p.setColor(GOLD);
        c.drawCircle(cx, cy, unit * 0.016f, p);
        text(c, "\uD83D\uDCCD Sun & Earth \u2014 you are here", cx, cy + unit * 0.075f, unit * 0.028f, GREEN, true, Paint.Align.CENTER);
        text(c, "Milky Way disk from above \u2022 \"visible\" = seen without a telescope", cx, h - unit * 0.012f, unit * 0.021f, 0xFF8A8AA8, false, Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ galaxy
    private static final double SUN_R = 26700.0; // light-years from the Galactic centre

    private float gx(double xLy, float cx, double cxLy, double s) { return cx + (float) ((xLy - cxLy) * s); }
    private float gy(double yLy, float cy, double cyLy, double s) { return cy - (float) ((yLy - cyLy) * s); }

    /** Writes a name along the longest part of a curve that is inside the view. */
    private void pathText(Canvas c, float[] xs, float[] ys, int n, String text, int color, float size, float w, float h, float minSize) {
        float m = size;
        int bestStart = -1, bestLen = 0, curStart = -1;
        for (int i = 0; i < n; i++) {
            boolean in = xs[i] > m && xs[i] < w - m && ys[i] > m && ys[i] < h - m;
            if (in) {
                if (curStart < 0) curStart = i;
                int len = i - curStart + 1;
                if (len > bestLen) { bestLen = len; bestStart = curStart; }
            } else curStart = -1;
        }
        if (bestLen < 4) return;
        boolean rev = xs[bestStart] > xs[bestStart + bestLen - 1]; // keep the text upright
        Path pth = new Path();
        for (int j = 0; j < bestLen; j++) {
            int idx = rev ? bestStart + bestLen - 1 - j : bestStart + j;
            if (j == 0) pth.moveTo(xs[idx], ys[idx]); else pth.lineTo(xs[idx], ys[idx]);
        }
        float len = new PathMeasure(pth, false).getLength();
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTextSize(size);
        float tw = tp.measureText(text);
        if (len < tw * 1.1f) {
            size = size * len / (tw * 1.1f);
            if (size < minSize) return;
            tp.setTextSize(size);
            tw = tp.measureText(text);
        }
        float hOff = (len - tw) / 2f, vOff = -size * 0.45f;
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.25f);
        tp.setColor(BG);
        c.drawTextOnPath(text, pth, hOff, vOff, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(color);
        c.drawTextOnPath(text, pth, hOff, vOff, tp);
    }

    private void drawGalaxy(Canvas c, float w, float h) {
        float unit = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        double base = (unit * 0.46) / 55000.0;
        double s = base * galaxyScale(zoom);
        double cxLy = 0, cyLy = -SUN_R * ((Math.log(galaxyScale(zoom))) / Math.log(8.0)); // glide from the centre to the Sun
        float gcx = gx(0, cx, cxLy, s), gcy = gy(0, cy, cyLy, s);

        // disk and bulge
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(gcx, gcy, (float) (50000 * s), new int[]{0x66385A9A, 0x33203060, 0x00000000}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(gcx, gcy, (float) (50000 * s), p);
        p.setShader(new RadialGradient(gcx, gcy, (float) (7000 * s), 0xCCFFE9A8, 0x00FFE9A8, Shader.TileMode.CLAMP));
        c.drawCircle(gcx, gcy, (float) (7000 * s), p);
        p.setShader(null);

        // bar
        c.save();
        c.rotate(30f, gcx, gcy);
        p.setColor(0x55FFD27E);
        c.drawOval(gcx - (float) (8000 * s), gcy - (float) (1800 * s), gcx + (float) (8000 * s), gcy + (float) (1800 * s), p);
        c.restore();

        // spiral arms (schematic, 4 arms, pitch 15 degrees) with their names written along them
        double tanP = Math.tan(Math.toRadians(15));
        int[] acol = {0xFF7EC8FF, 0xFFFFB3D9, 0xFFFFD27E, 0xFFA8FFB0};
        double[] phi = {-90, -180, 0, 90};
        String[] aname = {"Sagittarius Arm", "Perseus Arm", "Scutum\u2013Centaurus Arm", "Norma\u2013Outer Arm"};
        int np = 0;
        float[] axs = new float[120], ays = new float[120];
        for (int a = 0; a < 4; a++) {
            path.reset();
            np = 0;
            for (double r = 8000; r <= 46000 && np < 120; r += 320) {
                double th = Math.toRadians(phi[a]) + Math.log(r / 21000.0) / tanP;
                float x = gx(r * Math.cos(th), cx, cxLy, s), y = gy(r * Math.sin(th), cy, cyLy, s);
                if (np == 0) path.moveTo(x, y); else path.lineTo(x, y);
                axs[np] = x; ays[np] = y; np++;
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth((float) Math.max(unit * 0.012, 3000 * s));
            p.setColor(Color.argb(70, Color.red(acol[a]), Color.green(acol[a]), Color.blue(acol[a])));
            c.drawPath(path, p);
            p.setStrokeWidth(unit * 0.004f);
            p.setColor(Color.argb(200, Color.red(acol[a]), Color.green(acol[a]), Color.blue(acol[a])));
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);
        // names after all arms are drawn so they stay on top
        for (int a = 0; a < 4; a++) {
            np = 0;
            for (double r = 8000; r <= 46000 && np < 120; r += 320) {
                double th = Math.toRadians(phi[a]) + Math.log(r / 21000.0) / tanP;
                axs[np] = gx(r * Math.cos(th), cx, cxLy, s); ays[np] = gy(r * Math.sin(th), cy, cyLy, s); np++;
            }
            pathText(c, axs, ays, np, aname[a], acol[a], unit * 0.034f, w, h, unit * 0.02f);
        }

        // the Sun's orbit round the galaxy
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(unit * 0.003f);
        p.setColor(0xAAFFFFFF);
        p.setPathEffect(new DashPathEffect(new float[]{unit * 0.02f, unit * 0.015f}, 0));
        c.drawCircle(gcx, gcy, (float) (SUN_R * s), p);
        float sx = gx(0, cx, cxLy, s), sy = gy(-SUN_R, cy, cyLy, s);
        c.drawLine(gcx, gcy, sx, sy, p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);

        // Orion Spur (a short local arm piece at the Sun) with its name
        float spur = (float) (5000 * s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth((float) Math.max(unit * 0.008, 900 * s));
        p.setColor(0xAA6FE0D0);
        c.drawLine(sx - spur, sy + spur * 0.18f, sx + spur, sy - spur * 0.18f, p);
        p.setStyle(Paint.Style.FILL);
        float[] sxs = new float[12], sys = new float[12];
        for (int i = 0; i < 12; i++) {
            float f = i / 11f;
            sxs[i] = sx - spur + 2 * spur * f;
            sys[i] = sy + spur * 0.18f - 2 * spur * 0.18f * f;
        }
        pathText(c, sxs, sys, 12, "Orion Spur", 0xFF6FE0D0, unit * 0.028f, w, h, unit * 0.016f);

        // centre and Sun
        p.setColor(Color.WHITE);
        c.drawCircle(gcx, gcy, unit * 0.008f, p);
        text(c, "Galactic centre (Sgr A*)", gcx, gcy - unit * 0.025f, unit * 0.024f, Color.WHITE, false, Paint.Align.CENTER);
        p.setShader(new RadialGradient(sx, sy, unit * 0.045f, 0xFFFFE082, 0x00FFC107, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, unit * 0.045f, p);
        p.setShader(null);
        p.setColor(GOLD);
        c.drawCircle(sx, sy, unit * 0.014f, p);
        text(c, "\uD83D\uDCCD Sun & Earth \u2014 you are here", sx, sy + unit * 0.085f, unit * 0.028f, GREEN, true, Paint.Align.CENTER);
        text(c, "26,700 light-years", (gcx + sx) / 2f + unit * 0.015f, (gcy + sy) / 2f, unit * 0.024f, Color.WHITE, false, Paint.Align.LEFT);

        // the Sun's direction of travel round the galaxy
        float ax0 = sx - unit * 0.035f, ax1 = sx - unit * 0.15f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(unit * 0.007f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(GREEN);
        c.drawLine(ax0, sy, ax1, sy, p);
        c.drawLine(ax1, sy, ax1 + unit * 0.028f, sy - unit * 0.02f, p);
        c.drawLine(ax1, sy, ax1 + unit * 0.028f, sy + unit * 0.02f, p);
        p.setStyle(Paint.Style.FILL);
        text(c, "moves this way", (ax0 + ax1) / 2f, sy - unit * 0.03f, unit * 0.022f, GREEN, false, Paint.Align.CENTER);
        text(c, "schematic view from above the galaxy", cx, h - unit * 0.012f, unit * 0.022f, 0xFF8A8AA8, false, Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ touch
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mode == GALAXY) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float unit = Math.min(getWidth(), getHeight());
            int best = -1;
            float bestD = unit * 0.09f;
            for (int i = 0; i < hitN; i++) {
                float d = (float) Math.hypot(e.getX() - hitX[i], e.getY() - hitY[i]);
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best >= 0) {
                selected = best;
                invalidate();
                if (listener != null) listener.onSelect(mode, best);
            }
            return true;
        }
        return true;
    }
}
