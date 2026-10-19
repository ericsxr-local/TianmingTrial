package com.eric.xzxm.tianming;

import com.eric.xzxm.tianming.command.TianmingCommand;
import com.eric.xzxm.tianming.config.AbilityRegistry;
import com.eric.xzxm.tianming.event.GameListener;
import com.eric.xzxm.tianming.service.ActivityManager;
import com.eric.xzxm.tianming.service.DisguiseService;
import com.eric.xzxm.tianming.service.HudManager;
import com.eric.xzxm.tianming.service.SkillItemService;
import com.eric.xzxm.tianming.service.StateStore;
import com.eric.xzxm.tianming.ui.DisguiseMenu;
import com.eric.xzxm.tianming.ui.SkillMenu;
import com.eric.xzxm.tianming.util.PluginKeys;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class TianmingTrialPlugin extends JavaPlugin {
    private AbilityRegistry abilities;
    private ActivityManager activity;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory");
        }
        saveResource("abilities.yml", false);

        PluginKeys keys = new PluginKeys(this);
        abilities = new AbilityRegistry(this);
        HudManager hud = new HudManager(this);
        DisguiseService disguises = new DisguiseService(this);
        SkillItemService items = new SkillItemService(this, keys);
        activity = new ActivityManager(this, abilities, new StateStore(this), hud, items, disguises);
        SkillMenu menu = new SkillMenu(this, activity, items, keys);
        DisguiseMenu disguiseMenu = new DisguiseMenu(this, activity, keys);

        getServer().getPluginManager().registerEvents(new GameListener(this, activity, items, menu, disguiseMenu, disguises), this);
        PluginCommand command = Objects.requireNonNull(getCommand("tianming"), "tianming command is missing from plugin.yml");
        TianmingCommand executor = new TianmingCommand(this, activity);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        activity.resumeOnlineHolder();
        getServer().getScheduler().runTaskTimer(this, activity::tick, 20L, 20L);
        getLogger().info("TianmingTrial enabled. Real-time activity state is persistent.");
    }

    @Override
    public void onDisable() {
        if (activity != null) activity.shutdown();
    }

    public AbilityRegistry abilities() {
        return abilities;
    }

    public ActivityManager activity() {
        return activity;
    }

    public void reloadPluginFiles() {
        reloadConfig();
        abilities.reload();
    }
}
