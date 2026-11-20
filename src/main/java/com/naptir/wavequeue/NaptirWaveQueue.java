package com.naptir.wavequeue;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

public final class NaptirWaveQueue extends JavaPlugin implements Listener {
    private final Queue<Wave> queue = new ArrayDeque<>();
    private final List<UUID> activeMobs = new ArrayList<>();
    private boolean enabled;
    private boolean waveRunning;
    private BukkitTask monitorTask;

    private record Wave(String viewer, EntityType type, int amount) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        enabled = getConfig().getBoolean("enabled-on-start", false);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("waves") != null) getCommand("waves").setExecutor(this);
        monitorTask = Bukkit.getScheduler().runTaskTimer(this, this::monitorWave, 20L, 20L);
        getLogger().info("NaptirWaveQueue enabled. Reception is " + (enabled ? "ON" : "OFF"));
    }

    @Override public void onDisable() {
        if (monitorTask != null) monitorTask.cancel();
        queue.clear();
        activeMobs.clear();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getName().equalsIgnoreCase(targetName())) {
            enabled = false;
            queue.clear();
            // Let already spawned mobs remain; they are part of the current world encounter.
            broadcast("&cNaptir left. Gift reception stopped and waiting gifts cleared.");
        }
    }

    @EventHandler public void onMobDeath(EntityDeathEvent event) {
        activeMobs.remove(event.getEntity().getUniqueId());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return help(sender);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                if (!targetOnline()) { sender.sendMessage(color("&cNaptir must be online before enabling waves.")); return true; }
                enabled = true; sender.sendMessage(color("&aWave reception enabled.")); return true;
            }
            case "off" -> { enabled = false; queue.clear(); sender.sendMessage(color("&cWave reception disabled; waiting queue cleared.")); return true; }
            case "status" -> {
                sender.sendMessage(color("&6Waves: " + (enabled ? "&aON" : "&cOFF") + " &7| Queue: &f" + queue.size() + " &7| Active mobs: &f" + activeMobs.size()));
                return true;
            }
            case "clear" -> { queue.clear(); sender.sendMessage(color("&aWaiting queue cleared.")); return true; }
            case "enqueue", "test" -> {
                if (args.length < 4) { sender.sendMessage(color("&cUsage: /waves enqueue <viewer> <ENTITY> <amount>")); return true; }
                if (!enabled) { sender.sendMessage(color("&cWave reception is OFF.")); return true; }
                if (!targetOnline()) { sender.sendMessage(color("&cNaptir is not online. Gift ignored.")); return true; }
                EntityType type;
                try { type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ex) { sender.sendMessage(color("&cUnknown entity type: " + args[2])); return true; }
                if (!type.isAlive() || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) { sender.sendMessage(color("&cThat entity cannot be used as a mob wave.")); return true; }
                int amount;
                try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { sender.sendMessage(color("&cAmount must be a number.")); return true; }
                int max = getConfig().getInt("max-mobs-per-wave", 20);
                if (amount < 1 || amount > max) { sender.sendMessage(color("&cAmount must be between 1 and " + max)); return true; }
                if (queue.size() >= getConfig().getInt("max-queue-size", 100)) { sender.sendMessage(color("&cWave queue is full.")); return true; }
                queue.add(new Wave(args[1], type, amount));
                sender.sendMessage(color("&aGift queued at position " + (queue.size() + (waveRunning ? 1 : 0))));
                startNextWave();
                return true;
            }
            default -> { return help(sender); }
        }
    }

    private void startNextWave() {
        if (waveRunning || queue.isEmpty() || !enabled) return;
        Player target = targetPlayer();
        if (target == null) return;
        Wave wave = queue.poll();
        waveRunning = true;
        activeMobs.clear();
        int spawned = 0;
        for (int i = 0; i < wave.amount(); i++) {
            Location location = findSpawnLocation(target, i);
            if (location == null) continue;
            Entity entity = target.getWorld().spawnEntity(location, wave.type());
            if (entity instanceof LivingEntity living) {
                if (getConfig().getBoolean("show-mob-name", true)) {
                    living.setCustomName(color("&e" + wave.viewer()));
                    living.setCustomNameVisible(true);
                }
                activeMobs.add(entity.getUniqueId());
                spawned++;
            } else entity.remove();
        }
        String msg = getConfig().getString("messages.started", "&eWave started: {viewer} {mob} x{amount}")
                .replace("{viewer}", wave.viewer()).replace("{mob}", wave.type().name()).replace("{amount}", String.valueOf(spawned));
        broadcast(msg);
        if (spawned == 0) waveRunning = false;
    }

    private void monitorWave() {
        if (!waveRunning) { startNextWave(); return; }
        Iterator<UUID> it = activeMobs.iterator();
        while (it.hasNext()) {
            Entity entity = Bukkit.getEntity(it.next());
            if (entity == null || !entity.isValid() || entity.isDead()) it.remove();
        }
        if (activeMobs.isEmpty()) {
            waveRunning = false;
            broadcast(getConfig().getString("messages.finished", "&aWave finished!"));
            startNextWave();
        }
    }

    private Location findSpawnLocation(Player player, int index) {
        Location base = player.getLocation();
        World world = player.getWorld();
        int radius = Math.max(2, getConfig().getInt("spawn-radius", 4));
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = (index * 2.399963229728653 + attempt * 0.7);
            int x = (int) Math.round(Math.cos(angle) * (radius + attempt % 3));
            int z = (int) Math.round(Math.sin(angle) * (radius + attempt % 3));
            int bx = base.getBlockX() + x, bz = base.getBlockZ() + z;
            int y = world.getHighestBlockYAt(bx, bz) + 1;
            Block feet = world.getBlockAt(bx, y, bz);
            Block head = world.getBlockAt(bx, y + 1, bz);
            if (feet.isPassable() && head.isPassable() && feet.getType() != Material.WATER && feet.getType() != Material.LAVA) {
                return new Location(world, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }

    private Player targetPlayer() { return Bukkit.getPlayerExact(targetName()); }
    private boolean targetOnline() { return targetPlayer() != null; }
    private String targetName() { return getConfig().getString("target-player", "Naptir"); }
    private void broadcast(String message) { Bukkit.broadcastMessage(color(getConfig().getString("messages.prefix", "&6[Waves] ") + message)); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private boolean help(CommandSender sender) {
        sender.sendMessage(color("&6/waves on|off|status|clear"));
        sender.sendMessage(color("&6/waves enqueue <viewer> <ENTITY> <amount>"));
        sender.sendMessage(color("&6/waves test <viewer> <ENTITY> <amount>"));
        return true;
    }
}
