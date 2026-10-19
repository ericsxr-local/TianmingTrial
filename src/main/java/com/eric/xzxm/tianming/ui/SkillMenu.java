package com.eric.xzxm.tianming.ui;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import com.eric.xzxm.tianming.model.AbilityDefinition;
import com.eric.xzxm.tianming.model.ActivityState;
import com.eric.xzxm.tianming.service.ActivityManager;
import com.eric.xzxm.tianming.service.SkillItemService;
import com.eric.xzxm.tianming.util.PluginKeys;
import com.eric.xzxm.tianming.util.Text;
import com.eric.xzxm.tianming.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkillMenu {
    private final TianmingTrialPlugin plugin;
    private final ActivityManager activity;
    private final SkillItemService items;
    private final PluginKeys keys;
    private final Map<UUID, Integer> editing = new HashMap<>();

    public SkillMenu(TianmingTrialPlugin plugin, ActivityManager activity, SkillItemService items, PluginKeys keys) {
        this.plugin = plugin;
        this.activity = activity;
        this.items = items;
        this.keys = keys;
    }

    public void open(Player player) {
        if (!activity.isHolder(player)) {
            player.sendMessage("§c只有天命者可以使用天命之书。");
            return;
        }
        ActivityState state = activity.state();
        SkillMenuHolder holder = new SkillMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, "§6§l天命之书 · 技能配置");
        holder.setInventory(inventory);
        for (int slot = 0; slot < 3; slot++) inventory.setItem(slot, loadoutButton(slot, state.loadout.get(slot)));

        int position = 9;
        for (AbilityDefinition ability : plugin.abilities().all()) {
            if (position >= inventory.getSize()) break;
            inventory.setItem(position++, abilityItem(ability, state));
        }
        player.openInventory(inventory);
    }

    public boolean isMenu(Inventory inventory) {
        return inventory.getHolder() instanceof SkillMenuHolder;
    }

    public void click(Player player, Inventory inventory, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) return;
        ItemStack item = inventory.getItem(rawSlot);
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();

        Integer shortcutSlot = meta.getPersistentDataContainer().get(keys.menuSlot, PersistentDataType.INTEGER);
        if (shortcutSlot != null) {
            editing.put(player.getUniqueId(), shortcutSlot);
            player.sendActionBar("§e请选择要配置到快捷栏 " + (shortcutSlot + 1) + " 的能力。");
            return;
        }

        String abilityId = meta.getPersistentDataContainer().get(keys.menuAbility, PersistentDataType.STRING);
        if (abilityId == null) return;
        if (!activity.state().unlocked.contains(abilityId)) {
            player.sendMessage("§c该神格尚未觉醒。");
            return;
        }
        Integer targetSlot = editing.get(player.getUniqueId());
        if (targetSlot == null) {
            player.sendMessage("§e先点击上方三个快捷栏位之一。");
            return;
        }
        activity.state().loadout.put(targetSlot, abilityId);
        items.refresh(player, activity.state());
        player.sendMessage("§a已将 " + Text.plain(plugin.abilities().get(abilityId).displayName()) + " §a配置到快捷栏 " + (targetSlot + 1) + "。");
        activity.saveNow();
        open(player);
    }

    private ItemStack loadoutButton(int slot, String abilityId) {
        AbilityDefinition ability = abilityId == null ? null : plugin.abilities().get(abilityId);
        ItemStack item = new ItemStack(ability == null ? Material.GRAY_STAINED_GLASS_PANE : ability.icon());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.menuSlot, PersistentDataType.INTEGER, slot);
        meta.setDisplayName(ability == null ? "§7快捷栏 " + (slot + 1) + " · 未配置"
                : "§e快捷栏 " + (slot + 1) + " §7→ §f" + Text.plain(ability.displayName()));
        meta.setLore(List.of("§8点击后选择要放入该栏位的技能"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack abilityItem(AbilityDefinition ability, ActivityState state) {
        boolean unlocked = state.unlocked.contains(ability.id());
        ItemStack item = new ItemStack(unlocked ? ability.icon() : Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.menuAbility, PersistentDataType.STRING, ability.id());
        if (!unlocked) {
            meta.setDisplayName("§8未知神格");
            meta.setLore(List.of("§7尚未觉醒", "§8试炼会在适当的时刻揭开它的名字。"));
            item.setItemMeta(meta);
            return item;
        }

        meta.setDisplayName("§f" + Text.plain(ability.displayName()));
        List<String> lore = new ArrayList<>();
        lore.add("§a已解锁");
        lore.add("§7持续：" + TimeUtil.format(ability.duration()));
        lore.add(activity.state().ascended ? "§d登神状态：无冷却" : "§7恢复：" + TimeUtil.format(ability.cooldown()));
        long cooldownUntil = state.cooldowns.getOrDefault(ability.id(), 0L);
        if (!state.ascended && cooldownUntil > System.currentTimeMillis()) lore.add("§c恢复：" + TimeUtil.formatUntil(cooldownUntil));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
