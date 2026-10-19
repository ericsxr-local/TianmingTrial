package com.eric.xzxm.tianming.util;

public final class Text {
    private Text() {}
    public static String plain(String value) { return value == null ? "" : value.replaceAll("<[^>]*>", ""); }
    public static String color(String value) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', value); }
}
