package com.blackfalcon.earthinspace;

import java.util.Calendar;
import java.util.TimeZone;

/** Planet positions and Earth motion from JPL approximate Keplerian elements (valid 1800-2050). */
public final class Orbits {
    private Orbits() { }

    public static final double AU_KM = 149597870.7;
    public static final String[] NAMES = {"Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune"};
    public static final double[] AXIS = {0.387, 0.723, 1.0, 1.524, 5.203, 9.537, 19.19, 30.07};

    // a, da, e, de, I, dI, L, dL, long.peri, d, long.node, d  (per Julian century); rows = NAMES
    private static final double[][] EL = {
        {0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749, 252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081},
        {0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890, 181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418},
        {1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668, 100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0},
        {1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343},
        {5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106},
        {9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794},
        {19.18916464, -0.00196176, 0.04725744, -0.00004397, 0.77263783, -0.00242939, 313.23810451, 428.48202785, 170.95427630, 0.40805281, 74.01692503, 0.04240589},
        {30.06992276, 0.00026291, 0.00859048, 0.00005105, 1.77004347, 0.00035372, -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664}
    };

    public static double norm360(double d) { d %= 360.0; return d < 0 ? d + 360.0 : d; }
    public static double jd(long ms) { return ms / 86400000.0 + 2440587.5; }
    public static long millis(double jd) { return Math.round((jd - 2440587.5) * 86400000.0); }

    /** Heliocentric ecliptic (J2000) position in AU. row 2 = Earth. */
    public static double[] helio(int row, double jd) {
        double T = (jd - 2451545.0) / 36525.0;
        double[] k = EL[row];
        double a = k[0] + k[1] * T, e = k[2] + k[3] * T, I = Math.toRadians(k[4] + k[5] * T);
        double L = k[6] + k[7] * T, lp = k[8] + k[9] * T, node = k[10] + k[11] * T;
        double M = norm360(L - lp);
        if (M > 180) M -= 360;
        double Mr = Math.toRadians(M);
        double E = Mr + e * Math.sin(Mr);
        for (int i = 0; i < 12; i++) E -= (E - e * Math.sin(E) - Mr) / (1 - e * Math.cos(E));
        double xp = a * (Math.cos(E) - e), yp = a * Math.sqrt(1 - e * e) * Math.sin(E);
        double w = Math.toRadians(lp - node), N = Math.toRadians(node);
        double cw = Math.cos(w), sw = Math.sin(w), cN = Math.cos(N), sN = Math.sin(N), cI = Math.cos(I), sI = Math.sin(I);
        return new double[]{
                (cw * cN - sw * sN * cI) * xp + (-sw * cN - cw * sN * cI) * yp,
                (cw * sN + sw * cN * cI) * xp + (-sw * sN + cw * cN * cI) * yp,
                (sw * sI) * xp + (cw * sI) * yp};
    }

    public static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double norm(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }

    /** Earth's speed around the Sun in km/s. */
    public static double earthSpeedKmS(double jd) {
        double dt = 0.01;
        return dist(helio(2, jd + dt), helio(2, jd - dt)) / (2 * dt) * AU_KM / 86400.0;
    }

    /** Ecliptic longitude of the Sun as seen from Earth (of date), degrees. 0 = March equinox. */
    public static double sunLon(double jd) {
        double T = (jd - 2451545.0) / 36525.0;
        double[] e = helio(2, jd);
        return norm360(Math.toDegrees(Math.atan2(-e[1], -e[0])) + 1.396971 * T);
    }

    private static double wrap180(double d) { d = norm360(d); return d > 180 ? d - 360 : d; }

    /** JD of the next equinox / solstice after jd. idx[0] = 0 March eq, 1 June sol, 2 Sept eq, 3 Dec sol. */
    public static double nextQuarter(double jd, int[] idx) {
        double L = sunLon(jd);
        int q = ((int) Math.floor(L / 90.0) + 1) % 4;
        double target = q * 90.0;
        double t = jd;
        while (wrap180(sunLon(t) - target) < 0) t += 0.5;
        double lo = t - 0.5, hi = t;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (wrap180(sunLon(mid) - target) < 0) lo = mid; else hi = mid;
        }
        idx[0] = q;
        return (lo + hi) / 2;
    }

    /** JD of the given quarter (0..3) inside the calendar year that contains jdRef. */
    public static double quarterOfYear(double jdRef, int q) {
        double start = yearStartJd(jdRef);
        double approxDay = new double[]{79, 172, 266, 355}[q];
        double target = q * 90.0;
        double lo = start + approxDay - 6, hi = start + approxDay + 6;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (wrap180(sunLon(mid) - target) < 0) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    public static double yearStartJd(double jd) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(millis(jd));
        c.set(c.get(Calendar.YEAR), Calendar.JANUARY, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return jd(c.getTimeInMillis());
    }

    /** Distance Earth has travelled along its orbit between two moments, in AU (1-day chords). */
    public static double pathAu(double jdStart, double jdNow) {
        double sum = 0, t = jdStart;
        double[] prev = helio(2, t);
        while (t < jdNow) {
            double nt = Math.min(t + 1.0, jdNow);
            double[] cur = helio(2, nt);
            sum += dist(prev, cur);
            prev = cur;
            t = nt;
        }
        return sum;
    }

    /** {jd of perihelion, jd of aphelion} in the year of jdRef (half-day resolution). */
    public static double[] extremes(double jdRef) {
        double start = yearStartJd(jdRef);
        double minR = 9, maxR = 0, tMin = start, tMax = start;
        for (double t = start; t < start + 366; t += 0.25) {
            double r = norm(helio(2, t));
            if (r < minR) { minR = r; tMin = t; }
            if (r > maxR) { maxR = r; tMax = t; }
        }
        return new double[]{tMin, tMax};
    }

    /** Equatorial J2000 (degrees) -> galactic {l, b} in degrees. */
    public static double[] toGalactic(double raDeg, double decDeg) {
        double raNgp = Math.toRadians(192.85948), decNgp = Math.toRadians(27.12825), lNcp = 122.93192;
        double ra = Math.toRadians(raDeg), dec = Math.toRadians(decDeg);
        double sinB = Math.sin(dec) * Math.sin(decNgp) + Math.cos(dec) * Math.cos(decNgp) * Math.cos(ra - raNgp);
        double b = Math.asin(Math.max(-1, Math.min(1, sinB)));
        double y = Math.cos(dec) * Math.sin(ra - raNgp);
        double x = Math.sin(dec) * Math.cos(decNgp) - Math.cos(dec) * Math.sin(decNgp) * Math.cos(ra - raNgp);
        double l = norm360(lNcp - Math.toDegrees(Math.atan2(y, x)));
        return new double[]{l, Math.toDegrees(b)};
    }
}
