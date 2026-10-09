package com.blackfalcon.earthinspace;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.view.Surface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {

    private static final int GREEN = 0xFF2ECC71, GOLD = 0xFFFFC107, GRAY = 0xFFA0A0C0, CARD = 0xFF0C0C1E, EDGE = 0xFF3A3A60;
    private static final long DAY = 86400000L;
    private static final String[] QUARTER = {"March equinox", "June solstice", "September equinox", "December solstice"};

    private int mode = SpaceView.SOLAR;
    private long offsetMs = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tickCount = 0;

    private SpaceView space;
    private SeekBar zoomSeek, dateSeek;
    private TextView zoomTv, dateTv, infoTitle, infoBody;
    private LinearLayout dateBox;
    private ScrollView mainScroll, addressScroll;
    private final TextView[] tabs = new TextView[4];
    private boolean seekFromCode = false;

    // compass view
    private boolean compassOn = false;
    private SensorManager sm;
    private Sensor rotSensor;
    private LocationManager lm;
    private SharedPreferences sp;
    private SkyCompassView compassView;
    private LinearLayout compassCard;
    private TextView compassBtn, compassStatus, compassLegend;
    private final float[] rotMat = new float[9], remapped = new float[9], orient = new float[3];
    private float sinAvg = 0f, cosAvg = 1f, heading = 0f, declination = 0f;
    private boolean firstSample = true, hasLoc = false, locSearching = false;
    private double lat, lon;
    private Runnable locTimeout;
    private long lastLegendMs = 0;
    private Sky.Target[] skyTargets;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            tickCount++;
            if (mode == SpaceView.SOLAR) {
                if (offsetMs == 0 && tickCount % 60 == 0) rebuildSolar();
                if (offsetMs == 0) updateInfo();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sp = getSharedPreferences("space", MODE_PRIVATE);
        if (sp.contains("lat") && sp.contains("lon")) {
            lat = Double.longBitsToDouble(sp.getLong("lat", 0));
            lon = Double.longBitsToDouble(sp.getLong("lon", 0));
            hasLoc = true;
            declination = new GeomagneticField((float) lat, (float) lon, 0f, System.currentTimeMillis()).getDeclination();
        }
        buildUi();
        setMode(SpaceView.SOLAR);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(tick);
        if (compassOn) { startSensors(); startLocation(); }
    }
    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        stopSensors();
        stopLocation();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // -------------------------------------------------------------------- UI
    private TextView tv(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(r));
        return g;
    }

    private LinearLayout card(int stroke) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(round(CARD, stroke, 16));
        return c;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(top), 0, 0);
        return p;
    }

    private TextView pill(String s, int color) {
        TextView b = tv(13, color, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), dp(10), dp(4), dp(10));
        b.setBackground(round(0xFF101028, color, 20));
        b.setClickable(true);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF02030A);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(12), dp(16), dp(4));
        TextView title = tv(22, Color.WHITE, true);
        title.setText("\uD83C\uDF0D Earth in Space");
        TextView sub = tv(13, GRAY, false);
        sub.setText("Where is Earth right now? Zoom out step by step.");
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(title);
        titles.addView(sub);
        compassBtn = tv(24, Color.WHITE, false);
        compassBtn.setText("\uD83E\uDDED");
        compassBtn.setGravity(Gravity.CENTER);
        compassBtn.setClickable(true);
        compassBtn.setOnClickListener(v -> toggleCompass());
        headRow.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headRow.addView(compassBtn, new LinearLayout.LayoutParams(dp(52), dp(52)));
        head.addView(headRow);
        styleCompassButton();

        // tabs
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(dp(10), dp(6), dp(10), dp(6));
        String[] names = {"\u2600\uFE0F Solar", "\u2B50 Stars", "\uD83C\uDF0C Galaxy", "\uD83D\uDD2D Address"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tabs[i] = pill(names[i], GREEN);
            tabs[i].setOnClickListener(v -> setMode(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            tabRow.addView(tabs[i], lp);
        }

        // main page: picture, controls, info
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(4), dp(10), dp(10));

        space = new SpaceView(this);
        space.setListener((m, i) -> { updateInfo(); });
        int side = getResources().getDisplayMetrics().widthPixels - dp(20);
        box.addView(space, new LinearLayout.LayoutParams(side, side));

        compassCard = card(GREEN);
        compassCard.setVisibility(View.GONE);
        TextView ct = tv(16, GREEN, true);
        ct.setText("\uD83E\uDDED Compass \u2014 which way is it in space?");
        compassStatus = tv(12, GRAY, false);
        compassStatus.setPadding(0, dp(2), 0, dp(4));
        compassView = new SkyCompassView(this);
        compassLegend = tv(13, Color.WHITE, false);
        compassLegend.setLineSpacing(0, 1.12f);
        compassLegend.setPadding(0, dp(6), 0, 0);
        compassCard.addView(ct);
        compassCard.addView(compassStatus);
        compassCard.addView(compassView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, side));
        compassCard.addView(compassLegend);
        box.addView(compassCard, lp(8));

        LinearLayout controls = card(EDGE);
        zoomTv = tv(13, GRAY, false);
        zoomSeek = new SeekBar(this);
        zoomSeek.setMax(100);
        zoomSeek.setProgressTintList(ColorStateList.valueOf(GREEN));
        zoomSeek.setThumbTintList(ColorStateList.valueOf(GREEN));
        zoomSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (seekFromCode) return;
                space.setZoom(p / 100f);
                updateZoomLabel();
                updateInfo();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        controls.addView(zoomTv);
        controls.addView(zoomSeek);

        dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        dateBox.setPadding(0, dp(10), 0, 0);
        dateTv = tv(14, Color.WHITE, true);
        dateSeek = new SeekBar(this);
        dateSeek.setMax(730); // +/- one year in 1 day steps
        dateSeek.setProgress(365);
        dateSeek.setProgressTintList(ColorStateList.valueOf(GOLD));
        dateSeek.setThumbTintList(ColorStateList.valueOf(GOLD));
        dateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser || seekFromCode) return;
                offsetMs = (p - 365) * DAY;
                rebuildSolar();
                updateInfo();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        LinearLayout btnRow = new LinearLayout(this);
        String[] bl = {"\u221230 d", "\u22121 d", "Now", "+1 d", "+30 d"};
        final long[] delta = {-30 * DAY, -DAY, 0, DAY, 30 * DAY};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView b = pill(bl[i], i == 2 ? GOLD : GREEN);
            b.setOnClickListener(v -> {
                if (idx == 2) offsetMs = 0; else offsetMs += delta[idx];
                rebuildSolar();
                updateInfo();
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            l.setMargins(dp(2), dp(6), dp(2), 0);
            btnRow.addView(b, l);
        }
        dateBox.addView(dateTv);
        dateBox.addView(dateSeek);
        dateBox.addView(btnRow);
        controls.addView(dateBox);
        box.addView(controls, lp(8));

        LinearLayout info = card(GOLD);
        infoTitle = tv(17, GOLD, true);
        infoBody = tv(14, Color.WHITE, false);
        infoBody.setLineSpacing(0, 1.15f);
        infoBody.setPadding(0, dp(6), 0, 0);
        info.addView(infoTitle);
        info.addView(infoBody);
        box.addView(info, lp(8));

        mainScroll = new ScrollView(this);
        mainScroll.addView(box);

        addressScroll = new ScrollView(this);
        addressScroll.addView(buildAddress());
        addressScroll.setVisibility(View.GONE);

        TextView credit = tv(13, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(16), dp(4), dp(16), dp(8));

        root.addView(head);
        root.addView(tabRow);
        root.addView(mainScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(addressScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(credit);
        setContentView(root);
    }

    private View buildAddress() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(6), dp(12), dp(12));

        TextView h = tv(18, Color.WHITE, true);
        h.setText("\uD83D\uDD2D Earth's cosmic address");
        TextView sub = tv(13, GRAY, false);
        sub.setText("Each step is bigger than the one before. The bar shows the size on a log scale.");
        sub.setPadding(0, dp(2), 0, dp(6));
        box.addView(h);
        box.addView(sub);

        LinearLayout speeds = card(GREEN);
        TextView st = tv(16, GREEN, true);
        st.setText("\uD83D\uDE80 You are moving, all at once:");
        TextView sb = tv(14, Color.WHITE, false);
        sb.setLineSpacing(0, 1.15f);
        sb.setPadding(0, dp(6), 0, 0);
        sb.setText("\u2022 about 0.46 km/s \u2014 Earth spinning (1,670 km/h at the equator)\n"
                + "\u2022 about 29.8 km/s \u2014 Earth going round the Sun (107,000 km/h)\n"
                + "\u2022 about 230 km/s \u2014 the Sun going round the Milky Way (about 830,000 km/h)\n"
                + "\u2022 about 370 km/s \u2014 the Solar System relative to the glow left by the Big Bang (about 1.3 million km/h), towards galactic longitude 264\u00B0, latitude +48\u00B0\n\n"
                + "These do not simply add up: they point in different directions and are measured against different things.");
        speeds.addView(st);
        speeds.addView(sb);
        box.addView(speeds, lp(6));

        for (int i = 0; i < Cosmos.LADDER.length; i++) {
            Cosmos.Level lv = Cosmos.LADDER[i];
            LinearLayout c = card(i == 7 ? GREEN : EDGE);
            TextView name = tv(17, Color.WHITE, true);
            name.setText((i + 1) + ".  " + lv.emoji + "  " + lv.name);
            TextView size = tv(13, GOLD, false);
            size.setText(lv.size);
            TextView desc = tv(14, Color.WHITE, false);
            desc.setText(lv.desc);
            desc.setPadding(0, dp(4), 0, dp(4));
            TextView light = tv(12, GRAY, false);
            light.setText("Light takes about " + Cosmos.lightTime(lv.meters) + " to cross it.");
            double f = (Math.log10(lv.meters) - 6.5) / (27.2 - 6.5);
            f = Math.max(0.03, Math.min(1.0, f));
            LinearLayout bar = new LinearLayout(this);
            View on = new View(this);
            on.setBackground(round(GREEN, GREEN, 3));
            View off = new View(this);
            off.setBackground(round(0xFF1C1C34, 0xFF1C1C34, 3));
            bar.addView(on, new LinearLayout.LayoutParams(0, dp(6), (float) f));
            bar.addView(off, new LinearLayout.LayoutParams(0, dp(6), (float) (1 - f)));
            c.addView(name);
            c.addView(size);
            c.addView(desc);
            c.addView(light);
            LinearLayout.LayoutParams bl = lp(6);
            bl.height = dp(6);
            c.addView(bar, bl);
            if (i == 0) {
                TextView you = tv(12, GREEN, true);
                you.setText("\uD83D\uDCCD You are here");
                c.addView(you, lp(6));
            }
            box.addView(c, lp(8));
        }

        TextView src = tv(11, GRAY, false);
        src.setText("Sizes are rounded and some are still being refined by astronomers. Sources: JPL approximate planet positions (Standish); "
                + "Reid et al. 2019 (ApJ 885, 131) and the GRAVITY Collaboration 2019 for the Sun's distance from the Galactic centre; "
                + "Bland-Hawthorn & Gerhard 2016 (ARA&A 54, 529); Tully et al. 2014 (Nature 513, 71) for Laniakea.");
        src.setPadding(dp(4), dp(12), dp(4), dp(8));
        box.addView(src);
        return box;
    }

    // ----------------------------------------------------------------- state
    private void setMode(int m) {
        mode = m;
        for (int i = 0; i < 4; i++) {
            boolean on = i == m;
            tabs[i].setTextColor(on ? Color.BLACK : GREEN);
            tabs[i].setBackground(on ? round(GREEN, GREEN, 20) : round(0xFF101028, GREEN, 20));
        }
        boolean addr = m == 3;
        mainScroll.setVisibility(addr ? View.GONE : View.VISIBLE);
        addressScroll.setVisibility(addr ? View.VISIBLE : View.GONE);
        if (addr) return;

        space.setMode(m);
        seekFromCode = true;
        zoomSeek.setProgress(m == SpaceView.SOLAR ? 40 : (m == SpaceView.STARS ? 55 : 0));
        seekFromCode = false;
        space.setZoom(zoomSeek.getProgress() / 100f);
        dateBox.setVisibility(m == SpaceView.SOLAR ? View.VISIBLE : View.GONE);
        updateZoomLabel();
        if (m == SpaceView.SOLAR) rebuildSolar();
        updateInfo();
    }

    private void updateZoomLabel() {
        float z = zoomSeek.getProgress() / 100f;
        if (mode == SpaceView.SOLAR) {
            zoomTv.setText(String.format(Locale.US, "Zoom \u2014 showing out to %.1f AU (1 AU = Earth\u2013Sun distance)", SpaceView.solarRmax(z)));
        } else if (mode == SpaceView.STARS) {
            zoomTv.setText(String.format(Locale.US, "Zoom \u2014 showing stars within %,.0f light-years", SpaceView.starsDmax(z)));
        } else {
            zoomTv.setText(String.format(Locale.US, "Zoom \u2014 \u00D7%.1f (slide right to fly in to the Sun's neighbourhood)", SpaceView.galaxyScale(z)));
        }
    }

    // ------------------------------------------------------------- solar data
    private double nowJd() { return Orbits.jd(System.currentTimeMillis() + offsetMs); }

    private void rebuildSolar() {
        double jd = nowJd();
        double[][] pos = new double[8][];
        double[][] orb = new double[8][];
        for (int i = 0; i < 8; i++) {
            pos[i] = Orbits.helio(i, jd);
            int n = 180;
            double period = Math.pow(Orbits.AXIS[i], 1.5) * 365.25;
            double[] arr = new double[2 * n];
            for (int j = 0; j < n; j++) {
                double[] q = Orbits.helio(i, jd + j * period / n);
                arr[2 * j] = Math.atan2(q[1], q[0]);
                arr[2 * j + 1] = Math.hypot(q[0], q[1]);
            }
            orb[i] = arr;
        }
        double start = Orbits.yearStartJd(jd);
        int n = (int) Math.ceil(jd - start) + 1;
        double[] tx = new double[n], ty = new double[n];
        for (int j = 0; j < n; j++) {
            double[] q = Orbits.helio(2, Math.min(start + j, jd));
            tx[j] = q[0]; ty[j] = q[1];
        }
        double[][] quarters = new double[4][];
        for (int q = 0; q < 4; q++) quarters[q] = Orbits.helio(2, Orbits.quarterOfYear(jd, q));
        double[] ex = Orbits.extremes(jd);
        double[][] periAp = {Orbits.helio(2, ex[0]), Orbits.helio(2, ex[1])};
        space.setSolar(pos, orb, new double[][]{tx, ty}, quarters, periAp);
    }

    // ------------------------------------------------------------------ info
    private String utc(long ms, String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    private void updateInfo() {
        if (mode == SpaceView.SOLAR) solarInfo();
        else if (mode == SpaceView.STARS) starsInfo();
        else galaxyInfo();
    }

    private void solarInfo() {
        long ms = System.currentTimeMillis() + offsetMs;
        double jd = Orbits.jd(ms);
        boolean live = offsetMs == 0;

        seekFromCode = true;
        dateSeek.setProgress((int) Math.max(0, Math.min(730, Math.round(offsetMs / (double) DAY) + 365)));
        seekFromCode = false;
        dateTv.setText((live ? "Now \u2022 " : "Date \u2022 ") + utc(ms, "d MMM yyyy, HH:mm") + " UTC"
                + (live ? "" : "  (" + (offsetMs > 0 ? "+" : "\u2212") + Math.abs(Math.round(offsetMs / (double) DAY)) + " days)"));

        int sel = space.getSelected();
        double[] earth = Orbits.helio(2, jd);
        double rE = Orbits.norm(earth);
        StringBuilder sb = new StringBuilder();

        if (sel == SpaceView.SUN) {
            infoTitle.setText("\u2600\uFE0F The Sun \u2014 our star");
            sb.append(String.format(Locale.US, "Distance from Earth: %.4f AU (%.2f million km)\n", rE, rE * Orbits.AU_KM / 1e6));
            sb.append(String.format(Locale.US, "Its light takes %s to reach Earth.\n", mmss(rE * 499.0048)));
            sb.append("Width: about 1.39 million km (109 Earths in a row).\n");
            sb.append("Holds 99.8% of all the mass in the Solar System.\n");
            sb.append("It goes round the centre of the Milky Way once every ~230 million years.");
        } else if (sel >= 0 && sel < 8 && sel != 2) {
            double[] p = Orbits.helio(sel, jd);
            double r = Orbits.norm(p), d = Orbits.dist(p, earth);
            infoTitle.setText("\uD83E\uDE90 " + Orbits.NAMES[sel]);
            sb.append(String.format(Locale.US, "Distance from the Sun: %.2f AU (%,.0f million km)\n", r, r * Orbits.AU_KM / 1e6));
            sb.append(String.format(Locale.US, "Distance from Earth right now: %.2f AU (%,.0f million km)\n", d, d * Orbits.AU_KM / 1e6));
            sb.append(String.format(Locale.US, "Its light takes %s to reach Earth.\n", mmss(d * 499.0048)));
            double years = Math.pow(Orbits.AXIS[sel], 1.5);
            sb.append(years < 1 ? String.format(Locale.US, "One year there: %.0f Earth days.", years * 365.25)
                    : String.format(Locale.US, "One year there: %.1f Earth years.", years));
        } else {
            infoTitle.setText(live ? "\uD83D\uDCCD Earth \u2014 right now" : "\uD83D\uDCCD Earth on " + utc(ms, "d MMM yyyy"));
            double speed = Orbits.earthSpeedKmS(jd);
            double start = Orbits.yearStartJd(jd);
            double travelledKm = Orbits.pathAu(start, jd) * Orbits.AU_KM;
            int[] idx = new int[1];
            double nq = Orbits.nextQuarter(jd, idx);
            double[] ex = Orbits.extremes(jd);
            double lon = Orbits.norm360(Math.toDegrees(Math.atan2(earth[1], earth[0])));
            sb.append("Time: ").append(utc(ms, "yyyy-MM-dd HH:mm:ss")).append(" UTC\n");
            sb.append(String.format(Locale.US, "Distance from the Sun: %.4f AU (%.2f million km)\n", rE, rE * Orbits.AU_KM / 1e6));
            sb.append(String.format(Locale.US, "Speed round the Sun: %.2f km/s (%,.0f km/h)\n", speed, speed * 3600));
            sb.append("Sunlight needs ").append(mmss(rE * 499.0048)).append(" to get here.\n");
            sb.append(String.format(Locale.US, "Angle round the Sun: %.0f\u00B0 from the March-equinox direction.\n", lon));
            sb.append(String.format(Locale.US, "Travelled along its orbit since 1 Jan: about %,.0f km", Math.round(travelledKm / 100.0) * 100.0));
            sb.append(live ? " (and growing by about 30 km every second).\n" : ".\n");
            sb.append("Next: ").append(QUARTER[idx[0]]).append(" on ").append(utc(Orbits.millis(nq), "d MMM yyyy, HH:mm"))
                    .append(" UTC (in ").append(Math.max(0, Math.round(nq - jd))).append(" days).\n");
            sb.append(String.format(Locale.US, "Closest to the Sun (P): about %s \u2022 farthest (A): about %s.\n",
                    utc(Orbits.millis(ex[0]), "d MMM"), utc(Orbits.millis(ex[1]), "d MMM")));
            sb.append("Times are accurate to about 15 minutes. Tap a planet to learn about it.");
        }
        infoBody.setText(sb.toString());
    }

    private String mmss(double seconds) {
        if (seconds < 120) return String.format(Locale.US, "%.0f seconds", seconds);
        if (seconds < 7200) return String.format(Locale.US, "%d min %02d s", (int) (seconds / 60), (int) (seconds % 60));
        return String.format(Locale.US, "%.1f hours", seconds / 3600);
    }

    private void starsInfo() {
        int sel = space.getSelected();
        if (sel < 0 || sel >= Cosmos.STARS.length) {
            infoTitle.setText("\u2B50 Stars near Earth");
            infoBody.setText("This is the flat disk of the Milky Way seen from above, with the Sun in the middle. "
                    + "Up points to the centre of the galaxy; left is the way the Sun is travelling.\n\n"
                    + "The nearest star system is Alpha Centauri, only 4.37 light-years away. "
                    + "Slide the zoom to see stars out to 2,000 light-years. "
                    + "Each star says \"visible\" if you can see it with your own eyes, or \"telescope only\" if it is too faint. Tap a star to learn about it.");
            return;
        }
        Cosmos.Star s = Cosmos.STARS[sel];
        infoTitle.setText("\u2B50 " + s.name);
        String dist = s.ly < 100 ? String.format(Locale.US, "%.1f", s.ly) : String.format(Locale.US, "%,.0f", s.ly);
        String kindName = s.kind == 1 ? "Star cluster" : (s.kind == 2 ? "Nebula (a cloud where stars are born)" : "Star");
        infoBody.setText(kindName + "\n" + s.visibleLine() + "\n\n"
                + "Distance: " + dist + " light-years (about " + String.format(Locale.US, "%,.0f", s.ly * 9.4607) + " trillion km).\n"
                + "The light you see left it about " + dist + " years ago, so you see the star as it was then.\n"
                + String.format(Locale.US, "Direction in our galaxy: galactic longitude %.0f\u00B0, latitude %+.0f\u00B0.\n", s.l, s.b)
                + "Distances here are approximate (they differ a little between catalogues).");
    }

    private void galaxyInfo() {
        infoTitle.setText("\uD83C\uDF0C Earth in the Milky Way");
        infoBody.setText("Place: the Orion Spur, a small arm piece between the Sagittarius and Perseus arms.\n"
                + "Distance from the Galactic centre (the black hole Sgr A*): about 26,700 light-years.\n"
                + "Height above the middle of the disk: only about 65 light-years.\n"
                + "The Sun goes round the galaxy at about 230 km/s, one lap takes about 230 million years, "
                + "so the Sun has made around 20 laps. The last time the Solar System was at this spot, the first dinosaurs were appearing.\n"
                + "We move towards galactic longitude 90\u00B0, in the direction of the constellation Cygnus.\n\n"
                + "The arms are a schematic drawing, not an exact map. Sources: Reid et al. 2019; GRAVITY 2019; Bland-Hawthorn & Gerhard 2016.");
    }

    // ---------------------------------------------------------------- compass
    private void styleCompassButton() {
        compassBtn.setBackground(round(compassOn ? GREEN : 0xFF101028, GREEN, 26));
        compassBtn.setAlpha(compassOn ? 1f : 0.85f);
    }

    private boolean hasPerm() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void toggleCompass() {
        compassOn = !compassOn;
        styleCompassButton();
        compassCard.setVisibility(compassOn ? View.VISIBLE : View.GONE);
        if (compassOn) {
            if (rotSensor == null) compassStatus.setText("This phone has no compass sensor, so the dial cannot turn.");
            if (!hasPerm() && Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
            startSensors();
            startLocation();
            recomputeTargets();
        } else {
            stopSensors();
            stopLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (compassOn) { startLocation(); updateCompassStatus(); }
    }

    private void startSensors() {
        if (rotSensor != null) sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private void stopSensors() { sm.unregisterListener(this); }

    private void startLocation() {
        if (!compassOn || !hasPerm()) { updateCompassStatus(); return; }
        stopLocation();
        boolean any = false;
        try {
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(pr)) { lm.requestLocationUpdates(pr, 0, 0, this); any = true; }
            }
            if (!hasLoc) {
                Location last = null;
                for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                    Location l = lm.getLastKnownLocation(pr);
                    if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
                }
                if (last != null) setLocation(last.getLatitude(), last.getLongitude(), false);
            }
        } catch (SecurityException ignored) { }
        locSearching = any;
        if (any) {
            locTimeout = () -> { stopLocation(); updateCompassStatus(); };
            handler.postDelayed(locTimeout, 25000);
        }
        updateCompassStatus();
    }

    private void stopLocation() {
        locSearching = false;
        if (locTimeout != null) { handler.removeCallbacks(locTimeout); locTimeout = null; }
        try { lm.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    private void setLocation(double la, double lo, boolean save) {
        lat = la; lon = lo; hasLoc = true;
        declination = new GeomagneticField((float) la, (float) lo, 0f, System.currentTimeMillis()).getDeclination();
        if (save) {
            sp.edit().putLong("lat", Double.doubleToRawLongBits(la)).putLong("lon", Double.doubleToRawLongBits(lo)).apply();
        }
        recomputeTargets();
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!locSearching) return;
        stopLocation();
        setLocation(l.getLatitude(), l.getLongitude(), true);
        updateCompassStatus();
    }

    @Override public void onStatusChanged(String p, int st, Bundle e) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    private void updateCompassStatus() {
        if (!compassOn) return;
        String st;
        if (rotSensor == null) st = "This phone has no compass sensor, so the dial cannot turn.";
        else if (!hasPerm()) st = "Allow location so I can work out where things are in YOUR sky. (The dial still shows north.)";
        else if (hasLoc) st = String.format(Locale.US, "Hold the phone flat. Location %.2f\u00B0, %.2f\u00B0%s \u2022 true north", lat, lon, locSearching ? " (updating\u2026)" : "");
        else st = locSearching ? "Finding your location\u2026" : "Turn GPS on to see where things are in your sky.";
        compassStatus.setText(st);
    }

    private void recomputeTargets() {
        if (!hasLoc) { skyTargets = null; compassView.setTargets(null); compassLegend.setText(""); return; }
        skyTargets = Sky.targets(Orbits.jd(System.currentTimeMillis()), lat, lon);
        compassView.setTargets(skyTargets);
        updateLegend();
    }

    private static String dir8(double az) {
        String[] n = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return n[(int) Math.round(az / 45.0) % 8];
    }

    private void updateLegend() {
        if (skyTargets == null) return;
        StringBuilder sb = new StringBuilder();
        for (Sky.Target t : skyTargets) {
            double diff = ((t.az - heading + 540.0) % 360.0) - 180.0;
            String turn = Math.abs(diff) < 5 ? "straight ahead"
                    : (diff > 0 ? String.format(Locale.US, "turn right %.0f\u00B0", diff) : String.format(Locale.US, "turn left %.0f\u00B0", -diff));
            String where = t.alt > 0 ? String.format(Locale.US, "%.0f\u00B0 above the horizon", t.alt)
                    : String.format(Locale.US, "%.0f\u00B0 below the horizon (through the ground)", -t.alt);
            sb.append(t.emoji).append(" ").append(t.name).append("\n   ").append(dir8(t.az))
                    .append(String.format(Locale.US, " (%.0f\u00B0)", t.az)).append(" \u2022 ").append(where).append(" \u2022 ").append(turn).append("\n");
        }
        sb.append("\nEarth is moving towards the direction marked \uD83C\uDF0D \u2014 about 30 km every second. "
                + "The Sun carries us towards \uD83C\uDF00 (the constellation Cygnus).");
        compassLegend.setText(sb.toString().trim());
    }

    @SuppressWarnings("deprecation")
    private int displayRotation() { return getWindowManager().getDefaultDisplay().getRotation(); }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotMat, event.values);
        int xAxis, yAxis;
        switch (displayRotation()) {
            case Surface.ROTATION_90: xAxis = SensorManager.AXIS_Y; yAxis = SensorManager.AXIS_MINUS_X; break;
            case Surface.ROTATION_180: xAxis = SensorManager.AXIS_MINUS_X; yAxis = SensorManager.AXIS_MINUS_Y; break;
            case Surface.ROTATION_270: xAxis = SensorManager.AXIS_MINUS_Y; yAxis = SensorManager.AXIS_X; break;
            default: xAxis = SensorManager.AXIS_X; yAxis = SensorManager.AXIS_Y; break;
        }
        SensorManager.remapCoordinateSystem(rotMat, xAxis, yAxis, remapped);
        SensorManager.getOrientation(remapped, orient);
        double rad = Math.toRadians(Math.toDegrees(orient[0]) + declination);
        float sn = (float) Math.sin(rad), cs = (float) Math.cos(rad);
        if (firstSample) { sinAvg = sn; cosAvg = cs; firstSample = false; }
        else { sinAvg += 0.15f * (sn - sinAvg); cosAvg += 0.15f * (cs - cosAvg); }
        heading = (float) Math.toDegrees(Math.atan2(sinAvg, cosAvg));
        if (heading < 0) heading += 360f;
        compassView.setHeading(heading);
        long now = System.currentTimeMillis();
        if (now - lastLegendMs > 400) { lastLegendMs = now; updateLegend(); }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
