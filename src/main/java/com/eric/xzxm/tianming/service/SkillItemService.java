package com.eric.xzxm.tianming.service;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import com.eric.xzxm.tianming.model.AbilityDefinition;
import com.eric.xzxm.tianming.model.ActivityState;
import com.eric.xzxm.tianming.util.PluginKeys;
import com.eric.xzxm.tianming.util.Text;
import com.eric.xzxm.tianming.util.TimeUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class SkillItemService {
    private static final String TOKEN = "skill_token";
    private static final String BOOK = "skill_book";
    private static final int STORAGE_SIZE = 36;

    private final TianmingTrialPlugin plugin;
    private final PluginKeys keys;

    public SkillItemService(TianmingTrialPlugin plugin, PluginKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public void issueToolkit(Player player, ActivityState state) {
        removeManaged(player);
        safePut(player, 5, token(0, state.loadout.get(0), state.ascended));
        safePut(player, 6, token(1, state.loadout.get(1), state.ascended));
        safePut(player, 7, token(2, state.loadout.get(2), state.ascended));
        safePut(player, 8, book());
    }

    /**
     * Updates existing token appearance without changing its position and restores
     * only genuinely missing managed items. This makes the toolkit recoverable if
     * another vanilla interaction ever consumes an item.
     */
    public void refresh(Player player, ActivityState state) {
        PlayerInventory inventory = player.getInventory();
        boolean[] presentSlots = new boolean[3];
        boolean bookPresent = false;

        for (int index = 0; index < inventory.getSize(); index++) {
            ItemStack item = inventory.getItem(index);
            Integer slot = getTokenSlot(item);
            if (slot != null) {
                if (slot < 0 || slot >= presentSlots.length || presentSlots[slot]) {
                    inventory.setItem(index, null);
                    continue;
                }
                presentSlots[slot] = true;
                inventory.setItem(index, token(slot, state.loadout.get(slot), state.ascended));
                continue;
            }
            if (isBook(item)) {
                if (bookPresent) inventory.setItem(index, null);
                else bookPresent = true;
            }
        }

        for (int slot = 0; slot < presentSlots.length; slot++) {
            if (!presentSlots[slot]) putMissing(player, token(slot, state.loadout.get(slot), state.ascended), 5 + slot);
        }
        if (!bookPresent) putMissing(player, book(), 8);
    }

    public void removeManaged(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getSize(); index++) {
            if (isManaged(inventory.getItem(index))) inventory.setItem(index, null);
        }
        if (isManaged(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
    }

    public boolean isManaged(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        String kind = item.getItemMeta().getPersistentDataContainer().get(keys.itemKind, PersistentDataType.STRING);
        return TOKEN.equals(kind) || BOOK.equals(kind);
    }

    public boolean isBook(ItemStack item) {
        return kind(item, BOOK);
    }

    public Integer getTokenSlot(ItemStack item) {
        if (!kind(item, TOKEN)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keys.skillSlot, PersistentDataType.INTEGER);
    }

    public void applyVanillaCooldown(Player player, AbilityDefinition ability, Duration cooldown) {
        long ticks = Math.min(Integer.MAX_VALUE, Math.max(1L, cooldown.toSeconds() * 20L));
        player.setCooldown(ability.icon(), (int) ticks);
    }

    private ItemStack token(int slot, String abilityId, boolean ascended) {
        AbilityDefinition ability = abilityId == null ? null : plugin.abilities().get(abilityId);
        ItemStack item = new ItemStack(ability == null ? Material.GRAY_DYE : ability.icon());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, TOKEN);
        meta.getPersistentDataContainer().set(keys.skillSlot, PersistentDataType.INTEGER, slot);
        if (ability == null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "技能栏 " + (slot + 1) + " · 未配置");
            meta.setLore(List.of(ChatColor.GRAY + "在天命之书中配置已解锁能力"));
        } else {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ " + Text.plain(ability.displayName()));
            meta.setLore(List.of(
                    ChatColor.GRAY + "右键使用，左键释放主动效果",
                    ChatColor.DARK_GRAY + (ascended ? "登神状态：无冷却" : "独立恢复：" + TimeUtil.format(ability.cooldown()))
            ));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack book() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, BOOK);
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "天命之书");
        meta.setLore(List.of(ChatColor.GRAY + "右键配置三个快捷技能栏", ChatColor.DARK_GRAY + "不可丢弃、合成或放入容器"));
        item.setItemMeta(meta);
        return item;
    }

    private boolean kind(ItemStack item, String expected) {
        return item != null && item.hasItemMeta()
                && expected.equals(item.getItemMeta().getPersistentDataContainer().get(keys.itemKind, PersistentDataType.STRING));
    }

    private void putMissing(Player player, ItemStack managed, int fallbackSlot) {
        PlayerInventory inventory = player.getInventory();
        for (int index = 0; index < STORAGE_SIZE; index++) {
            ItemStack candidate = inventory.getItem(index);
            if (candidate == null || candidate.getType().isAir()) {
                inventory.setItem(index, managed);
                return;
            }
        }
        safePut(player, fallbackSlot, managed);
    }

    private void safePut(Player player, int slot, ItemStack special) {
        PlayerInventory inventory = player.getInventory();
        ItemStack old = inventory.getItem(slot);
        if (old != null && !old.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = inventory.addItem(old);
            leftovers.values().forEach(stack -> safeDrop(player, stack));
        }
        inventory.setItem(slot, special);
    }

    private void safeDrop(Player player, ItemStack stack) {
        Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), stack);
        dropped.setOwner(player.getUniqueId());
        dropped.setPickupDelay(20);
        long seconds = plugin.getConfig().getLong("activity.safe-drop-owner-seconds", 180L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dropped.isValid()) dropped.setOwner(null);
        }, Math.max(1L, seconds) * 20L);
    }
}
