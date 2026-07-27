package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * P3's Frozen Heart: a real physical ice structure at arena centre, <b>immune to weapons outright</b>
 * (batch-1 §2.3/§2.6 — "there is no DPS answer") and solved only by carrying real fire to it. This is
 * the roster's one authorized instance of "boss gated while you deal with an objective" — see the memory
 * note on the anti-pattern — earned specifically because the Heart is a transport objective, not a
 * disguised second health bar: nothing about it responds to damage at all, weapon or otherwise.
 * <p>
 * Breaking is refused outright at the block level rather than merely left un-damageable by attacks,
 * because "immune to weapons" has to include a pickaxe as much as a sword. The only thing that reduces
 * its health is {@link #pulse}, driven by whoever is standing at it holding fire.
 */
final class FrozenHeart implements Listener {

    private static final org.bukkit.Color EMBER = org.bukkit.Color.fromRGB(255, 140, 60);

    private final FrostFight fight;
    private final List<Block> blocks = new ArrayList<>();
    private final List<Player> carriers = new ArrayList<>();

    private double hp;
    private double maxHp;
    private boolean built;
    private boolean destroyed;
    private boolean listening;

    FrozenHeart(FrostFight fight) {
        this.fight = fight;
    }

    boolean destroyed() {
        return destroyed;
    }

    double fraction() {
        return maxHp <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, hp / maxHp));
    }

    List<Player> carriers() {
        return List.copyOf(carriers);
    }

    /** Grows the physical shell at the heart spot. Idempotent — P3 may be entered only once per fight. */
    void build() {
        if (built) {
            return;
        }
        built = true;
        maxHp = fight.config().dbl("heart-max-hp", 60.0);
        hp = maxHp;
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location centre = fight.heartSpot();
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            for (int y = 0; y <= 1; y++) {
                Block block = world.getBlockAt(centre.getBlockX() + offset[0], centre.getBlockY() + y,
                        centre.getBlockZ() + offset[1]);
                Material material = (offset[0] == 0 && offset[1] == 0) ? Material.BLUE_ICE : Material.PACKED_ICE;
                if (Grief.setMechanicBlock(fight.griefContext(), block, material)) {
                    blocks.add(block);
                }
            }
        }
        if (!listening) {
            listening = true;
            fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
        }
        Fx.coloredBurst(centre.clone().add(0, 1.2, 0), FrostFight.FROST_BLUE, 2.6f, 70, 1.0);
        Fx.sound(centre, Sound.BLOCK_GLASS_PLACE, 1.4f, 0.5f);
    }

    /**
     * One step: fire-carriers standing at the shell burn it down, and it re-freezes the instant nobody
     * is. Batch-1 §2.3: "hesitation resets progress" — the regen is deliberately not slow.
     */
    void pulse(int intervalTicks) {
        if (destroyed || !built) {
            return;
        }
        double stepSeconds = intervalTicks / 20.0;
        double radius = fight.config().dbl("heart-carry-radius", 3.0);
        carriers.clear();
        for (Player player : fight.combatants()) {
            if (carryingFire(player) && withinRadius(player.getLocation(), radius)) {
                carriers.add(player);
                // Carrying fire suppresses Chill (batch-1 §2.3) — expressed as a strong cure rather than
                // a meter exemption, so the same "physical act, not a flag" rule the meter itself is
                // built on still holds for this one carve-out.
                fight.chill().cure(player, fight.config().dbl("heart-carrier-chill-cure", 30.0) * stepSeconds);
            }
        }
        Location centre = fight.heartSpot();
        if (!carriers.isEmpty()) {
            double drainPerSecond = fight.config().dbl("heart-drain-per-second", 6.0);
            hp = Math.max(0.0, hp - drainPerSecond * carriers.size() * stepSeconds);
            Fx.coloredBurst(centre.clone().add(0, 1.0, 0), EMBER, 1.6f, 10, 0.5);
            Fx.sound(centre, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.3f);
            if (hp <= 0) {
                destroy();
                return;
            }
        } else {
            double regenPerSecond = fight.config().dbl("heart-regen-per-second", 2.5);
            hp = Math.min(maxHp, hp + regenPerSecond * stepSeconds);
        }
        Fx.burst(centre.clone().add(0, 1.4, 0), Particle.SNOWFLAKE, 8, 0.4);
    }

    private boolean withinRadius(Location loc, double radius) {
        Location centre = fight.heartSpot();
        return loc.getWorld() != null && loc.getWorld().equals(centre.getWorld())
                && loc.distanceSquared(centre) <= radius * radius;
    }

    private static boolean carryingFire(Player player) {
        return isFireSource(player.getInventory().getItemInMainHand())
                || isFireSource(player.getInventory().getItemInOffHand());
    }

    private static boolean isFireSource(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Material type = stack.getType();
        return type == Material.TORCH || type == Material.FLINT_AND_STEEL || type == Material.BLAZE_ROD
                || type == Material.SOUL_TORCH;
    }

    private void destroy() {
        destroyed = true;
        Location centre = fight.heartSpot();
        for (Block block : blocks) {
            Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
        }
        blocks.clear();
        Fx.coloredBurst(centre.clone().add(0, 1.2, 0), FrostFight.PALE_ICE, 3.0f, 90, 1.2);
        Fx.burst(centre.clone().add(0, 1.2, 0), Particle.SNOWFLAKE, 60, 0.9);
        Fx.sound(centre, Sound.BLOCK_GLASS_BREAK, 1.6f, 0.6f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE HEART SHATTERS", NamedTextColor.WHITE),
                    2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocks.contains(event.getBlock())) {
            // Weapons — and pickaxes — do nothing to it at all. See the class header.
            event.setCancelled(true);
        }
    }

    void discard() {
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
        blocks.clear();
        carriers.clear();
    }
}
