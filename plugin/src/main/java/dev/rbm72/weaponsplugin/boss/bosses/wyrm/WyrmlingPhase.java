package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * <b>P1 — The Wyrmling.</b> Small, fast, and actively evasive — it will not stand and fight. Damage
 * barely registers ({@link #filterDamage}'s uncornered fraction) until the group traps it with no
 * escape line ({@link EvasiveRig#blockedFraction}), at which point it is briefly, fully damageable.
 * Exit requires three real corners, not just the health threshold (batch-4 §1.3) — a cornering that
 * lands no hit doesn't count, so the phase can't be cheesed by boxing it in and standing still.
 */
final class WyrmlingPhase extends WyrmPhaseMechanic {

    private static final int REQUIRED_CORNERS = 3;

    private int corneringsCompleted;
    private int cornerHoldTicks;
    private int heldTicksLeft;
    private boolean tookDamageThisHold;

    WyrmlingPhase(BossInstance instance, double exitFraction) {
        super(instance, exitFraction);
    }

    @Override
    protected void onArm() {
        fight.rig().start();
        corneringsCompleted = 0;
        cornerHoldTicks = 0;
        heldTicksLeft = 0;
        dropCorneringSupplies();
    }

    @Override
    protected void onDisarm() {
        fight.rig().discard();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        if (fight.rig().isHeld()) {
            heldTicksLeft -= intervalTicks;
            if (heldTicksLeft <= 0) {
                breakFree();
            }
            return;
        }
        boolean solo = fight.playerCount() <= 1;
        double checkDistance = fight.config().dbl("corner-check-distance", 3.0);
        double playerBlockRadius = fight.config().dbl("corner-player-radius", 2.2);
        double requiredFraction = solo ? fight.config().dbl("corner-required-fraction-solo", 0.75)
                : fight.config().dbl("corner-required-fraction", 1.0);

        double blocked = fight.rig().blockedFraction(checkDistance, playerBlockRadius);
        if (blocked >= requiredFraction) {
            cornerHoldTicks += intervalTicks;
            if (cornerHoldTicks >= fight.config().num("corner-hold-ticks", 15)) {
                corner();
            }
        } else {
            cornerHoldTicks = 0;
        }
    }

    private void corner() {
        fight.rig().hold();
        tookDamageThisHold = false;
        heldTicksLeft = fight.config().num("corner-window-ticks", 80);
        Location at = instance.entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1, 0), WyrmFight.VOID_PURPLE, 2.0f, 40, 0.7);
        Fx.sound(at, Sound.ENTITY_ENDER_DRAGON_HURT, 1.2f, 1.3f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("CORNERED — hit it now", NamedTextColor.LIGHT_PURPLE),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void breakFree() {
        fight.rig().release();
        Location at = instance.entity().getLocation();
        if (tookDamageThisHold) {
            corneringsCompleted++;
            Fx.coloredBurst(at.clone().add(0, 1, 0), WyrmFight.STARLIGHT, 1.6f, 30, 0.6);
            Fx.sound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.4f);
            if (corneringsCompleted < REQUIRED_CORNERS) {
                instance.showTitle(Component.empty(),
                        Component.text("Cornered " + corneringsCompleted + "/" + REQUIRED_CORNERS + " — it slips free", NamedTextColor.GRAY));
            }
        } else {
            Fx.burst(at.clone().add(0, 1, 0), Particle.CLOUD, 20, 0.5);
        }
        Fx.sound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 1.6f);
    }

    @Override
    public double filterDamage(Player attacker, double damage) {
        if (fight.rig().isHeld()) {
            return damage;
        }
        return damage * fight.config().dbl("uncornered-damage-fraction", 0.05);
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (fight.rig().isHeld() && damageDealt > 0) {
            tookDamageThisHold = true;
        }
    }

    @Override
    protected boolean objectiveMet() {
        return corneringsCompleted >= REQUIRED_CORNERS;
    }

    @Override
    protected int progressSignal() {
        return corneringsCompleted * 1000 + cornerHoldTicks;
    }

    /** §0.3: cornering tools the group needs to close escape lines — real, arena-supplied blocks. */
    private void dropCorneringSupplies() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        boolean solo = fight.playerCount() <= 1;
        int perPlayer = fight.config().num(solo ? "supply-blocks-per-player-solo" : "supply-blocks-per-player", solo ? 24 : 12);
        Location at = instance.arena().center().add(0, 1, 0);
        int amount = perPlayer * fight.playerCount();
        int max = Material.COBBLESTONE.getMaxStackSize();
        while (amount > 0) {
            int size = Math.min(amount, max);
            amount -= size;
            Item item = world.dropItem(at, new ItemStack(Material.COBBLESTONE, size));
            item.setPersistent(false);
            instance.trackEntity(item);
        }
        Fx.burst(at, Particle.CLOUD, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }
}
