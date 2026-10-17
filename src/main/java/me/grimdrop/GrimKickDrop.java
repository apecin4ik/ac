package me.grimdrop;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Если игрока кикнули (например, Grim за пакеты) пока он в бою — его вещи высыпаются на землю.
 * Бой отслеживается самим плагином: любой удар игрок->игрок помечает обоих на combat-seconds секунд.
 */
public class GrimKickDrop extends JavaPlugin implements Listener {

    private final Map<UUID, Long> combat = new HashMap<>();
    private long combatMs;
    private List<String> keywords;
    private boolean dropExp;
    private String broadcast;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void load() {
        reloadConfig();
        combatMs = getConfig().getLong("combat-seconds", 15) * 1000L;
        keywords = getConfig().getStringList("reason-keywords");
        dropExp = getConfig().getBoolean("drop-exp", true);
        broadcast = getConfig().getString("broadcast", "");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            load();
            s.sendMessage(ChatColor.GREEN + "GrimKickDrop: конфиг перезагружен.");
        }
        return true;
    }

    // ---- бой

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) d = shooter;
        if (!(d instanceof Player attacker) || attacker.equals(victim)) return;
        long until = System.currentTimeMillis() + combatMs;
        combat.put(victim.getUniqueId(), until);
        combat.put(attacker.getUniqueId(), until);
    }

    private boolean inCombat(Player p) {
        Long until = combat.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        combat.remove(e.getPlayer().getUniqueId());
    }

    // ---- кик

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onKick(PlayerKickEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("grimkickdrop.bypass") || !inCombat(p)) return;

        if (!keywords.isEmpty()) {
            String reason = ChatColor.stripColor(String.valueOf(e.getReason())).toLowerCase();
            boolean match = false;
            for (String k : keywords) {
                if (!k.isEmpty() && reason.contains(k.toLowerCase())) { match = true; break; }
            }
            if (!match) return;
        }

        dropEverything(p);
        combat.remove(p.getUniqueId());
        if (broadcast != null && !broadcast.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcast.replace("{player}", p.getName())));
        }
    }

    private void dropEverything(Player p) {
        Location l = p.getLocation();
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {          // хотбар + инвентарь + броня + вторая рука
            if (it != null && it.getType() != Material.AIR) l.getWorld().dropItemNaturally(l, it);
        }
        inv.clear();
        if (dropExp) {
            int xp = p.getTotalExperience();
            if (xp > 0) l.getWorld().spawn(l, ExperienceOrb.class, o -> o.setExperience(xp));
            p.setTotalExperience(0);
            p.setLevel(0);
            p.setExp(0f);
        }
    }
}
