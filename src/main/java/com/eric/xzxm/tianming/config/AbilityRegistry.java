package com.eric.xzxm.tianming.config;

import com.eric.xzxm.tianming.model.AbilityDefinition;
import com.eric.xzxm.tianming.model.AbilityType;
import com.eric.xzxm.tianming.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.*;

public final class AbilityRegistry {
    private final JavaPlugin plugin;
    private final Map<String, AbilityDefinition> values = new LinkedHashMap<>();

    public AbilityRegistry(JavaPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        values.clear();
        File file = new File(plugin.getDataFolder(), "abilities.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("abilities");
        if (root == null) throw new IllegalStateException("abilities.yml has no abilities section");
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = Objects.requireNonNull(root.getConfigurationSection(id));
            Material icon = Material.matchMaterial(Objects.requireNonNull(s.getString("icon")));
            if (icon == null) throw new IllegalArgumentException("Invalid icon for " + id);
            List<AbilityDefinition.EffectSpec> effects = new ArrayList<>();
            for (String raw : s.getStringList("effects")) {
                String[] parts = raw.split(":");
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                if (type == null) throw new IllegalArgumentException("Invalid effect " + raw + " in " + id);
                effects.add(new AbilityDefinition.EffectSpec(type, parts.length > 1 ? Integer.parseInt(parts[1]) : 0));
            }
            List<AbilityDefinition.RewardSpec> rewards = new ArrayList<>();
            for (String raw : s.getStringList("rewards")) {
                String[] parts = raw.split(":");
                Material material = Material.matchMaterial(parts[0]);
                if (material == null) throw new IllegalArgumentException("Invalid reward " + raw + " in " + id);
                rewards.add(new AbilityDefinition.RewardSpec(material, Integer.parseInt(parts[1])));
            }
            values.put(id, new AbilityDefinition(id, s.getInt("day"), s.getString("name", id), icon,
                    AbilityType.valueOf(s.getString("type", "POTION").toUpperCase(Locale.ROOT)),
                    TimeUtil.parse(s.getString("duration", "1m")), TimeUtil.parse(s.getString("cooldown", "1h")),
                    s.getString("group", ""), s.getInt("range", 32), List.copyOf(effects), List.copyOf(rewards)));
        }
    }

    public AbilityDefinition get(String id) { return values.get(id); }
    public Collection<AbilityDefinition> all() { return Collections.unmodifiableCollection(values.values()); }
    public List<AbilityDefinition> throughDay(int day) { return values.values().stream().filter(a -> a.day() <= day).sorted(Comparator.comparingInt(AbilityDefinition::day)).toList(); }
}
