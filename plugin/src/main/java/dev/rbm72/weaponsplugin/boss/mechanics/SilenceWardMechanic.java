package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * A cantor plants a ward, and inside it everything you do lands soft. Kill the cantor and the ward
 * collapses; leave it standing and the boss simply fights inside its own bubble where you cannot hurt
 * it properly.
 * <p>
 * The tension is that the ward usually sits <em>on the boss</em>. So the group has three real
 * options at any moment — go kill the cantor, back out and fight from beyond the ward's edge, or
 * stay in and accept smothered damage — and which is correct depends on the group's range profile
 * and on where the cantor happened to plant it. That is a genuine standing choice rather than a
 * chore, and it never stops the fight or locks anyone out.
 * <p>
 * The ward blunts damage rather than blocking it, so a melee-only group is inconvenienced, never
 * walled. The cantor is a real, killable mob with modest health: solving it is always available, it
 * just costs the tempo of walking over there.
 */
public final class SilenceWardMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 4L;

    private final String label;
    private final Color color;
    private final Material markerMaterial;
    private final String cantorName;
    private final EntityType cantorType;
    private final double cantorHealth;
    private final double wardRadius;
    private final double smotheredMultiplier;
    private final int respawnDelayTicks;
    private final double placementFraction;

    private LivingEntity cantor;
    private Location wardCentre;
    private Display marker;
    private int respawnCountdown;
    private int collapses;

    public SilenceWardMechanic(BossInstance instance, String label, Color color, Material markerMaterial,
                                String cantorName, EntityType cantorType, double cantorHealth,
                                double wardRadius, double smotheredMultiplier, int respawnDelayTicks,
                                double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.markerMaterial = markerMaterial;
        this.cantorName = cantorName;
        this.cantorType = cantorType;
        this.cantorHealth = Math.max(4.0, cantorHealth);
        this.wardRadius = Math.max(3.0, wardRadius);
        this.smotheredMultiplier = Math.max(0.05, Math.min(1.0, smotheredMultiplier));
        this.respawnDelayTicks = Math.max(60, respawnDelayTicks);
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Your blows die inside the ward — silence the cantor", NamedTextColor.GRAY));
        plant();
    }

    @Override
    protected void onStop() {
        clearWard();
    }

    @Override
    public double filterDamage(Player attacker, double damage) {
        if (stopped || !wardStanding() || !insideWard(attacker)) {
            return damage;
        }
        if (instance.signalDeflectReady()) {
            plugin.actionBarHub().flash(attacker,
                    Component.text("The ward smothers your strike — get out of it, or kill the cantor",
                            NamedTextColor.LIGHT_PURPLE),
                    1200, ActionBarHub.PRIORITY_NOTICE);
        }
        return damage * smotheredMultiplier;
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (stopped || damageDealt <= 0 || attacker == null) {
            return;
        }
        // Landing a full-strength blow — from outside the ward, or with the cantor already dead — is
        // the phase being solved rather than endured.
        if (!wardStanding() || !insideWard(attacker)) {
            instance.recordExposure();
        }
    }

    @Override
    protected void tick() {
        if (!wardStanding()) {
            if (cantor != null) {
                collapse();
                return;
            }
            respawnCountdown -= TICK_INTERVAL;
            if (respawnCountdown <= 0) {
                plant();
            }
            showBars();
            return;
        }

        if (elapsedTicks % 4 == 0 && wardCentre != null) {
            Fx.coloredRing(wardCentre, color, 1.4f, wardRadius, 28, elapsedTicks * 0.05);
            Fx.coloredRing(wardCentre.clone().add(0, 2.0, 0), color, 1.1f, wardRadius * 0.7, 20,
                    -elapsedTicks * 0.07);
        }
        if (elapsedTicks % 10 == 0 && cantor != null && cantor.isValid()) {
            Fx.line(cantor.getLocation().add(0, 1.2, 0), wardCentre.clone().add(0, 0.5, 0), Particle.WITCH, 8);
        }
        showBars();
    }

    private boolean wardStanding() {
        return cantor != null && cantor.isValid() && !cantor.isDead() && wardCentre != null;
    }

    private boolean insideWard(Player player) {
        return wardCentre != null && flatDistance(player.getLocation(), wardCentre) <= wardRadius;
    }

    private void showBars() {
        boolean standing = wardStanding();
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            if (!standing) {
                Component text = Component.text(label + "  ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("silenced", NamedTextColor.GREEN))
                        .append(Component.text("   next ward in " + Math.max(0, respawnCountdown / 20) + "s",
                                NamedTextColor.GRAY));
                return MechanicBar.Readout.of(text, 0.0, BossBar.Color.GREEN);
            }
            boolean smothered = insideWard(viewer);
            double health = cantor.getHealth() / Math.max(1.0, cantorHealth);
            Component text = Component.text(label + "  ", NamedTextColor.LIGHT_PURPLE)
                    .append(smothered
                            ? Component.text("YOUR STRIKES ARE SMOTHERED", NamedTextColor.RED)
                            : Component.text("outside the ward — full strength", NamedTextColor.GREEN))
                    .append(Component.text("   cantor " + (int) Math.ceil(cantor.getHealth()) + " hp",
                            NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, health,
                    smothered ? BossBar.Color.RED : BossBar.Color.PURPLE);
        });
    }

    private void plant() {
        clearWard();
        // Anchored to the arena centre so the ward is always somewhere players can walk around, even
        // when the boss has been shoved against the wall by its own leash.
        double angle = java.util.concurrent.ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        wardCentre = surfaceSpot(angle, placementFraction);
        marker = Fx.glowPillar(plugin, wardCentre, markerMaterial, 0.5f, 3.0f, 20 * 60 * 30);
        if (marker != null) {
            instance.trackEntity(marker);
        }

        Location spawnAt = wardCentre.clone();
        cantor = instance.addManager().spawn(spawnAt.getWorld(), spawnAt, cantorType, mob -> {
            mob.customName(Component.text(cantorName, NamedTextColor.LIGHT_PURPLE));
            mob.setCustomNameVisible(true);
            mob.setRemoveWhenFarAway(false);
            var attr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(cantorHealth);
                mob.setHealth(cantorHealth);
            }
            if (mob instanceof Mob m) {
                // It holds the ward rather than chasing — the group has to come to it.
                m.setTarget(null);
            }
        });

        Fx.coloredBurst(wardCentre.clone().add(0, 1.2, 0), color, 2.0f, 40, 0.7);
        Fx.sound(wardCentre, Sound.ENTITY_EVOKER_CAST_SPELL, 1.3f, 0.8f);
        instance.showTitle(
                Component.text(label, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("A new ward — find the cantor", NamedTextColor.GRAY));
    }

    private void collapse() {
        collapses++;
        Location at = wardCentre != null ? wardCentre.clone() : instance.entity().getLocation();
        clearWard();
        instance.recordExposure();
        respawnCountdown = respawnDelayTicks;

        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.4f, 60, 0.9);
        Fx.sound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.4f, 0.9f);
        instance.showTitle(
                Component.text("WARD COLLAPSED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Everything lands again", NamedTextColor.GRAY));
    }

    private void clearWard() {
        if (cantor != null && cantor.isValid()) {
            cantor.remove();
        }
        cantor = null;
        if (marker != null && marker.isValid()) {
            marker.remove();
        }
        marker = null;
        wardCentre = null;
    }
}
