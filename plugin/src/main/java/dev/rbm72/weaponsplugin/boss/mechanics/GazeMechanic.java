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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Heads hang around the arena and take turns singing. Whoever is looking at one when it sings takes
 * the verse full in the face; everyone facing anywhere else is fine.
 * <p>
 * This is the only mechanic in the game that reads the player's <em>camera</em>, and that is exactly
 * why it earns a phase. Every other demand in the roster is about position, timing or target
 * priority — things a player solves with their feet and their cooldowns. Being told to look away from
 * something forces them to give up the one thing they never otherwise surrender in a boss fight:
 * sight of the boss. Fighting blind for two seconds at a time, repeatedly, is a genuinely different
 * skill from anything else here.
 * <p>
 * Each head telegraphs loudly before it sings — it brightens, it hums, and the ring under it closes —
 * so there is always time to turn away, and turning away is always enough. Nothing is gated: the boss
 * remains hittable throughout, and a group that simply eats every verse survives a while and then
 * does not.
 */
public final class GazeMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    /**
     * How closely a player must be facing a head to count as looking at it. 0.55 is roughly a 57°
     * half-angle — wide enough that "glance away" is unambiguous, narrow enough that a player fighting
     * something else nearby is not punished for peripheral bad luck.
     */
    private static final double GAZE_DOT_THRESHOLD = 0.55;
    /**
     * Prop lifetime. Long enough to outlast any realistic phase, finite so a leaked display always
     * eventually cleans itself up even if teardown somehow never runs.
     */
    private static final int PROP_LIFETIME_TICKS = 20 * 60 * 30;

    private final String label;
    private final Color color;
    private final Material headMaterial;
    private final int headCount;
    private final int chargeTicks;
    private final int singTicks;
    private final int restTicks;
    private final double damage;
    private final double range;
    private final double placementFraction;

    private final List<Head> heads = new ArrayList<>();
    private int cycleTick;
    private int singingIndex = -1;
    private boolean verseResolved;

    private static final class Head {
        final Location at;
        Display prop;

        Head(Location at) {
            this.at = at;
        }
    }

    public GazeMechanic(BossInstance instance, String label, Color color, Material headMaterial,
                         int headCount, int chargeTicks, int singTicks, int restTicks,
                         double damage, double range, double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.headMaterial = headMaterial;
        this.headCount = Math.max(1, headCount);
        this.chargeTicks = Math.max(20, chargeTicks);
        this.singTicks = Math.max(4, singTicks);
        this.restTicks = Math.max(10, restTicks);
        this.damage = damage;
        this.range = range;
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("When one sings, look away from it", NamedTextColor.GRAY));
        raiseHeads();
        beginVerse();
    }

    @Override
    protected void onStop() {
        clearHeads();
    }

    @Override
    protected void tick() {
        if (heads.isEmpty()) {
            raiseHeads();
            return;
        }
        cycleTick += TICK_INTERVAL;

        if (singingIndex < 0 || singingIndex >= heads.size()) {
            beginVerse();
            return;
        }
        Head singer = heads.get(singingIndex);

        if (cycleTick < chargeTicks) {
            double urgency = cycleTick / (double) chargeTicks;
            Fx.coloredRing(singer.at.clone().add(0, 1.6, 0), color, (float) (1.0 + urgency),
                    3.0 * (1.0 - urgency) + 0.6, 20, elapsedTicks * 0.2);
            if (cycleTick % 10 < TICK_INTERVAL) {
                Fx.sound(singer.at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.6f + 1.2f * (float) urgency);
            }
            showBars(singer, false);
            return;
        }

        if (cycleTick < chargeTicks + singTicks) {
            if (!verseResolved) {
                verseResolved = true;
                sing(singer);
            }
            Fx.coloredBurst(singer.at.clone().add(0, 1.8, 0), color, 2.4f, 40, 0.8);
            showBars(singer, true);
            return;
        }

        if (cycleTick >= chargeTicks + singTicks + restTicks) {
            beginVerse();
        }
        showBars(singer, false);
    }

    private void beginVerse() {
        cycleTick = 0;
        verseResolved = false;
        if (heads.isEmpty()) {
            singingIndex = -1;
            return;
        }
        // Never the same head twice running, so the group cannot settle into facing one direction.
        int next = java.util.concurrent.ThreadLocalRandom.current().nextInt(heads.size());
        if (heads.size() > 1 && next == singingIndex) {
            next = (next + 1) % heads.size();
        }
        singingIndex = next;
        Fx.sound(heads.get(singingIndex).at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 0.7f);
    }

    /** Resolves one verse: everyone in range and facing the singer eats it. */
    private void sing(Head singer) {
        Location source = singer.at.clone().add(0, 1.6, 0);
        Fx.sound(source, Sound.ENTITY_VEX_CHARGE, 1.6f, 0.7f);
        Fx.burst(source, Particle.SONIC_BOOM, 2, 0.2);

        boolean anyoneLooked = false;
        for (Player player : combatants()) {
            if (flatDistance(player.getLocation(), singer.at) > range) {
                continue;
            }
            if (!isLookingAt(player, source)) {
                continue;
            }
            anyoneLooked = true;
            hurt(player, damage);
            if (player.isValid() && !player.isDead()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                Fx.coloredBurst(player.getLocation().add(0, 1.4, 0), color, 1.8f, 26, 0.5);
                Fx.sound(player.getLocation(), Sound.ENTITY_VEX_HURT, 1.2f, 0.8f);
            }
        }
        if (!anyoneLooked) {
            // The whole group turned away in time — that is the phase being played correctly.
            instance.recordExposure();
        }
    }

    private boolean isLookingAt(Player player, Location target) {
        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null || target.getWorld() == null || !eye.getWorld().equals(target.getWorld())) {
            return false;
        }
        Vector toTarget = target.toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 0.0001) {
            return true;
        }
        return eye.getDirection().normalize().dot(toTarget.normalize()) >= GAZE_DOT_THRESHOLD;
    }

    private void showBars(Head singer, boolean singing) {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean inRange = flatDistance(viewer.getLocation(), singer.at) <= range;
            boolean looking = inRange && isLookingAt(viewer, singer.at.clone().add(0, 1.6, 0));
            double charge = Math.min(1.0, cycleTick / (double) chargeTicks);
            Component text = Component.text(label + "  ", NamedTextColor.LIGHT_PURPLE)
                    .append(singing
                            ? Component.text("SINGING", NamedTextColor.RED)
                            : looking
                                    ? Component.text("LOOK AWAY — it is winding up", NamedTextColor.RED)
                                    : Component.text("eyes off it", NamedTextColor.GREEN));
            return MechanicBar.Readout.of(text, charge,
                    looking ? BossBar.Color.RED : BossBar.Color.PURPLE);
        });
    }

    private void raiseHeads() {
        clearHeads();
        double startAngle = java.util.concurrent.ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < headCount; i++) {
            double angle = startAngle + 2 * Math.PI * i / headCount;
            Location spot = surfaceSpot(angle, placementFraction);
            Head head = new Head(spot);
            head.prop = Fx.glowPillar(plugin, spot.clone().add(0, 1.2, 0), headMaterial, 0.7f, 0.7f, PROP_LIFETIME_TICKS);
            if (head.prop != null) {
                instance.trackEntity(head.prop);
            }
            heads.add(head);
            Fx.coloredBurst(spot.clone().add(0, 1.6, 0), color, 1.6f, 22, 0.4);
        }
    }

    private void clearHeads() {
        for (Head head : heads) {
            if (head.prop != null && head.prop.isValid()) {
                head.prop.remove();
            }
        }
        heads.clear();
        singingIndex = -1;
    }
}
