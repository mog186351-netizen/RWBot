package rw.bot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BotManager {

    record Saved(String name, String world, double x, double y, double z, float yaw, float pitch) {}

    private final RWBotPlugin plugin;
    private final File file;
    private final Map<String, Saved> saved = new LinkedHashMap<>();
    private final Map<String, NmsBot> active = new LinkedHashMap<>();
    private final Map<String, Long> respawnAt = new HashMap<>();
    private long tick;

    BotManager(RWBotPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bots.yml");
    }

    void start() {
        load();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Saved s : new ArrayList<>(saved.values())) {
                trySpawn(s);
            }
        }, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 41L, 1L);
    }

    String add(String name, Location loc) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            return "Name must be 1-16 characters: A-Z, 0-9, _";
        }
        String key = name.toLowerCase();
        if (key.equals("list") || key.equals("clear")) {
            return "That name is reserved.";
        }
        if (saved.containsKey(key) || Bukkit.getPlayerExact(name) != null) {
            return "A player or bot with that name already exists.";
        }
        Saved s = new Saved(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
        if (!trySpawn(s)) {
            return "Could not spawn the bot. Check the server console.";
        }
        saved.put(key, s);
        save();
        return null;
    }

    int clear(String only) {
        int n = 0;
        Iterator<String> it = saved.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (only != null && !key.equals(only.toLowerCase())) {
                continue;
            }
            NmsBot bot = active.remove(key);
            if (bot != null) {
                safeRemove(bot);
            }
            respawnAt.remove(key);
            it.remove();
            n++;
        }
        save();
        return n;
    }

    List<String> list() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Saved> e : saved.entrySet()) {
            out.add(e.getValue().name() + (active.containsKey(e.getKey()) ? "" : " (offline)"));
        }
        return out;
    }

    void shutdown() {
        for (NmsBot bot : active.values()) {
            safeRemove(bot);
        }
        active.clear();
    }

    private boolean trySpawn(Saved s) {
        World world = Bukkit.getWorld(s.world());
        if (world == null) {
            plugin.getLogger().warning("World '" + s.world() + "' not found for bot " + s.name());
            return false;
        }
        try {
            Location loc = new Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
            active.put(s.name().toLowerCase(), NmsBot.spawn(s.name(), loc));
            return true;
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().severe("Failed to spawn bot " + s.name() + ". This Paper version may have "
                    + "changed internal (NMS) code. Rebuild the plugin for this version. Error: " + e);
            e.printStackTrace();
            return false;
        }
    }

    private void onTick() {
        tick++;

        Iterator<Map.Entry<String, NmsBot>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, NmsBot> e = it.next();
            NmsBot bot = e.getValue();
            if (!bot.isOnline()) {
                it.remove();
                safeRemove(bot);
                respawnAt.put(e.getKey(), tick + 100);
                continue;
            }
            try {
                bot.tick(tick);
            } catch (LinkageError | RuntimeException ex) {
                plugin.getLogger().severe("Bot tick failed: " + ex);
            }
        }

        if (!respawnAt.isEmpty()) {
            Iterator<Map.Entry<String, Long>> rit = respawnAt.entrySet().iterator();
            List<Saved> toSpawn = new ArrayList<>();
            while (rit.hasNext()) {
                Map.Entry<String, Long> e = rit.next();
                if (tick >= e.getValue()) {
                    rit.remove();
                    Saved s = saved.get(e.getKey());
                    if (s != null) {
                        toSpawn.add(s);
                    }
                }
            }
            toSpawn.forEach(this::trySpawn);
        }
    }

    private void safeRemove(NmsBot bot) {
        try {
            bot.remove();
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Error while removing bot: " + e);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("bots");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection b = sec.getConfigurationSection(key);
            if (b == null) {
                continue;
            }
            String name = b.getString("name", key);
            saved.put(name.toLowerCase(), new Saved(name, b.getString("world", "world"),
                    b.getDouble("x"), b.getDouble("y"), b.getDouble("z"),
                    (float) b.getDouble("yaw"), (float) b.getDouble("pitch")));
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Saved> e : saved.entrySet()) {
            Saved s = e.getValue();
            String p = "bots." + e.getKey() + ".";
            yml.set(p + "name", s.name());
            yml.set(p + "world", s.world());
            yml.set(p + "x", s.x());
            yml.set(p + "y", s.y());
            yml.set(p + "z", s.z());
            yml.set(p + "yaw", s.yaw());
            yml.set(p + "pitch", s.pitch());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save bots.yml: " + ex);
        }
    }
}
