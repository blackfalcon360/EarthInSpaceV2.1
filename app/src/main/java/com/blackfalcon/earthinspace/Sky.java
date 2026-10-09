package com.blackfalcon.earthinspace;

/** Where the Sun, Earth's direction of travel, the galactic centre etc. are in the sky of an observer. */
public final class Sky {
    private Sky() { }

    public static final class Target {
        public final String name, shortName, emoji;
        public double az, alt;
        Target(String name, String shortName, String emoji) { this.name = name; this.shortName = shortName; this.emoji = emoji; }
    }

    static double norm360(double d) { d %= 360.0; return d < 0 ? d + 360.0 : d; }

    /** Ecliptic longitude/latitude (of date, degrees) -> {RA, Dec} degrees. */
    static double[] eclToEq(double lonDeg, double latDeg, double eps) {
        double l = Math.toRadians(lonDeg), b = Math.toRadians(latDeg);
        double ra = Math.atan2(Math.sin(l) * Math.cos(eps) - Math.tan(b) * Math.sin(eps), Math.cos(l));
        double dec = Math.asin(Math.sin(b) * Math.cos(eps) + Math.cos(b) * Math.sin(eps) * Math.sin(l));
        return new double[]{norm360(Math.toDegrees(ra)), Math.toDegrees(dec)};
    }

    /** Galactic {l, b} degrees -> equatorial J2000 {RA, Dec} degrees. */
    public static double[] fromGalactic(double lDeg, double bDeg) {
        double raNgp = Math.toRadians(192.85948), decNgp = Math.toRadians(27.12825), lNcp = Math.toRadians(122.93192);
        double l = Math.toRadians(lDeg), b = Math.toRadians(bDeg);
        double sinDec = Math.sin(b) * Math.sin(decNgp) + Math.cos(b) * Math.cos(decNgp) * Math.cos(lNcp - l);
        double dec = Math.asin(Math.max(-1, Math.min(1, sinDec)));
        double y = Math.cos(b) * Math.sin(lNcp - l);
        double x = Math.sin(b) * Math.cos(decNgp) - Math.cos(b) * Math.sin(decNgp) * Math.cos(lNcp - l);
        return new double[]{norm360(Math.toDegrees(raNgp + Math.atan2(y, x))), Math.toDegrees(dec)};
    }

    /** {altitude, azimuth from true north clockwise} in degrees. */
    public static double[] altAz(double raDeg, double decDeg, double jd, double latDeg, double lonDeg) {
        double gmst = norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0));
        double lst = norm360(gmst + lonDeg);
        double H = Math.toRadians(lst - raDeg), phi = Math.toRadians(latDeg), d = Math.toRadians(decDeg);
        double sinAlt = Math.sin(phi) * Math.sin(d) + Math.cos(phi) * Math.cos(d) * Math.cos(H);
        double alt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinAlt))));
        double az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(d) * Math.cos(phi)) + Math.PI;
        return new double[]{alt, norm360(Math.toDegrees(az))};
    }

    private static void place(Target t, double[] eq, double jd, double lat, double lon) {
        double[] a = altAz(eq[0], eq[1], jd, lat, lon);
        t.alt = a[0];
        t.az = a[1];
    }

    public static Target[] targets(double jd, double lat, double lon) {
        double T = (jd - 2451545.0) / 36525.0;
        double eps = Math.toRadians(23.439291 - 0.0130042 * T);
        double sunLon = Orbits.sunLon(jd);

        Target sun = new Target("The Sun", "Sun", "\u2600\uFE0F");
        place(sun, eclToEq(sunLon, 0, eps), jd, lat, lon);

        // Earth moves towards the point on the ecliptic 90 degrees behind the Sun
        Target earth = new Target("Earth's direction of travel", "Earth goes here", "\uD83C\uDF0D");
        place(earth, eclToEq(sunLon - 90.0, 0, eps), jd, lat, lon);

        Target gc = new Target("Galactic centre", "Galaxy centre", "\uD83C\uDF0C");
        place(gc, new double[]{266.40499, -28.93617}, jd, lat, lon);

        Target rot = new Target("Sun's path round the galaxy", "Sun goes here", "\uD83C\uDF00");
        place(rot, fromGalactic(90.0, 0.0), jd, lat, lon);

        Target cmb = new Target("Motion through the Big Bang glow", "CMB motion", "\uD83C\uDF20");
        place(cmb, fromGalactic(264.021, 48.253), jd, lat, lon);

        Target pol = new Target("Earth's axis (Polaris)", "Polaris", "\u2B50");
        place(pol, new double[]{37.95, 89.264}, jd, lat, lon);

        return new Target[]{sun, earth, gc, rot, cmb, pol};
    }
}
