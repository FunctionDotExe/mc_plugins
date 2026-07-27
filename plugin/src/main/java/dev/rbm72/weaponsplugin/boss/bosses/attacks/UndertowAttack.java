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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The Tide Leviathan's signature: it doesn't hide behind wards, it drags the whole arena into its
 * pull. It sounds and submerges, and a maelstrom opens where it dove — every player nearby is hauled
 * steadily toward the deadly eye at its center, harder the longer it churns. Fight the current and
 * keep clear of the eye when it collapses and the Leviathan breaches, cracked wide open; get sucked
 * in and the collapse crushes you. A swim-against-the-drag check, not another totem ring.
 */
public final class UndertowAttack extends BossAttack {

    private static final Color DEEP_BLUE = Color.fromRGB(30, 90, 200);
    private static final Color FOAM = Color.fromRGB(180, 220, 255);
    private static final Color EYE_RED = Color.fromRGB(200, 40, 40);

    private final int telegraphTicks;
    private final int durationTicks;
    private final double basePull;
    private final double pullRampPerTick;
    private final double eyeRadius;
    private final double eyeTickDamage;
    private final double collapseDamage;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public UndertowAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.telegraphTicks = configInt("undertow-telegraph-ticks", 30);
        this.durationTicks = configInt("undertow-duration-ticks", 120);
        this.basePull = configDouble("undertow-base-pull", 0.18);
        this.pullRampPerTick = configDouble("undertow-pull-ramp", 0.0015);
        this.eyeRadius = configDouble("undertow-eye-radius", 3.5);
        this.eyeTickDamage = configDouble("undertow-eye-tick-damage", 3.0);
        this.collapseDamage = configDouble("undertow-collapse-damage", 20.0);
        this.exposedStaggerTicks = configInt("undertow-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("undertow-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("undertow-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "The Undertow";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("undertow-cooldown-seconds", 46.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), DEEP_BLUE, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.ENTITY_DOLPHIN_DEATH, 0.7f, 0.6f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    // Eye is pinned where it dove, not tracked to the boss — a maelstrom has a fixed
                    // center you swim away from; a moving one would just be an aura, not a current.
                    Location eye = ctx.bossLocation().clone();
                    double radius = ctx.arena().radius();

                    ctx.instance().showTitle(
                            Component.text("THE UNDERTOW", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                            Component.text("Swim clear of the eye — the pull only grows", NamedTextColor.GRAY));
                    BossAudio.play(eye, "boss.undertow", Sound.WEATHER_RAIN_ABOVE, 1.0f, 0.6f);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                collapse();
                                cancel();
                                return;
                            }
                            double pull = basePull + ticks * pullRampPerTick;
                            renderSwirl(pull);
                            double eyeSq = eyeRadius * eyeRadius;
                            for (Player player : Arena.combatants(eye, radius)) {
                                Vector toEye = eye.toVector().subtract(player.getLocation().toVector());
                                toEye.setY(0);
                                double dist = toEye.length();
                                if (dist < 1.0E-4) {
                                    continue;
                                }
                                // Constant inward tug regardless of distance — the fight is holding
                                // ground against it, and it bites harder as the churn ramps up.
                                Vector drag = toEye.multiply(pull / dist);
                                player.setVelocity(player.getVelocity().add(drag));
                                if (player.getLocation().distanceSquared(eye) <= eyeSq) {
                                    player.damage(eyeTickDamage, ctx.boss());
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), EYE_RED, 1.2f, 8, 0.3);
                                }
                            }
                            ticks++;
                        }

                        private void renderSwirl(double pull) {
                            Fx.coloredRing(eye, DEEP_BLUE, 1.2f, eyeRadius, 20, 0);
                            Fx.coloredRing(eye, FOAM, 1.0f, Math.min(radius, eyeRadius + 4 + pull * 20), 28, 0);
                            Fx.coloredBurst(eye.clone().add(0, 0.3, 0), EYE_RED, 1.2f, 6, 0.2);
                            Fx.sound(eye, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8f, 0.7f);
                        }

                        private void collapse() {
                            ctx.instance().setForcedInvulnerable(false);
                            boolean anyCaught = false;
                            double eyeSq = eyeRadius * eyeRadius;
                            for (Player player : Arena.combatants(eye, radius)) {
                                if (player.getLocation().distanceSquared(eye) <= eyeSq) {
                                    anyCaught = true;
                                    player.damage(collapseDamage, ctx.boss());
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), EYE_RED, 2.0f, 40, 0.6);
                                }
                            }
                            Fx.coloredBurst(eye.clone().add(0, 1, 0), FOAM, 2.4f, 50, 0.8);
                            Fx.burst(eye.clone().add(0, 1, 0), Particle.SPLASH, 60, 1.2);
                            Fx.sound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);

                            if (!anyCaught) {
                                Location loc = ctx.bossLocation();
                                ctx.instance().recordExposure();
                                ctx.instance().setDamageMultiplier(exposedMultiplier);
                                ctx.instance().stagger(exposedStaggerTicks);
                                ctx.instance().entity().setGlowing(true);
                                Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DEEP_BLUE, 2.2f, 50, 0.8);
                                Fx.flash(loc.clone().add(0, 1.2, 0), 2);
                                Fx.sound(loc, Sound.ENTITY_ELDER_GUARDIAN_HURT, 1.2f, 0.9f);
                                ctx.instance().showTitle(
                                        Component.text("BREACHED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                        Component.text("It surfaces, spent from the churn", NamedTextColor.GRAY));
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
