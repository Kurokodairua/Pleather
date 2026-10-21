package de.kurokodairua.pleatherPlugin;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PleatherPlugin extends JavaPlugin implements Listener {

    private final Random random = new Random();

    // UUID -> letzter Zeitpunkt
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private final long COOLDOWN = 60 * 1000; // 60 Sekunden

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        startCleanupTask();

        getLogger().info("PleatherPlugin mit Cleanup wurde aktiviert!");
    }

    @EventHandler
    public void onShear(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (player.getInventory().getItemInMainHand().getType() != Material.SHEARS) {
            return;
        }

        Entity entity = event.getRightClicked();

        if (!(entity instanceof LivingEntity)) return;

        long now = System.currentTimeMillis();


        UUID entityId = entity.getUniqueId();
        // Cooldown prüfen
        if (cooldowns.containsKey(entityId)) {
            long lastUse = cooldowns.get(entityId);

            if ((now - lastUse) < COOLDOWN) {
                long secondsLeft = (COOLDOWN - (now - lastUse)) / 1000;
                player.sendActionBar("§cDieses Tier kann in " + secondsLeft + "s wieder geschoren werden!");
                return;
            }
        }

        Material dropMaterial = null;
        int amount = 1 + random.nextInt(2);

        switch (entity.getType()) {
            case COW:
            case HORSE:
            case DONKEY:
            case MULE:
            case LLAMA:
                dropMaterial = Material.LEATHER;
                break;
            case CHICKEN:
                dropMaterial = Material.FEATHER;
                break;

            default:
                return;
        }



        // Drop
        entity.getWorld().dropItemNaturally(entity.getLocation(),
                new ItemStack(dropMaterial, amount));

        // Scher-Sound + Effekte
        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);

        playEffects(entity);

        cooldowns.put(entityId, now);

        damageShears(player);

        event.setCancelled(true);
    }

    private void playEffects(Entity entity) {
        Location loc = entity.getLocation();

        // ✨ Partikel (kleine Wolke + Happy)
        entity.getWorld().spawnParticle(Particle.CLOUD, loc.add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.01);
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.4, 0.4, 0.4, 0.1);

        // 🎵 Jingle (mehrere Sounds kombiniert)
        entity.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
        entity.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f);
        entity.getWorld().playSound(loc, Sound.ENTITY_CHICKEN_EGG, 0.6f, 1.2f);
    }

    // Entfernt Tiere sofort bei Tod
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        cooldowns.remove(event.getEntity().getUniqueId());
    }

    // Cleanup Task
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {

            int before = cooldowns.size();

            Iterator<Map.Entry<UUID, Long>> it = cooldowns.entrySet().iterator();
            long now = System.currentTimeMillis();

            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID uuid = entry.getKey();
                long lastUse = entry.getValue();

                Entity entity = findEntity(uuid);

                // Entfernen wenn:
                // - Tier existiert nicht mehr
                // - oder Cooldown lange vorbei (z.B. doppelte Zeit)
                if (entity == null || !entity.isValid() || (now - lastUse) > (COOLDOWN * 2)) {
                    it.remove();
                }
            }

            int after = cooldowns.size();

            getLogger().info("Cleanup durchgeführt: " + before + " -> " + after);

        }, 20L * 60, 20L * 60 * 5); // Start nach 1 min, dann alle 5 min
    }

    // Sucht Entity anhand UUID
    private Entity findEntity(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(uuid)) {
                    return entity;
                }
            }
        }
        return null;
    }

    private void damageShears(Player player) {
        ItemStack shears = player.getInventory().getItemInMainHand();

        if (shears.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + 1);
            shears.setItemMeta(damageable);

            if (damageable.getDamage() >= shears.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }
}