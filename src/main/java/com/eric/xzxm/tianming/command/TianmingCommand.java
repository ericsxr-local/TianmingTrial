package com.eric.xzxm.tianming.command;

import com.eric.xzxm.tianming.TianmingTrialPlugin;
import com.eric.xzxm.tianming.model.AbilityDefinition;
import com.eric.xzxm.tianming.service.ActivityManager;
import com.eric.xzxm.tianming.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TianmingCommand implements CommandExecutor, TabCompleter {
    private final TianmingTrialPlugin plugin;
    private final ActivityManager activity;

    public TianmingCommand(TianmingTrialPlugin plugin, ActivityManager activity) {
        this.plugin = plugin;
        this.activity = activity;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tianming.admin")) {
            sender.sendMessage("§c你没有管理天命试炼的权限。");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> start(sender, args, false);
            case "end" -> {
                if (!activity.isActive()) sender.sendMessage("§7当前没有进行中的活动。");
                else {
                    activity.end("§7管理员结束了本轮活动");
                    sender.sendMessage("§a已结束天命试炼。");
                }
            }
            case "status" -> sender.sendMessage(activity.status());
            case "reload" -> {
                try {
                    plugin.reloadPluginFiles();
                    sender.sendMessage("§a配置与 abilities.yml 已重新加载。");
                } catch (RuntimeException ex) {
                    sender.sendMessage("§c加载失败：" + ex.getMessage());
                    plugin.getLogger().warning("Reload failed: " + ex.getMessage());
                }
            }
            case "test" -> test(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void start(CommandSender sender, String[] args, boolean test) {
        if (args.length < 2) {
            sender.sendMessage("§e用法：/tm " + (test ? "test start <玩家>" : "start <玩家>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§c该玩家必须在线。");
            return;
        }
        if (!activity.start(target, test)) {
            sender.sendMessage("§c已有活动进行中；请先使用 /tm end 或 /tm test stop。");
            return;
        }
        sender.sendMessage("§a已启动" + (test ? "测试" : "正式") + "天命试炼，天命者为 §e" + target.getName());
    }

    private void test(CommandSender sender, String[] args) {
        if (args.length < 2) {
            testHelp(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> start(sender, slice(args, 1), true);
            case "stop" -> {
                if (!activity.isActive() || !activity.state().testMode) {
                    sender.sendMessage("§c当前没有测试模式活动。");
                    return;
                }
                activity.end("§7测试模式已结束");
                sender.sendMessage("§a测试模式已结束。");
            }
            case "reset" -> {
                if (!activity.resetTest()) sender.sendMessage("§c当前没有可重置的测试模式。");
                else sender.sendMessage("§a测试进度、冷却和计时已重置。");
            }
            case "day" -> setDay(sender, args);
            case "day-length" -> setTiming(sender, args, true);
            case "coordinate-interval" -> setTiming(sender, args, false);
            case "skill" -> setSkill(sender, args);
            default -> testHelp(sender);
        }
    }

    private void setDay(CommandSender sender, String[] args) {
        if (!inTest(sender) || args.length != 4 || !args[2].equalsIgnoreCase("set")) {
            sender.sendMessage("§e用法：/tm test day set <0-12>");
            return;
        }
        try {
            int day = Integer.parseInt(args[3]);
            if (day < 0 || day > plugin.getConfig().getInt("activity.total-days", 12)) throw new NumberFormatException();
            activity.setTestDay(day);
            sender.sendMessage("§a测试天数已设为第 §e" + day + " §a日。");
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c天数必须为 0 至 12 的整数。");
        }
    }

    private void setTiming(CommandSender sender, String[] args, boolean dayLength) {
        if (!inTest(sender) || args.length != 3) {
            sender.sendMessage("§e用法：/tm test " + (dayLength ? "day-length" : "coordinate-interval") + " <时间>");
            return;
        }
        Duration value = duration(sender, args[2]);
        if (value == null) return;
        if (dayLength) activity.testDayLength(value); else activity.testExposure(value);
        sender.sendMessage("§a测试" + (dayLength ? "每日时长" : "坐标曝光间隔") + "已改为 §e" + TimeUtil.format(value));
    }

    private void setSkill(CommandSender sender, String[] args) {
        if (!inTest(sender) || args.length != 5 || (!args[3].equalsIgnoreCase("duration") && !args[3].equalsIgnoreCase("cooldown"))) {
            sender.sendMessage("§e用法：/tm test skill <技能ID> <duration|cooldown> <时间>");
            return;
        }
        AbilityDefinition ability = plugin.abilities().get(args[2].toLowerCase(Locale.ROOT));
        if (ability == null) {
            sender.sendMessage("§c未知技能 ID。可用：" + String.join(", ", plugin.abilities().all().stream().map(AbilityDefinition::id).toList()));
            return;
        }
        Duration value = duration(sender, args[4]);
        if (value == null) return;
        activity.testTiming(args[3].toLowerCase(Locale.ROOT), ability.id(), value);
        sender.sendMessage("§a" + ability.id() + " 的测试" + (args[3].equalsIgnoreCase("duration") ? "持续时间" : "冷却") + "已改为 §e" + TimeUtil.format(value));
    }

    private boolean inTest(CommandSender sender) {
        if (activity.isActive() && activity.state().testMode) return true;
        sender.sendMessage("§c该命令只能在 /tm test start 启动的测试活动中使用。");
        return false;
    }

    private @Nullable Duration duration(CommandSender sender, String raw) {
        try {
            return TimeUtil.parse(raw);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c时间格式错误，请用 45s、2m30s、1h 或 24h。");
            return null;
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6§l天命试炼管理");
        sender.sendMessage("§e/tm start <玩家> §7启动正式活动（每真实 24 小时推进一天）");
        sender.sendMessage("§e/tm end §7立刻结束并清理本轮活动");
        sender.sendMessage("§e/tm status §7查看天命者、天数与坐标曝光倒计时");
        sender.sendMessage("§e/tm reload §7重载 config.yml 与 abilities.yml");
        sender.sendMessage("§e/tm test ... §7打开单人测试模式帮助");
    }

    private void testHelp(CommandSender sender) {
        sender.sendMessage("§d§l天命测试模式");
        sender.sendMessage("§e/tm test start <玩家> §7默认：5 分钟/天、1 分钟曝光");
        sender.sendMessage("§e/tm test day set <0-12> §7直接调整解锁天数");
        sender.sendMessage("§e/tm test day-length <时间> §7例如 20s");
        sender.sendMessage("§e/tm test coordinate-interval <时间>");
        sender.sendMessage("§e/tm test skill <ID> <duration|cooldown> <时间>");
        sender.sendMessage("§e/tm test reset §7清空测试进度与计时");
        sender.sendMessage("§e/tm test stop §7结束测试模式");
    }

    private static String[] slice(String[] source, int start) {
        String[] result = new String[source.length - start];
        System.arraycopy(source, start, result, 0, result.length);
        return result;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) return prefix(args[0], List.of("start", "end", "status", "reload", "test"));
        if (args[0].equalsIgnoreCase("start") && args.length == 2) return prefix(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (!args[0].equalsIgnoreCase("test")) return values;
        if (args.length == 2) return prefix(args[1], List.of("start", "stop", "reset", "day", "day-length", "coordinate-interval", "skill"));
        if (args.length == 3 && args[1].equalsIgnoreCase("start")) return prefix(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (args.length == 3 && args[1].equalsIgnoreCase("day")) return prefix(args[2], List.of("set"));
        if (args.length == 3 && args[1].equalsIgnoreCase("skill")) return prefix(args[2], plugin.abilities().all().stream().map(AbilityDefinition::id).toList());
        if (args.length == 4 && args[1].equalsIgnoreCase("skill")) return prefix(args[3], List.of("duration", "cooldown"));
        return values;
    }

    private static List<String> prefix(String input, List<String> source) {
        return source.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))).toList();
    }
}
