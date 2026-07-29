package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * It stops being one target. Copies of the boss stand among it, it shuffles positions with them on a
 * clock, and swinging at a copy throws you somewhere else in the arena.
 * <p>
 * The interesting part is that this costs the group <em>tempo</em> rather than damage. The real boss
 * is fully hittable the entire time — nothing is gated, nothing is immune — but every second spent
 * hitting the wrong thing is a second of damage not dealt, and the blink punish means a mistake also
 * moves you out of range of the one you wanted. It is the only mechanic in the roster where the
 * failure state is "you were fooled" rather than "you were too slow" or "you stood in the bad place".
 * <p>
 * Copies are read by polling their health rather than by listening for hits, which keeps the whole
 * mechanic inside this one class. They cannot be killed — a copy that takes a swing simply mends and
 * punishes — so there is no way to grind them down instead of solving the puzzle.
 */
public final class DecoyMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    private static final double DECOY_HEALTH = 200.0;

    private final String label;
    private final Color color;
    private final EntityType decoyType;
    private final int decoyCount;
    private final int shuffleIntervalTicks;
    private final double blinkDistance;
    private final double blinkDamage;
    private final double spawnRadius;
    private final int refreshIntervalTicks;

    private final List<Decoy> decoys = new ArrayList<>();
    private int shuffleCountdown;
    private int refreshCountdown;
    private int foolCount;

    private static final class Decoy {
        final LivingEntity entity;
        double lastHealth;

        Decoy(LivingEntity entity) {
            this.entity = entity;
            this.lastHealth = entity.getHealth();
        }
    }

    public DecoyMechanic(BossInstance instance, String label, Color color, EntityType decoyType,
                          int decoyCount, int shuffleIntervalTicks, double blinkDistance,
                          double blinkDamage, double spawnRadius, int refreshIntervalTicks) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.decoyType = decoyType;
        this.decoyCount = Math.max(1, decoyCount);
        this.shuffleIntervalTicks = Math.max(40, shuffleIntervalTicks);
        this.blinkDistance = blinkDistance;
        this.blinkDamage = blinkDamage;
        this.spawnRadius = spawnRadius;
        this.refreshIntervalTicks = Math.max(100, refreshIntervalTicks);
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Only one of them is real — strike the wrong one and it moves you", NamedTextColor.GRAY));
        summon();
    }

    @Override
    protected void onStop() {
        clearDecoys();
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (stopped || damageDealt <= 0) {
            return;
        }
        // Finding the real one is the entire mechanic — landing on it is the engagement.
        instance.recordExposure();
    }

    @Override
    protected void tick() {
        decoys.removeIf(decoy -> !decoy.entity.isValid());

        pollDecoys();
        drawDecoys();

        shuffleCountdown -= TICK_INTERVAL;
        if (shuffleCountdown <= 0) {
            shuffleCountdown = shuffleIntervalTicks;
            shuffle();
        }

        // Strictly on the timer, never "whenever the list is empty" — the latter turns a failed spawn
        // into a re-summon attempt on every single pulse for the rest of the phase.
        refreshCountdown -= TICK_INTERVAL;
        if (refreshCountdown <= 0) {
            summon();
        }
        showBars();
    }

    /**
     * A copy that took a hit mends instantly and throws its attacker away. Polling health is how we
     * see the hit without a listener; healing it back is what stops the group grinding copies down
     * rather than reading them.
     */
    private void pollDecoys() {
        for (Decoy decoy : decoys) {
            if (!decoy.entity.isValid() || decoy.entity.isDead()) {
                continue;
            }
            double delta = decoy.lastHealth - decoy.entity.getHealth();
            if (delta > 0.01) {
                decoy.entity.setHealth(DECOY_HEALTH);
                punishNearest(decoy);
            }
            decoy.lastHealth = decoy.entity.getHealth();
        }
    }

    /**
     * Blames whoever is standing closest to the copy that was struck. Not perfect attribution, but
     * the alternative is a global damage listener for a purely cosmetic distinction, and in practice
     * the person in melee range of the copy is the person who swung at it.
     */
    private void punishNearest(Decoy decoy) {
        Player culprit = null;
        double best = 7.0;
        for (Player player : combatants()) {
            double dist = flatDistance(player.getLocation(), decoy.entity.getLocation());
            if (dist < best) {
                best = dist;
                culprit = player;
            }
        }
        Location at = decoy.entity.getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), color, 2.0f, 34, 0.6);
        Fx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.8f);
        foolCount++;

        if (culprit == null) {
            return;
        }
        blink(culprit);
    }

    /** Throws a player to a random reachable spot — disorienting, survivable, never out of the arena. */
    private void blink(Player player) {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double fraction = Math.min(0.85, blinkDistance / Math.max(1.0, instance.arena().radius()));
        Location destination = surfaceSpot(angle, Math.max(0.25, fraction));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 1.6f, 26, 0.5);
        player.teleport(destination);
        Fx.coloredBurst(destination.clone().add(0, 1, 0), color, 1.6f, 26, 0.5);
        Fx.sound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        hurt(player, blinkDamage);
        plugin.actionBarHub().flash(player,
                Component.text("A reflection — it threw you across the room", NamedTextColor.DARK_PURPLE),
                1600, ActionBarHub.PRIORITY_NOTICE);
    }

    private void drawDecoys() {
        if (elapsedTicks % 6 != 0) {
            return;
        }
        for (Decoy decoy : decoys) {
            if (decoy.entity.isValid()) {
                Fx.burst(decoy.entity.getLocation().add(0, 1.2, 0), Particle.PORTAL, 4, 0.4);
            }
        }
    }

    /**
     * The boss and its copies trade places. The real one is genuinely somewhere else afterwards,
     * which is what stops the group from simply marking it once and ignoring the mechanic.
     */
    private void shuffle() {
        if (decoys.isEmpty() || !instance.entity().isValid()) {
            return;
        }
        List<Location> spots = new ArrayList<>();
        spots.add(instance.entity().getLocation().clone());
        for (Decoy decoy : decoys) {
            if (decoy.entity.isValid()) {
                spots.add(decoy.entity.getLocation().clone());
            }
        }
        if (spots.size() < 2) {
            return;
        }
        java.util.Collections.shuffle(spots, ThreadLocalRandom.current());

        for (Location spot : spots) {
            Fx.coloredBurst(spot.clone().add(0, 1.2, 0), color, 1.8f, 24, 0.5);
        }
        instance.entity().teleport(spots.get(0));
        int index = 1;
        for (Decoy decoy : decoys) {
            if (decoy.entity.isValid() && index < spots.size()) {
                decoy.entity.teleport(spots.get(index++));
            }
        }
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 0.9f);
    }

    private void summon() {
        clearDecoys();
        refreshCountdown = refreshIntervalTicks;
        shuffleCountdown = shuffleIntervalTicks;
        Location centre = instance.entity().getLocation();
        for (int i = 0; i < decoyCount; i++) {
            double angle = 2 * Math.PI * i / decoyCount + ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
            Location spot = centre.clone().add(Math.cos(angle) * spawnRadius, 0, Math.sin(angle) * spawnRadius);
            Fx.coloredBurst(spot.clone().add(0, 1.2, 0), color, 1.8f, 26, 0.5);
            LivingEntity decoy = instance.addManager().spawn(spot.getWorld(), spot, decoyType, mob -> {
                mob.customName(instance.boss().displayName());
                mob.setCustomNameVisible(true);
                mob.setRemoveWhenFarAway(false);
                var attr = mob.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(DECOY_HEALTH);
                    mob.setHealth(DECOY_HEALTH);
                }
                var scale = mob.getAttribute(Attribute.SCALE);
                var bossScale = instance.entity().getAttribute(Attribute.SCALE);
                if (scale != null && bossScale != null) {
                    scale.setBaseValue(bossScale.getValue());
                }
                if (mob instanceof Mob m) {
                    // Copies chase but never land a blow — they are a puzzle, not extra incoming damage.
                    m.setTarget(combatants().stream().findFirst().orElse(null));
                }
                var damageAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
                if (damageAttr != null) {
                    damageAttr.setBaseValue(0.0);
                }
            });
            if (decoy != null) {
                decoys.add(new Decoy(decoy));
            }
        }
        // tick() re-summons whenever the list is empty, so a failed spawn would otherwise retry every
        // pulse for the rest of the phase.
        if (decoys.isEmpty()) {
            return;
        }
        Fx.sound(centre, Sound.ENTITY_ILLUSIONER_AMBIENT, 1.2f, 1.0f);
    }

    private void showBars() {
        Component text = Component.text(label + "  ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(decoys.size() + " reflection(s)", NamedTextColor.WHITE))
                .append(Component.text("   fooled " + foolCount + "×", NamedTextColor.GRAY))
                .append(Component.text("   shuffles in " + Math.max(0, shuffleCountdown / 20) + "s",
                        NamedTextColor.DARK_GRAY));
        instance.mechanicBar().updateShared(instance.barViewers(), text,
                1.0 - Math.max(0.0, shuffleCountdown / (double) shuffleIntervalTicks),
                BossBar.Color.PURPLE);
    }

    private void clearDecoys() {
        for (Decoy decoy : decoys) {
            if (decoy.entity.isValid()) {
                decoy.entity.remove();
            }
        }
        decoys.clear();
    }
}
