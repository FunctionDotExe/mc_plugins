package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Void Sovereign's signature: it doesn't hide behind wards, it opens two rifts and lets you
 * guess wrong. One pulses a calm, steady light (safe); the other flickers a beat faster and hisses
 * instead of humming (deadly) — a real but learnable tell, not a coin flip. Shelter near the safe
 * rift when it collapses and the Sovereign is left staggered wide open; get caught near the deadly
 * one and it detonates on you. A discernment check, not another totem ring.
 */
public final class TwinRiftsAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);
    private static final Color SAFE_BLUE = Color.fromRGB(80, 180, 255);
    private static final Color DEADLY_RED = Color.fromRGB(200, 20, 20);

    private final int telegraphTicks;
    private final int durationTicks;
    private final double riftRadius;
    private final double deadlyDamage;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public TwinRiftsAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.telegraphTicks = configInt("twin-rifts-telegraph-ticks", 30);
        this.durationTicks = configInt("twin-rifts-duration-ticks", 90);
        this.riftRadius = configDouble("twin-rifts-radius", 3.0);
        this.deadlyDamage = configDouble("twin-rifts-deadly-damage", 18.0);
        this.exposedStaggerTicks = configInt("twin-rifts-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("twin-rifts-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("twin-rifts-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "Twin Rifts";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("twin-rifts-cooldown-seconds", 44.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), VOID_PURPLE, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.8f, 0.6f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    Location bossLoc = ctx.bossLocation();
                    double radius = ctx.arena().radius();

                    double angleA = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                    double angleB = angleA + Math.PI;
                    Location riftA = bossLoc.clone().add(Math.cos(angleA) * radius * 0.5, 0, Math.sin(angleA) * radius * 0.5);
                    Location riftB = bossLoc.clone().add(Math.cos(angleB) * radius * 0.5, 0, Math.sin(angleB) * radius * 0.5);
                    boolean aIsSafe = ThreadLocalRandom.current().nextBoolean();
                    Location safeRift = aIsSafe ? riftA : riftB;
                    Location deadlyRift = aIsSafe ? riftB : riftA;

                    ctx.instance().showTitle(
                            Component.text("TWIN RIFTS", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                            Component.text("One is calm, one hisses wrong — choose", NamedTextColor.GRAY));
                    BossAudio.play(bossLoc, "boss.twin_rifts", Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.6f);
                    // Real floating markers at each rift instead of particle rings alone.
                    Fx.spinningIcon(plugin, safeRift.clone().add(0, 1, 0), Material.ENDER_EYE, 0.6f, durationTicks + 10, 20.0);
                    Fx.spinningIcon(plugin, deadlyRift.clone().add(0, 1, 0), Material.CRYING_OBSIDIAN, 0.6f, durationTicks + 10, 20.0);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                resolve();
                                cancel();
                                return;
                            }
                            if (ticks % 4 == 0) {
                                Fx.coloredBurst(safeRift.clone().add(0, 1, 0), SAFE_BLUE, 1.0f, 6, 0.2);
                                Fx.sound(safeRift, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
                            }
                            if (ticks % 3 == 0) {
                                Fx.coloredBurst(deadlyRift.clone().add(0, 1, 0), DEADLY_RED, 1.0f, 6, 0.25);
                                Fx.sound(deadlyRift, Sound.ENTITY_ENDERMAN_SCREAM, 0.4f, 1.6f);
                            }
                            ticks++;
                        }

                        private void resolve() {
                            ctx.instance().setForcedInvulnerable(false);
                            Location loc = ctx.bossLocation();
                            var players = Arena.playersNear(loc, radius);
                            boolean anyCaughtNearDeadly = players.stream()
                                    .anyMatch(p -> p.getLocation().distanceSquared(deadlyRift) <= riftRadius * riftRadius);
                            for (Player player : players) {
                                if (player.getLocation().distanceSquared(deadlyRift) <= riftRadius * riftRadius) {
                                    player.damage(deadlyDamage, ctx.boss());
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), DEADLY_RED, 2.0f, 40, 0.6);
                                }
                            }
                            Fx.coloredBurst(deadlyRift.clone().add(0, 1, 0), DEADLY_RED, 2.2f, 40, 0.7);
                            Fx.burst(deadlyRift.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 30, 0.5);
                            Fx.sound(deadlyRift, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

                            if (!anyCaughtNearDeadly) {
                                ctx.instance().recordExposure();
                                ctx.instance().setDamageMultiplier(exposedMultiplier);
                                ctx.instance().stagger(exposedStaggerTicks);
                                ctx.instance().entity().setGlowing(true);
                                Fx.coloredBurst(loc.clone().add(0, 1.2, 0), VOID_PURPLE, 2.2f, 50, 0.8);
                                Fx.flash(loc.clone().add(0, 1.2, 0), 2);
                                ctx.instance().showTitle(
                                        Component.text("REALITY SLIPS", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                        Component.text("The Sovereign miscalculated", NamedTextColor.GRAY));
                                ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                                    if (ctx.boss().isValid()) {
                                        ctx.instance().entity().setGlowing(false);
                                        ctx.instance().setDamageMultiplier(1.0);
                                    }
                                }, exposedTicks);
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                durationTicks + 20, onComplete);
    }
}
