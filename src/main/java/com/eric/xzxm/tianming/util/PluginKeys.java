package com.eric.xzxm.tianming.util;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import org.bukkit.NamespacedKey;

public final class PluginKeys {
    public final NamespacedKey itemKind;
    public final NamespacedKey skillSlot;
    public final NamespacedKey menuAbility;
    public final NamespacedKey menuSlot;
    public final NamespacedKey disguiseForm;

    public PluginKeys(TianmingTrialPlugin plugin) {
        itemKind = new NamespacedKey(plugin, "item_kind");
        skillSlot = new NamespacedKey(plugin, "skill_slot");
        menuAbility = new NamespacedKey(plugin, "menu_ability");
        menuSlot = new NamespacedKey(plugin, "menu_slot");
        disguiseForm = new NamespacedKey(plugin, "disguise_form");
    }
}
