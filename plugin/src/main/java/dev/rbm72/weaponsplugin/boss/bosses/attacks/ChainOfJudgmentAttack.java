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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Storm Tyrant's signature: it doesn't hide behind wards, it punishes standing together.
 * It goes untouchable and repeatedly strikes a random player with a bolt that chains to whoever's
 * nearest them — clump up and everyone in the chain eats it, spread out and each strike only ever
 * finds one target. Ride out the whole barrage with the chain rarely finding a second link and it's
 * left staggered wide open. A positioning check, not another totem ring.
 */
public final class ChainOfJudgmentAttack extends BossAttack {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);

    private final int telegraphTicks;
    private final int durationTicks;
    private final int strikeIntervalTicks;
    private final double chainRadius;
    private final double strikeDamage;
    private final int chainFailThreshold;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public ChainOfJudgmentAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.telegraphTicks = configInt("chain-of-judgment-telegraph-ticks", 26);
        this.durationTicks = configInt("chain-of-judgment-duration-ticks", 100);
        this.strikeIntervalTicks = configInt("chain-of-judgment-strike-interval-ticks", 20);
        this.chainRadius = configDouble("chain-of-judgment-chain-radius", 5.0);
        this.strikeDamage = configDouble("chain-of-judgment-strike-damage", 7.0);
        this.chainFailThreshold = configInt("chain-of-judgment-fail-threshold", 2);
        this.exposedStaggerTicks = configInt("chain-of-judgment-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("chain-of-judgment-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("chain-of-judgment-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "Chain of Judgment";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("chain-of-judgment-cooldown-seconds", 46.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), STORM_YELLOW, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.8f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    Location bossLoc = ctx.bossLocation();
                    double radius = ctx.arena().radius();

                    ctx.instance().showTitle(
                            Component.text("CHAIN OF JUDGMENT", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                            Component.text("Spread out — the bolt chains to whoever's nearest", NamedTextColor.GRAY));
                    BossAudio.play(bossLoc, "boss.chain_of_judgment", Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.7f);

                    new BukkitRunnable() {
                        int ticks = 0;
                        int chainedStrikes = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                resolve(chainedStrikes);
                                cancel();
                                return;
                            }
                            if (ticks % strikeIntervalTicks == 0) {
                                strike();
                            }
                            ticks++;
                        }

                        private void strike() {
                            List<Player> nearby = Arena.combatants(bossLoc, radius);
                            if (nearby.isEmpty()) {
                                return;
                            }
                            Player primary = nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
                            zap(primary);

                            Player second = null;
                            double bestDistSq = chainRadius * chainRadius;
                            for (Player candidate : nearby) {
                                if (candidate.equals(primary)) {
                                    continue;
                                }
                                double distSq = candidate.getLocation().distanceSquared(primary.getLocation());
                                if (distSq <= bestDistSq) {
                                    bestDistSq = distSq;
                                    second = candidate;
                                }
                            }
                            if (second != null) {
                                Fx.line(primary.getLocation().add(0, 1, 0), second.getLocation().add(0, 1, 0), org.bukkit.Particle.ELECTRIC_SPARK, 18);
                                zap(second);
                                chainedStrikes++;
                            }
                        }

                        private void zap(Player player) {
                            player.damage(strikeDamage, ctx.boss());
                            Fx.coloredBurst(player.getLocation().add(0, 1, 0), STORM_YELLOW, 1.4f, 25, 0.4);
                            Fx.sound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.2f);
                            if (player.getWorld() != null) {
                                player.getWorld().strikeLightningEffect(player.getLocation());
                            }
                        }

                        private void resolve(int totalChained) {
                            ctx.instance().setForcedInvulnerable(false);
                            if (totalChained < chainFailThreshold) {
                                Location loc = ctx.bossLocation();
                                ctx.instance().recordExposure();
                                ctx.instance().setDamageMultiplier(exposedMultiplier);
                                ctx.instance().stagger(exposedStaggerTicks);
                                ctx.instance().entity().setGlowing(true);
                                Fx.coloredBurst(loc.clone().add(0, 1.2, 0), STORM_YELLOW, 2.2f, 50, 0.8);
                                Fx.flash(loc.clone().add(0, 1.2, 0), 2);
                                Fx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.2f);
                                ctx.instance().showTitle(
                                        Component.text("GROUNDED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                        Component.text("The storm turns on itself", NamedTextColor.GRAY));
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
