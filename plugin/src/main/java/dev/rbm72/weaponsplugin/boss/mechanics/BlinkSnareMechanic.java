package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One player at a time is folded out of the fight into a bubble of void they cannot walk out of. The
 * only way back is a rift that circles the inside of the bubble — they have to catch it before the
 * bubble closes on them.
 * <p>
 * Every other "someone is in trouble" mechanic in the roster is solved by the rest of the group going
 * to them. This one deliberately cannot be: nobody else can reach the snared player, so the group's
 * job is to survive a member down while the snared player's job is a small, self-contained puzzle
 * under time pressure. It creates a brief, genuine understaffing every time it fires, which is a
 * different kind of pressure from anything else here and needs no coordination at all to resolve.
 * <p>
 * That also makes it perfectly solo-safe by construction: the puzzle never needed anyone else, so a
 * lone player faces exactly the same task a full group's victim does.
 */
public final class BlinkSnareMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    /** How close the snared player must get to the rift to be counted as through it. */
    private static final double RIFT_CATCH_RADIUS = 1.6;

    private final String label;
    private final Color color;
    private final double bubbleRadius;
    private final int snareTicks;
    private final int recastDelayTicks;
    private final double riftDegreesPerSecond;
    private final double failDamage;
    private final int failDisorientTicks;

    private Player snared;
    private Location bubbleCentre;
    private double riftAngle;
    private int snareLeft;
    private int recastCountdown;
    private int escapes;

    public BlinkSnareMechanic(BossInstance instance, String label, Color color, double bubbleRadius,
                               int snareTicks, int recastDelayTicks, double riftDegreesPerSecond,
                               double failDamage, int failDisorientTicks) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.bubbleRadius = Math.max(3.0, bubbleRadius);
        this.snareTicks = Math.max(60, snareTicks);
        this.recastDelayTicks = Math.max(40, recastDelayTicks);
        this.riftDegreesPerSecond = riftDegreesPerSecond;
        this.failDamage = failDamage;
        this.failDisorientTicks = failDisorientTicks;
    }

    @Override
    protected void onStart() {
        recastCountdown = 60;
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It folds you out of the room — find the way back yourself", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        release();
    }

    @Override
    protected void tick() {
        if (snared == null) {
            recastCountdown -= TICK_INTERVAL;
            if (recastCountdown <= 0) {
                snare();
            }
            showBars();
            return;
        }

        if (!snared.isOnline() || !snared.isValid() || snared.isDead()) {
            release();
            recastCountdown = recastDelayTicks;
            showBars();
            return;
        }

        snareLeft -= TICK_INTERVAL;
        riftAngle += riftDegreesPerSecond * (TICK_INTERVAL / 20.0);

        drawBubble();
        confine();

        if (caughtRift()) {
            escaped();
        } else if (snareLeft <= 0) {
            collapsed();
        }
        showBars();
    }

    private Location riftLocation() {
        double radians = Math.toRadians(riftAngle);
        Location at = bubbleCentre.clone().add(
                Math.cos(radians) * (bubbleRadius - 0.8), 0.6, Math.sin(radians) * (bubbleRadius - 0.8));
        org.bukkit.World world = at.getWorld();
        if (world != null) {
            at.setY(world.getHighestBlockYAt(at.getBlockX(), at.getBlockZ()) + 1.0);
        }
        return at;
    }

    private boolean caughtRift() {
        return flatDistance(snared.getLocation(), riftLocation()) <= RIFT_CATCH_RADIUS;
    }

    private void drawBubble() {
        Fx.coloredRing(bubbleCentre, color, 1.5f, bubbleRadius, 30, elapsedTicks * 0.05);
        Fx.coloredRing(bubbleCentre.clone().add(0, 1.6, 0), color, 1.2f, bubbleRadius * 0.8, 22,
                -elapsedTicks * 0.07);

        Location rift = riftLocation();
        Fx.coloredBurst(rift, Color.fromRGB(255, 255, 255), 1.6f, 8, 0.25);
        Fx.burst(rift, Particle.PORTAL, 8, 0.4);
        if (elapsedTicks % 10 == 0) {
            Fx.sound(rift, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 1.4f);
        }
    }

    /** Pushes the snared player back toward the middle whenever they reach the wall. */
    private void confine() {
        double dist = flatDistance(snared.getLocation(), bubbleCentre);
        if (dist <= bubbleRadius) {
            return;
        }
        Vector inward = bubbleCentre.toVector().subtract(snared.getLocation().toVector()).setY(0);
        if (inward.lengthSquared() < 0.001) {
            return;
        }
        snared.setVelocity(snared.getVelocity().add(inward.normalize().multiply(0.35)));
        Fx.coloredBurst(snared.getLocation().add(0, 1, 0), color, 1.2f, 8, 0.3);
    }

    private void snare() {
        List<Player> present = combatants();
        if (present.isEmpty()) {
            recastCountdown = recastDelayTicks;
            return;
        }
        snared = present.get(ThreadLocalRandom.current().nextInt(present.size()));
        bubbleCentre = snared.getLocation().clone();
        riftAngle = ThreadLocalRandom.current().nextDouble(0, 360);
        snareLeft = snareTicks;

        Fx.coloredBurst(bubbleCentre.clone().add(0, 1.2, 0), color, 2.4f, 50, 0.8);
        Fx.sound(bubbleCentre, Sound.BLOCK_PORTAL_TRIGGER, 1.3f, 0.7f);
        snared.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, snareTicks, 0, false, false));
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text(snared.getName() + " is folded out — hold the line without them",
                        NamedTextColor.GRAY));
    }

    private void escaped() {
        Player freed = snared;
        Location rift = riftLocation();
        release();
        escapes++;
        recastCountdown = recastDelayTicks;
        instance.recordExposure();

        Fx.coloredBurst(rift, color, 2.4f, 50, 0.7);
        Fx.sound(rift, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.3f, 1.1f);
        if (freed != null && freed.isValid()) {
            Fx.coloredBurst(freed.getLocation().add(0, 1, 0), color, 1.8f, 30, 0.5);
        }
    }

    private void collapsed() {
        Player victim = snared;
        Location at = bubbleCentre.clone();
        release();
        recastCountdown = recastDelayTicks;

        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.8f, 70, 1.0);
        Fx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, 0.5f);
        if (victim != null && victim.isValid() && !victim.isDead()) {
            hurt(victim, failDamage);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, failDisorientTicks, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, failDisorientTicks, 2));
        }
        instance.showTitle(
                Component.text("THE BUBBLE CLOSED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("They never found the way out", NamedTextColor.GRAY));
    }

    private void release() {
        if (snared != null && snared.isOnline()) {
            snared.removePotionEffect(PotionEffectType.DARKNESS);
        }
        snared = null;
        bubbleCentre = null;
        snareLeft = 0;
    }

    private void showBars() {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            if (snared == null) {
                Component idle = Component.text(label + "  ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text("next fold in " + Math.max(0, recastCountdown / 20) + "s",
                                NamedTextColor.GRAY))
                        .append(Component.text("   escaped " + escapes + "×", NamedTextColor.DARK_GRAY));
                return MechanicBar.Readout.of(idle, 0.0, BossBar.Color.WHITE);
            }
            double left = Math.max(0.0, snareLeft / (double) snareTicks);
            if (viewer.equals(snared)) {
                Component text = Component.text("SNARED  ", NamedTextColor.RED)
                        .append(Component.text("catch the rift", NamedTextColor.WHITE))
                        .append(Component.text("   " + Math.max(0, snareLeft / 20) + "s", NamedTextColor.GRAY));
                return MechanicBar.Readout.of(text, left, BossBar.Color.PURPLE);
            }
            Component text = Component.text(label + "  ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text(snared.getName() + " is folded out", NamedTextColor.WHITE))
                    .append(Component.text("   you cannot reach them", NamedTextColor.DARK_GRAY));
            return MechanicBar.Readout.of(text, left, BossBar.Color.PURPLE);
        });
    }
}
