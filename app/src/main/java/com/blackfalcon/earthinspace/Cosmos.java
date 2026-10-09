package com.blackfalcon.earthinspace;

import java.util.Locale;

/** Nearby stars and the "cosmic address" ladder. */
public final class Cosmos {
    private Cosmos() { }

    public static final class Star {
        public final String name;
        public final double ra, dec, ly, mag, l, b, x, y; // x toward the Galactic centre, y toward galactic rotation (ly, in the plane)
        public final int color, kind; // kind: 0 star, 1 star cluster, 2 nebula
        Star(String name, double raHours, double dec, double ly, double mag, int color, int kind) {
            this.name = name; this.ra = raHours * 15.0; this.dec = dec; this.ly = ly; this.mag = mag; this.color = color; this.kind = kind;
            double[] g = Orbits.toGalactic(this.ra, dec);
            this.l = g[0]; this.b = g[1];
            this.x = ly * Math.cos(Math.toRadians(b)) * Math.cos(Math.toRadians(l));
            this.y = ly * Math.cos(Math.toRadians(b)) * Math.sin(Math.toRadians(l));
        }
        /** Can it be seen without a telescope? (limit about magnitude 6 under a dark sky) */
        public boolean visible() { return mag <= 6.0; }
        public String visibleLine() {
            String m = String.format(Locale.US, "magnitude %.1f", mag);
            if (mag <= 5.0) return "\uD83D\uDC41 VISIBLE to the naked eye (" + m + ")";
            if (mag <= 6.0) return "\uD83D\uDC41 VISIBLE to the naked eye, but only under a dark sky (" + m + ")";
            return "\u2716 NOT visible to the naked eye \u2014 you need binoculars or a telescope (" + m + ")";
        }
    }

    private static final int WHITE = 0xFFFFFFFF, BLUEWHITE = 0xFFCFE3FF, YELLOW = 0xFFFFE08A, ORANGE = 0xFFFFA64D, RED = 0xFFFF6B5A, PINK = 0xFFFF9AD5;

