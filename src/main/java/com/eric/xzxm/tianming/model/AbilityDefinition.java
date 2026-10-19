package com.eric.xzxm.tianming.model;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import java.time.Duration;
import java.util.List;

public record AbilityDefinition(String id, int day, String displayName, Material icon, AbilityType type, Duration duration, Duration cooldown, String exclusiveGroup, int range, List<EffectSpec> effects, List<RewardSpec> rewards) {
    public record EffectSpec(PotionEffectType type, int amplifier) {}
    public record RewardSpec(Material material, int amount) {}
}
