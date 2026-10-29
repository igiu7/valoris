package com.valoris.moderation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.util.Errors;

/** Durations like "30s", "10m", "2h", "7d", "1w" or combined "1d12h". */
public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");
    private static final long MAX = 3650L * 24 * 3600 * 1000; // ten years

    private Durations() {}

    public static long parse(String raw) throws CommandSyntaxException {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) break;
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                throw Errors.of("That duration is too long.");
            }
            long unit = switch (m.group(2)) {
                case "s" -> 1000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
            if (n > MAX / unit) throw Errors.of("That duration is too long.");
            total += n * unit;
            end = m.end();
        }
        if (end != s.length() || total <= 0) {
            throw Errors.of("Invalid duration \"" + raw + "\". Use e.g. 30m, 2h, 7d, 1w or 1d12h.");
        }
        return Math.min(total, MAX);
    }

    /** "2d 3h", "45m", "12s". */
    public static String format(long millis) {
        long s = Math.max(0, millis / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 && d == 0) sb.append(m).append("m ");
        if (sb.isEmpty()) sb.append(sec).append("s");
        return sb.toString().trim();
    }

    public static String date(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(epochMillis));
    }
}
