package com.eric.xzxm.tianming.service;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dependency-free visual disguise with damage/health mirroring.
 * The real holder is hidden from other clients; the holder sees the selected proxy
 * while their own third-person model is made invisible without particles.
 */
public final class DisguiseService {
    private final TianmingTrialPlugin plugin;
    private final Map<UUID, Entity> proxyByHolder = new HashMap<>();
    private final Map<UUID, UUID> holderByProxy = new HashMap<>();
    private final Team collisionTeam;

    public DisguiseService(TianmingTrialPlugin plugin) {
        this.plugin = plugin;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team existing = scoreboard.getTeam("tm_disguise");
        this.collisionTeam = existing == null ? scoreboard.registerNewTeam("tm_disguise") : existing;
        this.collisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    public void start(Player holder, String requestedForm) {
        stop(holder);
        Entity proxy = createProxy(holder, requestedForm);
        proxyByHolder.put(holder.getUniqueId(), proxy);
        holderByProxy.put(proxy.getUniqueId(), holder.getUniqueId());
        collisionTeam.addEntry(proxy.getUniqueId().toString());

        // Other players see only the chosen proxy. The holder sees the proxy as well,
        // while their own body is particle-free invisible for a proper disguise view.
        holder.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false, false));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(holder)) viewer.hidePlayer(plugin, holder);
        }
        syncHealth(holder);
    }

    public void stop(Player holder) {
        Entity proxy = proxyByHolder.remove(holder.getUniqueId());
        if (proxy != null) {
            holderByProxy.remove(proxy.getUniqueId());
            collisionTeam.removeEntry(proxy.getUniqueId().toString());
            proxy.remove();
        }
        holder.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(plugin, holder);
    }

    public boolean isProxy(Entity entity) {
        return entity != null && holderByProxy.containsKey(entity.getUniqueId());
    }

    public void onMove(Player holder, Location destination) {
        Entity proxy = proxyByHolder.get(holder.getUniqueId());
        if (proxy == null || !proxy.isValid()) return;
        proxy.teleport(visualLocation(destination));
        syncHealth(holder);
    }

    public void onViewerJoin(Player viewer) {
        for (UUID holderId : proxyByHolder.keySet()) {
            Player holder = Bukkit.getPlayer(holderId);
            if (holder != null && !holder.equals(viewer)) viewer.hidePlayer(plugin, holder);
        }
    }

    public void onHolderQuit(Player holder) {
        stop(holder);
    }

    public void onProxyDamage(EntityDamageByEntityEvent event) {
        UUID holderId = holderByProxy.get(event.getEntity().getUniqueId());
        if (holderId == null) return;
        event.setCancelled(true);
        Player holder = Bukkit.getPlayer(holderId);
        if (holder == null || holder.isDead()) return;

        event.getEntity().playEffect(EntityEffect.HURT);
        holder.playEffect(EntityEffect.HURT);
        holder.damage(event.getFinalDamage(), event.getDamager());
        plugin.getServer().getScheduler().runTask(plugin, () -> syncHealth(holder));
    }

    public void syncHealth(Player holder) {
        Entity proxy = proxyByHolder.get(holder.getUniqueId());
        if (!(proxy instanceof LivingEntity living) || !proxy.isValid()) return;

        double maximum = Math.max(1D, holder.getMaxHealth());
        AttributeInstance proxyMax = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (proxyMax != null && Math.abs(proxyMax.getBaseValue() - maximum) > 0.01D) {
            proxyMax.setBaseValue(maximum);
        }
        double displayed = Math.max(0.1D, Math.min(maximum, holder.getHealth()));
        if (Math.abs(living.getHealth() - displayed) > 0.01D) living.setHealth(displayed);
    }

    public void clearAll() {
        for (UUID holderId : proxyByHolder.keySet().toArray(UUID[]::new)) {
            Player holder = Bukkit.getPlayer(holderId);
            if (holder != null) stop(holder);
            else {
                Entity proxy = proxyByHolder.remove(holderId);
                if (proxy != null) {
                    collisionTeam.removeEntry(proxy.getUniqueId().toString());
                    proxy.remove();
                }
            }
        }
        holderByProxy.clear();
    }

    private Entity createProxy(Player holder, String requestedForm) {
        String form = requestedForm == null ? "VILLAGER" : requestedForm.toUpperCase(Locale.ROOT);
        Location location = visualLocation(holder.getLocation());
        if ("PLAYER".equals(form)) return playerShapedProxy(holder, location);

        try {
            EntityType type = EntityType.valueOf(form);
            Entity entity = holder.getWorld().spawnEntity(location, type);
            if (entity instanceof LivingEntity living) {
                prepareMob(living, holder);
                return living;
            }
            entity.remove();
        } catch (IllegalArgumentException ignored) {
            // Invalid values fall back to a villager instead of breaking the ability.
        }
        LivingEntity villager = (LivingEntity) holder.getWorld().spawnEntity(location, EntityType.VILLAGER);
        prepareMob(villager, holder);
        return villager;
    }

    private void prepareMob(LivingEntity entity, Player holder) {
        entity.setAI(false);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setInvulnerable(false);
        entity.setCollidable(false);
        entity.setCanPickupItems(false);
        entity.setPersistent(false);

    }

    private ArmorStand playerShapedProxy(Player holder, Location location) {
        ArmorStand stand = holder.getWorld().spawn(location, ArmorStand.class);
        stand.setVisible(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(false);
        stand.setCollidable(false);
        stand.setPersistent(false);

        EntityEquipment equipment = stand.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(copy(holder.getInventory().getHelmet()));
            equipment.setChestplate(copy(holder.getInventory().getChestplate()));
            equipment.setLeggings(copy(holder.getInventory().getLeggings()));
            equipment.setBoots(copy(holder.getInventory().getBoots()));
            equipment.setItemInMainHand(copy(holder.getInventory().getItemInMainHand()));
        }
        return stand;
    }

    private Location visualLocation(Location source) {
        Location result = source.clone();
        Vector horizontal = source.getDirection().clone().setY(0D);
        if (horizontal.lengthSquared() > 0.001D) result.subtract(horizontal.normalize().multiply(1.15D));
        return result;
    }

    private ItemStack copy(ItemStack item) {
        return item == null || item.getType() == Material.AIR ? null : item.clone();
    }
}
