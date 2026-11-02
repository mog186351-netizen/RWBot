package rw.bot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class RWBotPlugin extends JavaPlugin implements TabExecutor {

    private BotManager bots;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        bots = new BotManager(this);
        bots.start();
        getCommand("rwbotspawn").setExecutor(this);
        getCommand("rwbotspawn").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        if (bots != null) {
            bots.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/rwbotspawn <name> §7- spawn bot");
            sender.sendMessage("§e/rwbotspawn list §7- list bots");
            sender.sendMessage("§e/rwbotspawn clear [name] §7- remove all bots or one bot");
            return true;
        }

        String first = args[0].toLowerCase();

        if (first.equals("clear")) {
            int n = bots.clear(args.length > 1 ? args[1] : null);
            sender.sendMessage("§aRemoved " + n + " bot(s).");
            return true;
        }

        if (first.equals("list")) {
            List<String> names = bots.list();
            sender.sendMessage(names.isEmpty() ? "§7No bots." : "§aBots: §f" + String.join(", ", names));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can spawn a bot.");
            return true;
        }

        String error = bots.add(args[0], player.getLocation());
        if (error != null) {
            sender.sendMessage("§c" + error);
        } else {
            sender.sendMessage("§aBot §f" + args[0] + " §aspawned.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("list");
            out.add("clear");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            out.addAll(bots.list());
        }
        return out;
    }
}
