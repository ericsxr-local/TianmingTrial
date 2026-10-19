package com.eric.xzxm.tianming.ui;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import com.eric.xzxm.tianming.service.ActivityManager;
import com.eric.xzxm.tianming.util.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class DisguiseMenu {
    private static final List<String> DEFAULT_FORMS = List.of(
            "VILLAGER", "ZOMBIE", "HUSK", "DROWNED", "SKELETON", "WITHER_SKELETON",
            "CREEPER", "SPIDER", "CAVE_SPIDER", "ENDERMAN", "WITCH", "PILLAGER",
            "VINDICATOR", "EVOKER", "WOLF", "FOX", "CAT", "AXOLOTL", "BLAZE",
            "GHAST", "SLIME", "IRON_GOLEM", "SNOWMAN", "PLAYER"
    );

    private final TianmingTrialPlugin plugin;
    private final ActivityManager activity;
    private final PluginKeys keys;

    public DisguiseMenu(TianmingTrialPlugin plugin, ActivityManager activity, PluginKeys keys) {
        this.plugin = plugin;
        this.activity = activity;
        this.keys = keys;
    }

    public void open(Player player) {
        if (!activity.isHolder(player) || !activity.state().unlocked.contains("disguise")) {
            player.sendMessage("§c你尚未拥有伪装神格。");
            return;
        }
        DisguiseMenuHolder holder = new DisguiseMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, "§5§l选择伪装形态");
        holder.setInventory(inventory);
        List<String> forms = plugin.getConfig().getStringList("disguise.allowed-forms");
        if (forms.isEmpty()) forms = DEFAULT_FORMS;

        int slot = 9;
        for (String rawForm : forms) {
            if (slot >= 45) break;
            String form = rawForm.toUpperCase(Locale.ROOT);
            inventory.setItem(slot++, formItem(form, form.equalsIgnoreCase(activity.state().disguiseForm)));
        }
        player.openInventory(inventory);
    }

    public boolean isMenu(Inventory inventory) {
        return inventory.getHolder() instanceof DisguiseMenuHolder;
    }

    public void click(Player player, Inventory inventory, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) return;
        ItemStack item = inventory.getItem(rawSlot);
        if (item == null || !item.hasItemMeta()) return;
        String form = item.getItemMeta().getPersistentDataContainer().get(keys.disguiseForm, PersistentDataType.STRING);
        if (form == null) return;
        player.closeInventory();
        activity.activateDisguise(player, form);
    }

    private ItemStack formItem(String form, boolean selected) {
        ItemStack item = new ItemStack(icon(form));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.disguiseForm, PersistentDataType.STRING, form);
        meta.setDisplayName((selected ? "§a✓ " : "§d") + display(form));
        meta.setLore(List.of(selected ? "§a当前选择" : "§7点击选择并开始伪装", "§8无碰撞，不会挤压天命者"));
        item.setItemMeta(meta);
        return item;
    }

    private Material icon(String form) {
        return switch (form) {
            case "ZOMBIE", "HUSK", "DROWNED" -> Material.ZOMBIE_HEAD;
            case "SKELETON", "WITHER_SKELETON" -> Material.SKELETON_SKULL;
            case "CREEPER" -> Material.CREEPER_HEAD;
            case "SPIDER", "CAVE_SPIDER" -> Material.SPIDER_EYE;
            case "ENDERMAN" -> Material.ENDER_PEARL;
            case "WITCH" -> Material.POTION;
            case "PILLAGER", "VINDICATOR", "EVOKER" -> Material.CROSSBOW;
            case "WOLF" -> Material.BONE;
            case "FOX" -> Material.SWEET_BERRIES;
            case "CAT" -> Material.COD;
            case "AXOLOTL" -> Material.TROPICAL_FISH_BUCKET;
            case "BLAZE" -> Material.BLAZE_ROD;
            case "GHAST" -> Material.GHAST_TEAR;
            case "SLIME" -> Material.SLIME_BALL;
            case "IRON_GOLEM" -> Material.IRON_BLOCK;
            case "SNOWMAN" -> Material.CARVED_PUMPKIN;
            case "PLAYER" -> Material.PLAYER_HEAD;
            default -> Material.EMERALD;
        };
    }

    private String display(String form) {
        return switch (form) {
            case "ZOMBIE" -> "僵尸";
            case "HUSK" -> "尸壳";
            case "DROWNED" -> "溺尸";
            case "SKELETON" -> "骷髅";
            case "WITHER_SKELETON" -> "凋灵骷髅";
            case "CREEPER" -> "苦力怕";
            case "SPIDER" -> "蜘蛛";
            case "CAVE_SPIDER" -> "洞穴蜘蛛";
            case "ENDERMAN" -> "末影人";
            case "WITCH" -> "女巫";
            case "PILLAGER" -> "掠夺者";
            case "VINDICATOR" -> "卫道士";
            case "EVOKER" -> "唤魔者";
            case "WOLF" -> "狼";
            case "FOX" -> "狐狸";
            case "CAT" -> "猫";
            case "AXOLOTL" -> "美西螈";
            case "BLAZE" -> "烈焰人";
            case "GHAST" -> "恶魂";
            case "SLIME" -> "史莱姆";
            case "IRON_GOLEM" -> "铁傀儡";
            case "SNOWMAN" -> "雪傀儡";
            case "PLAYER" -> "玩家";
            default -> "村民";
        };
    }
}
