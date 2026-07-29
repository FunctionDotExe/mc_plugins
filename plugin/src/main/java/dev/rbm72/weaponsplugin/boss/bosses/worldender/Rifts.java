package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P6's Unmooring channel</b> — Void Sovereign's floor loss (batch-5 §3, P6), plus the callback the
 * design calls out explicitly: the group's own P4 construction skill is the only way to replace ground.
 * Rifts open permanently at the arena's surface layer, same single-layer-removal approach as {@code
 * sovereign.Rifts} — the roster-wide pit rule already turns whatever void sits below the arena's floor
 * into a heavy, survivable hit and an eject, with no bookkeeping of depth required here. Some rifts are
 * filled with powder snow instead of deleted outright — a softer hazard that punishes a group without
 * P2's boots rather than an outright fall, so those boots matter twice in one fight.
 * <p>
 * Registered for the whole fight from {@link WorldenderFight}'s constructor; the bucket-empty rescue is a
 * no-op everywhere no rift has opened yet.
 */
final class Rifts implements Listener {

    private final WorldenderFight fight;
    private final Set<Long> open = new HashSet<>();
    private int opened;
    private boolean listening;

    Rifts(WorldenderFight fight) {
        this.fight = fight;
        fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
        listening = true;
    }

    int opened() {
        return opened;
    }

    /** Opens one rift, deep pit or powder-snow patch depending on the coin flip the design asks for. */
    void openOne(Location at, double radius) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        boolean snow = ThreadLocalRandom.current().nextBoolean();
        int ir = (int) Math.ceil(radius);
        double r2 = radius * radius;
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                if (x * x + z * z > r2) {
                    continue;
                }
                Block block = world.getBlockAt(at.getBlockX() + x, at.getBlockY(), at.getBlockZ() + z);
                if (block.getType().isAir() || block.getType() == Material.POWDER_SNOW) {
                    continue;
                }
                Material target = snow ? Material.POWDER_SNOW : Material.AIR;
                if (Grief.setBlock(fight.griefContext(), block, target) && target == Material.AIR) {
                    open.add(key(block.getX(), block.getZ()));
                }
            }
        }
        opened++;
        Fx.coloredBurst(at.clone().add(0, 1, 0), WorldenderFight.VOID_PURPLE, 2.4f, 60, 0.9);
        Fx.burst(at.clone().add(0, 1, 0), snow ? Particle.SNOWFLAKE : Particle.SQUID_INK, 30, 0.7);
        Fx.sound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.1f, 0.6f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        Block target = event.getBlock();
        long key = key(target.getX(), target.getZ());
        if (!open.remove(key)) {
            return;
        }
        event.setCancelled(true);
        if (!Grief.setMechanicBlock(fight.griefContext(), target, Material.STONE)) {
            open.add(key);
            return;
        }
        fight.instance().recordProgress();
        fight.vibration().registerLoud(event.getPlayer(), fight.config().dbl("unmooring-pour-loudness", 1.2));
        consumeBucket(event);

        Location at = target.getLocation().add(0.5, 0.5, 0.5);
        Fx.burst(at, Particle.CLOUD, 24, 0.4);
        Fx.sound(at, Sound.BLOCK_STONE_PLACE, 1.1f, 1.0f);
    }

    private void consumeBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack empty = new ItemStack(Material.BUCKET);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(empty);
        } else {
            player.getInventory().setItemInMainHand(empty);
        }
    }

    void discardAll() {
        open.clear();
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
    }

    private static long key(int x, int z) {
        return ((long) (x & 0x3FFFFFFF) << 32) | (z & 0xFFFFFFFFL);
    }
}
