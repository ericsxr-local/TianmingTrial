package com.eric.xzxm.tianming.service;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import com.eric.xzxm.tianming.model.AbilityDefinition;
import com.eric.xzxm.tianming.util.Text;
import com.eric.xzxm.tianming.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class HudManager {
    private final BossBar locationBar;
    private final Map<String, BossBar> abilityBars = new HashMap<>();

    public HudManager(TianmingTrialPlugin plugin) {
        locationBar = Bukkit.createBossBar(ChatColor.RED + "⚠ 下次坐标曝光", BarColor.RED, BarStyle.SOLID);
    }

    public void showLocation(long nextAt, long interval) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!locationBar.getPlayers().contains(player)) locationBar.addPlayer(player);
        }
        long remaining = Math.max(0L, nextAt - System.currentTimeMillis());
        locationBar.setTitle(ChatColor.RED + "⚠ 下次坐标曝光：" + ChatColor.WHITE + TimeUtil.format(Duration.ofMillis(remaining)));
        locationBar.setProgress(Math.max(0D, Math.min(1D, remaining / (double) interval)));
        locationBar.setVisible(true);
    }

    public void hideLocation() {
        locationBar.removeAll();
        locationBar.setVisible(false);
    }

    public void showAbility(Player player, AbilityDefinition ability, long until, Duration fullDuration) {
        BossBar bar = abilityBars.computeIfAbsent(ability.id(), ignored -> Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        updateAbility(player, ability, until, fullDuration);
        bar.setVisible(true);
    }

    public void updateAbility(Player player, AbilityDefinition ability, long until, Duration fullDuration) {
        BossBar bar = abilityBars.get(ability.id());
        if (bar == null) return;
        long remaining = Math.max(0L, until - System.currentTimeMillis());
        bar.setTitle(ChatColor.LIGHT_PURPLE + Text.plain(ability.displayName()) + ChatColor.GRAY + " · 剩余 "
                + ChatColor.WHITE + TimeUtil.format(Duration.ofMillis(remaining)));
        bar.setProgress(Math.max(0D, Math.min(1D, remaining / (double) Math.max(1L, fullDuration.toMillis()))));
    }

    public void hideAbility(Player player, String abilityId) {
        BossBar bar = abilityBars.get(abilityId);
        if (bar != null) bar.removePlayer(player);
    }

    public void clearPlayer(Player player) {
        abilityBars.values().forEach(bar -> bar.removePlayer(player));
    }

    public void clearAll() {
        locationBar.removeAll();
        abilityBars.values().forEach(BossBar::removeAll);
        abilityBars.clear();
    }
}
