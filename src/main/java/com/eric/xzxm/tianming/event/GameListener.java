package com.eric.xzxm.tianming.event;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import com.eric.xzxm.tianming.model.AbilityDefinition;
import com.eric.xzxm.tianming.model.AbilityType;
import com.eric.xzxm.tianming.service.ActivityManager;
import com.eric.xzxm.tianming.service.DisguiseService;
import com.eric.xzxm.tianming.service.SkillItemService;
import com.eric.xzxm.tianming.ui.DisguiseMenu;
import com.eric.xzxm.tianming.ui.SkillMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

public final class GameListener implements Listener {
    private final TianmingTrialPlugin plugin;
    private final ActivityManager activity;
    private final SkillItemService items;
    private final SkillMenu menu;
    private final DisguiseMenu disguiseMenu;
    private final DisguiseService disguises;

    public GameListener(TianmingTrialPlugin plugin, ActivityManager activity, SkillItemService items,
                        SkillMenu menu, DisguiseMenu disguiseMenu, DisguiseService disguises) {
        this.plugin = plugin;
        this.activity = activity;
        this.items = items;
        this.menu = menu;
        this.disguiseMenu = disguiseMenu;
        this.disguises = disguises;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();
        if (items.isBook(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            menu.open(player);
            return;
        }
        Integer slot = items.getTokenSlot(item);
        if (slot == null || !activity.isHolder(player)) return;

        String abilityId = activity.state().loadout.get(slot);
        if (abilityId == null) {
            player.sendMessage("§7该快捷栏尚未配置。");
            event.setCancelled(true);
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            AbilityDefinition ability = plugin.abilities().get(abilityId);
            if (ability != null && ability.type() == AbilityType.DISGUISE) disguiseMenu.open(player);
            else activity.activate(player, abilityId);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            activity.castActive(player, abilityId);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onManagedInteractEntity(PlayerInteractEntityEvent event) {
        if (isManagedInHand(event.getPlayer(), event.getHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onManagedInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (isManagedInHand(event.getPlayer(), event.getHand())) event.setCancelled(true);
    }

    private boolean isManagedInHand(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        return items.isManaged(item);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (!items.isManaged(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar("§c天命物品不能丢弃。");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (disguiseMenu.isMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                disguiseMenu.click(player, event.getView().getTopInventory(), event.getRawSlot());
            }
            return;
        }
        if (menu.isMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                menu.click(player, event.getView().getTopInventory(), event.getRawSlot());
            }
            return;
        }

        boolean managedCurrent = items.isManaged(event.getCurrentItem());
        boolean managedCursor = items.isManaged(event.getCursor());
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if ((managedCurrent && (event.getClickedInventory() == null
                || event.getClickedInventory().getType() != InventoryType.PLAYER
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY))
                || (managedCursor && topSlot)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§c天命物品不能放入容器、合成栏或熔炉。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (menu.isMenu(event.getView().getTopInventory()) || disguiseMenu.isMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
            return;
        }
        if (items.isManaged(event.getOldCursor())
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (items.isManaged(item)) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage("§c天命物品不能用于合成。");
                return;
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!activity.hasCreation(event.getPlayer())) return;
        Material placed = event.getBlockPlaced().getType();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (hand.getType() == placed && !items.isManaged(hand) && placed.isBlock()) {
                hand.setAmount(hand.getMaxStackSize());
            }
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(items::isManaged);
        if (!activity.isHolder(event.getEntity())) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null) plugin.getServer().getScheduler().runTask(plugin, () -> activity.transfer(killer));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> activity.onRespawn(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHolderDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && activity.isHolder(player)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> disguises.syncHealth(player));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHolderHeal(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player && activity.isHolder(player)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> disguises.syncHealth(player));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProxyEnvironmentalDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent) && disguises.isProxy(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProxyDamage(EntityDamageByEntityEvent event) {
        disguises.onProxyDamage(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null) disguises.onMove(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onHolderChat(AsyncPlayerChatEvent event) {
        if (!activity.isHolder(event.getPlayer())) return;
        event.setFormat("§c[天命者] §f%1$s§7: §f%2$s");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        disguises.onViewerJoin(event.getPlayer());
        activity.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activity.onQuit(event.getPlayer());
    }
}