    public static final Star[] STARS = {
            new Star("Alpha Centauri", 14.660, -60.835, 4.37, -0.27, YELLOW, 0),
            new Star("Barnard's Star", 17.963, 4.693, 5.96, 9.5, RED, 0),
            new Star("Wolf 359", 10.941, 7.01, 7.86, 13.5, RED, 0),
            new Star("Lalande 21185", 11.056, 35.97, 8.31, 7.5, RED, 0),
            new Star("Sirius", 6.7525, -16.716, 8.6, -1.46, BLUEWHITE, 0),
            new Star("Epsilon Eridani", 3.549, -9.458, 10.5, 3.73, ORANGE, 0),
            new Star("Procyon", 7.655, 5.225, 11.46, 0.34, WHITE, 0),
            new Star("Tau Ceti", 1.734, -15.937, 11.9, 3.50, YELLOW, 0),
            new Star("Altair", 19.846, 8.868, 16.7, 0.76, WHITE, 0),
            new Star("Vega", 18.616, 38.784, 25.0, 0.03, BLUEWHITE, 0),
            new Star("Fomalhaut", 22.961, -29.622, 25.1, 1.16, WHITE, 0),
            new Star("Pollux", 7.755, 28.026, 33.8, 1.14, ORANGE, 0),
            new Star("Arcturus", 14.261, 19.182, 36.7, -0.05, ORANGE, 0),
            new Star("Capella", 5.278, 45.998, 42.9, 0.08, YELLOW, 0),
            new Star("Castor", 7.577, 31.888, 51, 1.58, WHITE, 0),
            new Star("Aldebaran", 4.599, 16.509, 65, 0.86, ORANGE, 0),
            new Star("Regulus", 10.139, 11.967, 79, 1.36, BLUEWHITE, 0),
            new Star("Alioth", 12.900, 55.960, 83, 1.76, WHITE, 0),
            new Star("Mizar", 13.399, 54.925, 83, 2.04, WHITE, 0),
            new Star("Gacrux", 12.519, -57.113, 88, 1.64, RED, 0),
            new Star("Algol", 3.136, 40.956, 90, 2.12, BLUEWHITE, 0),
            new Star("Alkaid", 13.792, 49.313, 101, 1.86, BLUEWHITE, 0),
            new Star("Dubhe", 11.062, 61.751, 123, 1.79, ORANGE, 0),
            new Star("Achernar", 1.629, -57.237, 139, 0.46, BLUEWHITE, 0),
            new Star("Hyades", 4.450, 15.87, 153, 0.5, ORANGE, 1),
            new Star("Eltanin", 17.943, 51.489, 154, 2.23, ORANGE, 0),
            new Star("Alphard", 9.460, -8.659, 177, 1.98, ORANGE, 0),
            new Star("Schedar", 0.675, 56.537, 228, 2.24, ORANGE, 0),
            new Star("Spica", 13.420, -11.161, 250, 0.97, BLUEWHITE, 0),
            new Star("Mimosa", 12.795, -59.689, 280, 1.25, BLUEWHITE, 0),
            new Star("Canopus", 6.399, -52.696, 310, -0.74, WHITE, 0),
            new Star("Acrux", 12.443, -63.099, 320, 0.76, BLUEWHITE, 0),
            new Star("Hadar", 14.064, -60.373, 390, 0.61, BLUEWHITE, 0),
            new Star("Albireo", 19.512, 27.960, 430, 3.05, YELLOW, 0),
            new Star("Adhara", 6.977, -28.972, 430, 1.50, BLUEWHITE, 0),
            new Star("Polaris", 2.530, 89.264, 433, 1.98, YELLOW, 0),
            new Star("Pleiades", 3.790, 24.117, 444, 1.6, BLUEWHITE, 1),
            new Star("Mirzam", 6.378, -17.956, 500, 1.98, BLUEWHITE, 0),
            new Star("Betelgeuse", 5.919, 7.407, 550, 0.5, RED, 0),
            new Star("Antares", 16.490, -26.432, 550, 1.06, RED, 0),
            new Star("Shaula", 17.560, -37.104, 570, 1.62, BLUEWHITE, 0),
            new Star("Mirfak", 3.405, 49.861, 590, 1.79, YELLOW, 0),
            new Star("Gamma Cassiopeiae", 0.945, 60.717, 610, 2.47, BLUEWHITE, 0),
            new Star("Saiph", 5.796, -9.670, 650, 2.06, BLUEWHITE, 0),
            new Star("Rigel", 5.242, -8.202, 860, 0.13, BLUEWHITE, 0),
            new Star("Naos", 8.060, -40.003, 1080, 2.25, BLUEWHITE, 0),
            new Star("Mintaka", 5.533, -0.299, 1200, 2.23, BLUEWHITE, 0),
            new Star("Alnitak", 5.679, -1.943, 1260, 1.77, BLUEWHITE, 0),
            new Star("Orion Nebula", 5.588, -5.391, 1344, 4.0, PINK, 2),
            new Star("Sadr", 20.370, 40.257, 1800, 2.23, YELLOW, 0),
            new Star("Wezen", 7.140, -26.393, 1800, 1.83, YELLOW, 0),
            new Star("Alnilam", 5.604, -1.202, 1980, 1.69, BLUEWHITE, 0)
    };

