package com.eric.xzxm.tianming.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private static final Pattern PART = Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);
    private TimeUtil() {}
    public static Duration parse(String source) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Time cannot be blank");
        String compact = source.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = PART.matcher(compact);
        long seconds = 0; int consumed = 0;
        while (matcher.find()) {
            consumed += matcher.group().length();
            long value = Long.parseLong(matcher.group(1));
            seconds += switch (matcher.group(2)) { case "d" -> value * 86400L; case "h" -> value * 3600L; case "m" -> value * 60L; default -> value; };
        }
        if (consumed != compact.length() || seconds <= 0) throw new IllegalArgumentException("Use 45s, 2m30s, 1h, or 24h.");
        return Duration.ofSeconds(seconds);
    }
    public static String format(Duration value) {
        long seconds = Math.max(0, value.getSeconds()), hours = seconds / 3600, minutes = (seconds % 3600) / 60, secs = seconds % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, secs) : String.format("%02d:%02d", minutes, secs);
    }
    public static String formatUntil(long epochMillis) { return format(Duration.ofMillis(Math.max(0, epochMillis - System.currentTimeMillis()))); }
}
