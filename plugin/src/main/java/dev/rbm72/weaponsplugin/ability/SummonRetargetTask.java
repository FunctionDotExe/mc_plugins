package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;
import java.util.Optional;

/**
 * Player-summoned mobs (necromancer skeletons/vexes/wraiths/guardians) are vanilla hostiles
 * whose only stock targeting goal is "nearest player" — and {@link dev.rbm72.weaponsplugin.listeners.PlayerSummonTargetListener}
 * blanket-cancels that so they never turn on their owner. Left alone that means they never
 * acquire any target at all and just stand there. This periodically hands any summon without a
 * live target the nearest real enemy (a boss entity, or any other hostile mob) in range, then
 * lets vanilla combat AI take it from there.
 */
public final class SummonRetargetTask extends BukkitRunnable {

    private static final double RETARGET_RADIUS = 32.0;

    private final WeaponsPlugin plugin;
    private final NamespacedKey playerSummonKey;

    public SummonRetargetTask(WeaponsPlugin plugin, NamespacedKey playerSummonKey) {
        this.plugin = plugin;
        this.playerSummonKey = playerSummonKey;
    }

    public void start() {
        runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Mob mob) || !mob.isValid()) {
                    continue;
                }
                if (!mob.getPersistentDataContainer().has(playerSummonKey, PersistentDataType.BYTE)) {
                    continue;
                }
                LivingEntity target = mob.getTarget();
                if (target != null && target.isValid() && !target.isDead()) {
                    continue;
                }
                findNearestEnemy(mob).ifPresent(mob::setTarget);
            }
        }
    }

    private Optional<LivingEntity> findNearestEnemy(Mob mob) {
        return mob.getWorld().getNearbyEntities(mob.getLocation(), RETARGET_RADIUS, RETARGET_RADIUS, RETARGET_RADIUS).stream()
                .filter(e -> e instanceof LivingEntity && !(e instanceof Player))
                .map(e -> (LivingEntity) e)
                .filter(e -> !e.getPersistentDataContainer().has(playerSummonKey, PersistentDataType.BYTE))
                .filter(e -> e instanceof Enemy || plugin.bossManager().instanceFor(e.getUniqueId()).isPresent())
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(mob.getLocation())));
    }
}