    /** Indexes of STARS, brightest first (used to decide whose label wins when labels collide). */
    public static final int[] ORDER = new int[STARS.length];
    static {
        Integer[] idx = new Integer[STARS.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return Double.compare(STARS[a].mag, STARS[b].mag); }
        });
        for (int i = 0; i < idx.length; i++) ORDER[i] = idx[i];
    }

    public static final class Level {
        public final String emoji, name, size, desc;
        public final double meters;
        Level(String emoji, String name, String size, double meters, String desc) {
            this.emoji = emoji; this.name = name; this.size = size; this.meters = meters; this.desc = desc;
        }
    }

    private static final double AU = 1.495978707e11, LY = 9.4607e15;

    public static final Level[] LADDER = {
            new Level("\uD83C\uDF0D", "Earth", "12,742 km wide", 1.2742e7,
                    "Your home. Everything you have ever known is on this one small ball."),
            new Level("\uD83C\uDF19", "Earth\u2013Moon system", "the Moon orbits at 384,400 km", 7.688e8,
                    "Light takes about 1.3 seconds to reach the Moon."),
            new Level("\u2600\uFE0F", "Solar System (planets)", "Neptune's orbit is about 60 AU across", 2 * 30.07 * AU,
                    "The Sun and its 8 planets. Sunlight takes about 8 minutes 20 seconds to reach Earth."),
            new Level("\uD83D\uDEF0\uFE0F", "Heliosphere", "about 240 AU across", 2 * 120 * AU,
                    "The Sun's bubble of solar wind. Voyager 1 crossed its edge at about 121 AU (2012), Voyager 2 at about 119 AU (2018)."),
            new Level("\u2604\uFE0F", "Oort Cloud (never seen directly)", "up to about 100,000 AU from the Sun (1.6 light-years)", 2 * 1.0e5 * AU,
                    "A huge shell of icy bodies that is thought to be where long-period comets come from."),
            new Level("\uD83C\uDF2B\uFE0F", "Local Interstellar Cloud", "about 30 light-years across", 30 * LY,
                    "A thin wisp of gas the Sun is passing through right now."),
            new Level("\uD83E\uDEE7", "Local Bubble", "about 1,000 light-years across", 1000 * LY,
                    "A giant cavity of hot, thin gas blown out by supernovae. The Sun sits inside it."),
            new Level("\uD83C\uDF00", "Orion Spur (Local Arm)", "about 3,500 light-years wide, 10,000 long", 3500 * LY,
                    "A small arm piece between the Sagittarius and Perseus arms of the Milky Way. This is Earth's neighbourhood."),
            new Level("\uD83C\uDF0C", "Milky Way galaxy", "about 100,000 light-years across", 1.0e5 * LY,
                    "Hundreds of billions of stars. The Sun is about 26,700 light-years from the centre."),
            new Level("\uD83D\uDD2D", "Local Group", "about 10 million light-years across", 1.0e7 * LY,
                    "The Milky Way, Andromeda (about 2.5 million light-years away), Triangulum and dozens of smaller galaxies."),
            new Level("\uD83C\uDF20", "Virgo Supercluster", "about 110 million light-years across", 1.1e8 * LY,
                    "A huge collection of galaxy groups. Our Local Group sits on its edge."),
            new Level("\uD83D\uDD78\uFE0F", "Laniakea Supercluster", "about 520 million light-years across", 5.2e8 * LY,
                    "Our home supercluster, mapped in 2014 from how galaxies flow. Everything in it drifts toward a gravity centre called the Great Attractor."),
            new Level("\uD83C\uDF10", "Observable Universe", "about 93 billion light-years across", 9.3e10 * LY,
                    "Everything whose light has had time to reach us since the Big Bang about 13.8 billion years ago. Every observer has their own observable universe, with themselves at the centre.")
    };

    public static String lightTime(double meters) {
        double s = meters / 299792458.0;
        if (s < 1) return String.format(Locale.US, "%.0f milliseconds", s * 1000);
        if (s < 120) return String.format(Locale.US, "%.0f seconds", s);
        if (s < 7200) return String.format(Locale.US, "%.0f minutes", s / 60);
        if (s < 172800) return String.format(Locale.US, "%.0f hours", s / 3600);
        double days = s / 86400;
        if (days < 730) return String.format(Locale.US, "%.0f days", days);
        double years = days / 365.25;
        if (years < 1e6) return String.format(Locale.US, "%,.0f years", years);
        if (years < 1e9) return String.format(Locale.US, "%,.1f million years", years / 1e6);
        return String.format(Locale.US, "%,.1f billion years", years / 1e9);
    }
}
