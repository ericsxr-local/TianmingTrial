package com.eric.xzxm.tianming.service;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import com.eric.xzxm.tianming.config.AbilityRegistry;
import com.eric.xzxm.tianming.model.AbilityDefinition;
import com.eric.xzxm.tianming.model.AbilityType;
import com.eric.xzxm.tianming.model.ActivityState;
import com.eric.xzxm.tianming.util.Text;
import com.eric.xzxm.tianming.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class ActivityManager {
    private static final double LASER_RANGE = 45D;
    private static final Particle.DustOptions LASER_DUST = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0F);

    private final TianmingTrialPlugin plugin;
    private final AbilityRegistry abilities;
    private final StateStore store;
    private final HudManager hud;
    private final SkillItemService items;
    private final DisguiseService disguises;
    private ActivityState state;
    private long lastSave;

    public ActivityManager(TianmingTrialPlugin plugin, AbilityRegistry abilities, StateStore store, HudManager hud,
                           SkillItemService items, DisguiseService disguises) {
        this.plugin = plugin;
        this.abilities = abilities;
        this.store = store;
        this.hud = hud;
        this.items = items;
        this.disguises = disguises;
        this.state = store.load();
    }

    public ActivityState state() {
        return state;
    }

    public boolean isActive() {
        return state.active;
    }

    public boolean isHolder(Player player) {
        return state.active && state.holderId != null && player.getUniqueId().equals(state.holderId);
    }

    public Player holder() {
        return state.holderId == null ? null : Bukkit.getPlayer(state.holderId);
    }

    public boolean start(Player selected, boolean testMode) {
        if (state.active) return false;
        state.reset();
        long now = System.currentTimeMillis();
        state.active = true;
        state.testMode = testMode;
        state.holderId = selected.getUniqueId();
        state.holderName = selected.getName();
        state.startedAt = now;
        state.currentDay = 0;
        state.dayLengthMillis = TimeUtil.parse(plugin.getConfig().getString(testMode ? "test.day-length" : "activity.day-length", "24h")).toMillis();
        state.exposureIntervalMillis = TimeUtil.parse(plugin.getConfig().getString(
                testMode ? "test.location-exposure-interval" : "activity.location-exposure-interval", "30m")).toMillis();
        state.nextExposureAt = now + state.exposureIntervalMillis;
        if (testMode) {
            long defaultCooldown = TimeUtil.parse(plugin.getConfig().getString("test.default-cooldown", "1m")).toMillis();
            for (AbilityDefinition ability : abilities.all()) state.testCooldownOverrides.put(ability.id(), defaultCooldown);
        }
        items.issueToolkit(selected, state);
        hud.showLocation(state.nextExposureAt, state.exposureIntervalMillis);
        announceStart(selected);
        saveNow();
        return true;
    }

    public void end(String reason) {
        if (!state.active) return;
        Player old = holder();
        if (old != null) cleanupHolder(old, false);
        hud.clearAll();
        state.reset();
        saveNow();
        for (Player player : Bukkit.getOnlinePlayers()) {
            title(player, "§7§l天 命 试 炼 结 束", reason == null ? "§7活动已结束" : reason);
        }
    }

    public boolean resetTest() {
        if (!state.active || !state.testMode) return false;
        Player selected = holder();
        if (selected == null) return false;
        cleanupHolder(selected, false);
        hud.clearAll();
        state.reset();
        return start(selected, true);
    }

    public boolean transfer(Player killer) {
        if (!state.active || killer == null || killer.getUniqueId().equals(state.holderId)) return false;
        long now = System.currentTimeMillis();
        Player old = holder();
        if (old != null) cleanupHolder(old, true);

        state.holderId = killer.getUniqueId();
        state.holderName = killer.getName();
        items.issueToolkit(killer, state);
        grantPendingRewards(killer);
        if (state.ascended) enableAscendedFlight(killer);

        for (String id : new ArrayList<>(state.activeUntil.keySet())) {
            AbilityDefinition ability = abilities.get(id);
            long until = state.activeUntil.getOrDefault(id, 0L);
            if (ability == null || until <= now) {
                state.activeUntil.remove(id);
                continue;
            }
            applyAbility(killer, ability, Duration.ofMillis(until - now));
            hud.showAbility(killer, ability, until, durationFor(ability));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            title(player, "§6§l✦ 命 格 易 主 ✦", "§e" + killer.getName() + " §7已继承第 §b" + state.currentDay + " §7日神格");
        }
        Bukkit.broadcastMessage("§5§l天命 §8» §e" + killer.getName() + " §7已继承全部神格、冷却与正在持续的能力。");
        saveNow();
        return true;
    }

    public void tick() {
        if (!state.active) return;
        long now = System.currentTimeMillis();
        if (state.dayLengthMillis <= 0L || state.exposureIntervalMillis <= 0L) {
            end("§c活动计时配置无效，已安全结束");
            return;
        }

        int totalDays = plugin.getConfig().getInt("activity.total-days", 12);
        int completed = (int) Math.min(totalDays, Math.max(0L, (now - state.startedAt) / state.dayLengthMillis));
        while (state.currentDay < completed) unlockDay(++state.currentDay);
        if (completed >= totalDays && !state.ascended) ascend();

        if (!state.ascended) {
            while (now >= state.nextExposureAt) {
                expose();
                state.nextExposureAt += state.exposureIntervalMillis;
            }
            hud.showLocation(state.nextExposureAt, state.exposureIntervalMillis);
        } else {
            hud.hideLocation();
        }

        Player currentHolder = holder();
        for (String id : new ArrayList<>(state.activeUntil.keySet())) {
            long until = state.activeUntil.getOrDefault(id, 0L);
            AbilityDefinition ability = abilities.get(id);
            if (ability == null || until <= now) {
                state.activeUntil.remove(id);
                state.castCooldowns.remove(id);
                if (currentHolder != null && ability != null) stopAbility(currentHolder, ability);
            } else if (currentHolder != null) {
                hud.updateAbility(currentHolder, ability, until, durationFor(ability));
            }
        }
        if (currentHolder != null) {
            if (state.ascended) enableAscendedFlight(currentHolder);
            disguises.syncHealth(currentHolder);
            items.refresh(currentHolder, state);
        }
        if (now - lastSave >= 15_000L) {
            saveNow();
            lastSave = now;
        }
    }

    private void unlockDay(int day) {
        Player currentHolder = holder();
        for (AbilityDefinition ability : abilities.all()) {
            if (ability.day() != day) continue;
            state.unlocked.add(ability.id());
            if (currentHolder != null && !state.rewardDaysGranted.contains(day)) giveRewards(currentHolder, ability);
            if (currentHolder != null) items.refresh(currentHolder, state);

            for (Player player : Bukkit.getOnlinePlayers()) {
                title(player, "§d§l✦ 天 命 进 化 ✦",
                        "§e" + state.holderName + " §7获得「§b" + Text.plain(ability.displayName()) + "§7」");
            }
            Bukkit.broadcastMessage("§5§l天命 §8» §e" + state.holderName + " §7获得 §b" + Text.plain(ability.displayName()) + "§7。");
        }
        if (currentHolder != null) state.rewardDaysGranted.add(day);
        saveNow();
    }

    private void ascend() {
        state.ascended = true;
        state.cooldowns.clear();
        state.castCooldowns.clear();
        Player currentHolder = holder();
        if (currentHolder != null) {
            enableAscendedFlight(currentHolder);
            for (AbilityDefinition ability : abilities.all()) currentHolder.setCooldown(ability.icon(), 0);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            title(player, "§5§l✦ 十 二 日 登 神 ✦", "§e" + state.holderName + " §f已获永恒飞行与无冷却神格");
        }
        Bukkit.broadcastMessage("§5§l天命 §8» §e" + state.holderName + " §d已完成十二日试炼：永久飞行，所有技能无冷却。");
        saveNow();
    }

    private void giveRewards(Player player, AbilityDefinition ability) {
        for (AbilityDefinition.RewardSpec reward : ability.rewards()) {
            Map<Integer, ItemStack> excess = player.getInventory().addItem(new ItemStack(reward.material(), reward.amount()));
            excess.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack).setOwner(player.getUniqueId()));
        }
    }

    private void grantPendingRewards(Player player) {
        for (int day = 1; day <= state.currentDay; day++) {
            if (state.rewardDaysGranted.contains(day)) continue;
            for (AbilityDefinition ability : abilities.all()) {
                if (ability.day() == day) giveRewards(player, ability);
            }
            state.rewardDaysGranted.add(day);
        }
        saveNow();
    }

    private void expose() {
        Player currentHolder = holder();
        if (currentHolder == null) {
            Bukkit.broadcastMessage("§c⚠ 天机泄露：§7天命者 §e" + state.holderName + " §7当前离线。");
            return;
        }
        Location location = currentHolder.getLocation();
        String coordinates = "§f世界:§b" + location.getWorld().getName() + " §fX:§e" + location.getBlockX() + " §fY:§e" + location.getBlockY() + " §fZ:§e" + location.getBlockZ();
        String text = "§e" + currentHolder.getName() + " §7| " + coordinates;
        for (Player player : Bukkit.getOnlinePlayers()) title(player, "§c§l⚠ 天 机 泄 露 ⚠", text);
        Bukkit.broadcastMessage("§c§l天命 §8» " + text);
        currentHolder.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6F, 1.5F);
    }

    public boolean activate(Player player, String id) {
        AbilityDefinition ability = abilities.get(id);
        if (!isHolder(player) || ability == null || !state.unlocked.contains(id)) return false;
        long now = System.currentTimeMillis();
        removeExpiredAbilities(now);

        if (!state.ascended) {
            long readyAt = state.cooldowns.getOrDefault(id, 0L);
            if (readyAt > now) {
                player.sendActionBar("§c" + Text.plain(ability.displayName()) + " §7恢复 " + TimeUtil.formatUntil(readyAt));
                return false;
            }
        }
        if (state.activeUntil.size() >= plugin.getConfig().getInt("activity.max-concurrent-abilities", 2)
                && !state.activeUntil.containsKey(id)) {
            player.sendMessage("§c当前同时生效能力已达上限：" + plugin.getConfig().getInt("activity.max-concurrent-abilities", 3));
            return false;
        }
        if (!ability.exclusiveGroup().isBlank() && hasActiveGroup(ability.exclusiveGroup())
                && !state.activeUntil.containsKey(id)) {
            player.sendMessage("§c该能力与当前生效能力冲突。");
            return false;
        }

        Duration duration = durationFor(ability);
        long until = now + duration.toMillis();
        state.activeUntil.put(id, until);
        if (state.ascended) {
            state.cooldowns.remove(id);
            player.setCooldown(ability.icon(), 0);
        } else {
            Duration cooldown = cooldownFor(ability);
            state.cooldowns.put(id, now + cooldown.toMillis());
            items.applyVanillaCooldown(player, ability, cooldown);
        }
        applyAbility(player, ability, duration);
        title(player, "§d§l✦ " + Text.plain(ability.displayName()) + " ✦",
                state.ascended ? "§d登神状态 · 无冷却" : "§7持续时间：§f" + TimeUtil.format(duration));
        hud.showAbility(player, ability, until, duration);
        saveNow();
        return true;
    }

    public boolean activateDisguise(Player player, String selectedForm) {
        if (!isHolder(player) || !state.unlocked.contains("disguise")) return false;
        String form = selectedForm == null ? "VILLAGER" : selectedForm.toUpperCase(java.util.Locale.ROOT);
        var configuredForms = plugin.getConfig().getStringList("disguise.allowed-forms");
        boolean allowed = configuredForms.isEmpty() || configuredForms.stream()
                .anyMatch(value -> value.equalsIgnoreCase(form));
        if (!allowed) {
            player.sendMessage("§c该伪装形态不在允许列表中。");
            return false;
        }
        state.disguiseForm = form;
        saveNow();
        return activate(player, "disguise");
    }
    private void applyAbility(Player player, AbilityDefinition ability, Duration duration) {
        int ticks = (int) Math.min(Integer.MAX_VALUE, duration.toSeconds() * 20L + 20L);
        for (AbilityDefinition.EffectSpec effect : ability.effects()) {
            player.addPotionEffect(new PotionEffect(effect.type(), ticks, effect.amplifier(), true, true, true));
        }
        switch (ability.type()) {
            case FLIGHT, CREATOR_STANCE -> {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
            case GLOW_NEARBY -> {
                double maxDistanceSquared = ability.range() * ability.range();
                for (Player other : player.getWorld().getPlayers()) {
                    if (!other.equals(player) && other.getLocation().distanceSquared(player.getLocation()) <= maxDistanceSquared) {
                        other.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, ticks, 0, true, true, true));
                    }
                }
            }
            case DISGUISE -> disguises.start(player, state.disguiseForm);
            default -> {
            }
        }
    }

    private void stopAbility(Player player, AbilityDefinition ability) {
        removeAbilityEffects(player, ability);
        hud.hideAbility(player, ability.id());
        String cooldownText = state.ascended ? "§d登神状态 · 无冷却"
                : "§7" + Text.plain(ability.displayName()) + " 已结束 · 恢复倒计时 "
                + TimeUtil.formatUntil(state.cooldowns.getOrDefault(ability.id(), System.currentTimeMillis()));
        player.sendActionBar(cooldownText);
    }

    private void removeAbilityEffects(Player player, AbilityDefinition ability) {
        for (AbilityDefinition.EffectSpec effect : ability.effects()) player.removePotionEffect(effect.type());
        if (ability.type() == AbilityType.DISGUISE) disguises.stop(player);
        if (ability.type() == AbilityType.FLIGHT || ability.type() == AbilityType.CREATOR_STANCE) refreshFlightPermission(player);
    }

    private void refreshFlightPermission(Player player) {
        if (state.ascended && isHolder(player)) {
            enableAscendedFlight(player);
            return;
        }
        boolean flightStillActive = state.activeUntil.keySet().stream()
                .map(abilities::get)
                .filter(Objects::nonNull)
                .anyMatch(value -> value.type() == AbilityType.FLIGHT || value.type() == AbilityType.CREATOR_STANCE);
        if (flightStillActive) {
            player.setAllowFlight(true);
            return;
        }
        if (player.getGameMode() == GameMode.SURVIVAL) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private void enableAscendedFlight(Player player) {
        if (isHolder(player) && player.getGameMode() == GameMode.SURVIVAL) player.setAllowFlight(true);
    }

    public boolean castActive(Player player, String id) {
        if (!isHolder(player) || !state.activeUntil.containsKey(id)) return false;
        AbilityDefinition ability = abilities.get(id);
        if (ability == null) return false;
        long now = System.currentTimeMillis();
        if (!state.ascended && state.castCooldowns.getOrDefault(id, 0L) > now) return true;

        switch (ability.type()) {
            case SHOCKWAVE -> {
                for (Entity entity : player.getNearbyEntities(7, 4, 7)) {
                    if (entity instanceof LivingEntity && entity != player) {
                        Vector offset = entity.getLocation().toVector().subtract(player.getLocation().toVector());
                        if (offset.lengthSquared() > 0.01D) entity.setVelocity(offset.normalize().multiply(1.5D).setY(0.55D));
                    }
                }
                player.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, player.getLocation().add(0, 1, 0), 20, 2, 0.5, 2, 0.05);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.7F);
                if (!state.ascended) state.castCooldowns.put(id, now + 1500L);
            }
            case LASER -> {
                Location eye = player.getEyeLocation();
                Vector direction = eye.getDirection().normalize();
                RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eye, direction, LASER_RANGE);
                double allowedDistance = blockHit == null ? LASER_RANGE : blockHit.getHitPosition().distance(eye.toVector());
                RayTraceResult entityHit = player.getWorld().rayTraceEntities(eye, direction, allowedDistance, 0.25D,
                        entity -> entity instanceof LivingEntity && entity != player && !disguises.isProxy(entity));
                double distance = entityHit == null ? allowedDistance : entityHit.getHitPosition().distance(eye.toVector());

                for (double step = 0D; step <= distance; step += 0.45D) {
                    Location point = eye.clone().add(direction.clone().multiply(step));
                    player.getWorld().spawnParticle(Particle.REDSTONE, point, 3, 0.025D, 0.025D, 0.025D, 0D, LASER_DUST, true);
                }
                if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity target) target.damage(1.2D, player);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1F, 1.7F);
                if (!state.ascended) state.castCooldowns.put(id, now + 1000L);
            }
            case POTION -> {
                if ("fire".equals(id)) {
                    player.launchProjectile(SmallFireball.class);
                    if (!state.ascended) state.castCooldowns.put(id, now + 1000L);
                }
            }
            default -> {
            }
        }
        return true;
    }

    public boolean hasCreation(Player player) {
        return isHolder(player) && state.activeUntil.containsKey("creation");
    }

    private void removeExpiredAbilities(long now) {
        Player currentHolder = holder();
        for (String id : new ArrayList<>(state.activeUntil.keySet())) {
            if (state.activeUntil.getOrDefault(id, 0L) <= now) {
                state.activeUntil.remove(id);
                state.castCooldowns.remove(id);
                AbilityDefinition ability = abilities.get(id);
                if (currentHolder != null && ability != null) stopAbility(currentHolder, ability);
            }
        }
    }

    private boolean hasActiveGroup(String group) {
        return state.activeUntil.keySet().stream()
                .map(abilities::get)
                .filter(Objects::nonNull)
                .anyMatch(ability -> group.equals(ability.exclusiveGroup()));
    }

    private Duration durationFor(AbilityDefinition ability) {
        return Duration.ofMillis(state.testDurationOverrides.getOrDefault(ability.id(), ability.duration().toMillis()));
    }

    private Duration cooldownFor(AbilityDefinition ability) {
        return Duration.ofMillis(state.testCooldownOverrides.getOrDefault(ability.id(), ability.cooldown().toMillis()));
    }

    public void setTestDay(int day) {
        if (!state.active || !state.testMode) return;
        int total = plugin.getConfig().getInt("activity.total-days", 12);
        int target = Math.max(0, Math.min(total, day));
        Player currentHolder = holder();

        if (target < total && state.ascended) {
            state.ascended = false;
            if (currentHolder != null && currentHolder.getGameMode() == GameMode.SURVIVAL) {
                currentHolder.setFlying(false);
                currentHolder.setAllowFlight(false);
            }
        }
        if (target < state.currentDay) {
            for (String id : new ArrayList<>(state.activeUntil.keySet())) {
                AbilityDefinition ability = abilities.get(id);
                if (ability != null && ability.day() > target && currentHolder != null) stopAbility(currentHolder, ability);
                if (ability != null && ability.day() > target) state.activeUntil.remove(id);
            }
            state.unlocked.removeIf(id -> {
                AbilityDefinition ability = abilities.get(id);
                return ability != null && ability.day() > target;
            });
            state.loadout.entrySet().removeIf(entry -> !state.unlocked.contains(entry.getValue()));
            if (currentHolder != null) items.refresh(currentHolder, state);
            state.currentDay = target;
        } else {
            while (state.currentDay < target) unlockDay(++state.currentDay);
        }
        state.startedAt = System.currentTimeMillis() - target * state.dayLengthMillis;
        if (target >= total && !state.ascended) ascend();
        saveNow();
    }

    public void testTiming(String kind, String abilityId, Duration value) {
        if (!state.active || !state.testMode) return;
        if ("duration".equals(kind)) state.testDurationOverrides.put(abilityId, value.toMillis());
        else state.testCooldownOverrides.put(abilityId, value.toMillis());
        saveNow();
    }

    public void testDayLength(Duration value) {
        if (!state.active || !state.testMode) return;
        state.dayLengthMillis = value.toMillis();
        state.startedAt = System.currentTimeMillis() - state.currentDay * state.dayLengthMillis;
        saveNow();
    }

    public void testExposure(Duration value) {
        if (!state.active || !state.testMode) return;
        state.exposureIntervalMillis = value.toMillis();
        state.nextExposureAt = System.currentTimeMillis() + value.toMillis();
        saveNow();
    }

    public void resumeOnlineHolder() {
        if (!state.active) return;
        if (state.ascended) hud.hideLocation();
        else hud.showLocation(state.nextExposureAt, state.exposureIntervalMillis);
        Player currentHolder = holder();
        if (currentHolder != null) onJoin(currentHolder);
    }

    public void onJoin(Player player) {
        if (!state.active) return;
        if (state.ascended) hud.hideLocation();
        else hud.showLocation(state.nextExposureAt, state.exposureIntervalMillis);
        if (!isHolder(player)) return;
        items.issueToolkit(player, state);
        grantPendingRewards(player);
        if (state.ascended) enableAscendedFlight(player);
        restoreActiveAbilities(player);
    }

    public void onRespawn(Player player) {
        if (!isHolder(player)) return;
        items.issueToolkit(player, state);
        grantPendingRewards(player);
        if (state.ascended) enableAscendedFlight(player);
        restoreActiveAbilities(player);
    }

    private void restoreActiveAbilities(Player player) {
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(state.activeUntil.keySet())) {
            AbilityDefinition ability = abilities.get(id);
            long until = state.activeUntil.getOrDefault(id, 0L);
            if (ability != null && until > now) {
                applyAbility(player, ability, Duration.ofMillis(until - now));
                hud.showAbility(player, ability, until, durationFor(ability));
            }
        }
    }

    public void onQuit(Player player) {
        if (isHolder(player)) {
            disguises.onHolderQuit(player);
            hud.clearPlayer(player);
        }
    }

    private void cleanupHolder(Player player, boolean preserveActive) {
        for (String id : new ArrayList<>(state.activeUntil.keySet())) {
            AbilityDefinition ability = abilities.get(id);
            if (ability != null) {
                removeAbilityEffects(player, ability);
                hud.hideAbility(player, ability.id());
            }
        }
        if (!preserveActive) {
            state.activeUntil.clear();
            state.castCooldowns.clear();
        }
        items.removeManaged(player);
        hud.clearPlayer(player);
        if (player.getGameMode() == GameMode.SURVIVAL) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private void announceStart(Player selected) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(selected)) {
                title(player, "§6§l你已被选中为天命者", "§e12天后成为神");
            } else {
                title(player, "§c§l天命者:" + selected.getName(), "");
            }
        }
        long titleLength = plugin.getConfig().getInt("hud.title-fade-in-ticks", 10)
                + plugin.getConfig().getInt("hud.title-stay-ticks", 50)
                + plugin.getConfig().getInt("hud.title-fade-out-ticks", 10);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!state.active || !selected.getUniqueId().equals(state.holderId)) return;
            for (Player player : Bukkit.getOnlinePlayers()) title(player, "§c§l击杀天命者可替代成神", "");
        }, Math.max(1L, titleLength));
        Bukkit.broadcastMessage("§5§l天命 §8» §7击杀天命者即可继承试炼进度。");
    }

    private void title(Player player, String title, String subtitle) {
        player.sendTitle(title, subtitle,
                plugin.getConfig().getInt("hud.title-fade-in-ticks", 10),
                plugin.getConfig().getInt("hud.title-stay-ticks", 50),
                plugin.getConfig().getInt("hud.title-fade-out-ticks", 10));
    }

    public void saveNow() {
        store.save(state);
    }

    public void shutdown() {
        saveNow();
        disguises.clearAll();
        hud.clearAll();
    }

    public String status() {
        if (!state.active) return "§7当前没有进行中的天命试炼。";
        String rank = state.ascended ? " §d| §l已登神" : "";
        return "§d天命者：§e" + state.holderName + " §7| 第 §b" + state.currentDay + " §7日" + rank
                + " §7| 下次坐标曝光：§f" + TimeUtil.formatUntil(state.nextExposureAt);
    }
}
