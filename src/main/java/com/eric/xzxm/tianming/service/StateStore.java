package com.eric.xzxm.tianming.service;

import com.eric.xzxm.tianming.model.ActivityState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public final class StateStore {
    private final File file;

    public StateStore(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "state.yml");
    }

    public ActivityState load() {
        ActivityState state = new ActivityState();
        if (!file.exists()) return state;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        state.active = yaml.getBoolean("active");
        state.testMode = yaml.getBoolean("test-mode");
        state.ascended = yaml.getBoolean("ascended");
        state.holderName = yaml.getString("holder-name");
        state.disguiseForm = yaml.getString("disguise-form", "VILLAGER");
        String holderRaw = yaml.getString("holder-id");
        if (holderRaw != null) {
            try {
                state.holderId = UUID.fromString(holderRaw);
            } catch (IllegalArgumentException ignored) {
                state.active = false;
            }
        }
        state.startedAt = yaml.getLong("started-at");
        state.nextExposureAt = yaml.getLong("next-exposure-at");
        state.currentDay = yaml.getInt("current-day");
        state.dayLengthMillis = yaml.getLong("day-length-ms");
        state.exposureIntervalMillis = yaml.getLong("exposure-interval-ms");
        state.unlocked.addAll(yaml.getStringList("unlocked"));
        state.rewardDaysGranted.addAll(yaml.getIntegerList("reward-days-granted"));
        readStringMap(yaml.getConfigurationSection("loadout"), state.loadout);
        readLongMap(yaml.getConfigurationSection("cooldowns"), state.cooldowns);
        readLongMap(yaml.getConfigurationSection("active"), state.activeUntil);
        readLongMap(yaml.getConfigurationSection("casts"), state.castCooldowns);
        readLongMap(yaml.getConfigurationSection("test-duration"), state.testDurationOverrides);
        readLongMap(yaml.getConfigurationSection("test-cooldown"), state.testCooldownOverrides);
        return state;
    }

    public void save(ActivityState state) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", state.active);
        yaml.set("test-mode", state.testMode);
        yaml.set("ascended", state.ascended);
        yaml.set("holder-id", state.holderId == null ? null : state.holderId.toString());
        yaml.set("holder-name", state.holderName);
        yaml.set("disguise-form", state.disguiseForm);
        yaml.set("started-at", state.startedAt);
        yaml.set("next-exposure-at", state.nextExposureAt);
        yaml.set("current-day", state.currentDay);
        yaml.set("day-length-ms", state.dayLengthMillis);
        yaml.set("exposure-interval-ms", state.exposureIntervalMillis);
        yaml.set("unlocked", new ArrayList<>(state.unlocked));
        yaml.set("reward-days-granted", new ArrayList<>(state.rewardDaysGranted));
        writeMap(yaml, "loadout", state.loadout);
        writeMap(yaml, "cooldowns", state.cooldowns);
        writeMap(yaml, "active", state.activeUntil);
        writeMap(yaml, "casts", state.castCooldowns);
        writeMap(yaml, "test-duration", state.testDurationOverrides);
        writeMap(yaml, "test-cooldown", state.testCooldownOverrides);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save Tianming state", ex);
        }
    }

    private static void readLongMap(ConfigurationSection section, Map<String, Long> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) target.put(key, section.getLong(key));
    }

    private static void readStringMap(ConfigurationSection section, Map<Integer, String> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) target.put(Integer.parseInt(key), section.getString(key));
    }

    private static void writeMap(YamlConfiguration yaml, String path, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) yaml.set(path + "." + entry.getKey(), entry.getValue());
    }
}
