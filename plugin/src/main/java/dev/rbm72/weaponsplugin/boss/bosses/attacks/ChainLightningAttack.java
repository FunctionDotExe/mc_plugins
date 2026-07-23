package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bolt that strikes the target and arcs to the nearest players, dealing damage along a spark chain. */
public final class ChainLightningAttack extends BossAttack {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);
    private static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final double damage;
    private final int maxArcs;
    private final double arcRange;
    private final int telegraphTicks;

    public ChainLightningAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.damage = configDouble("chain-lightning-damage", 8.0);
        this.maxArcs = configInt("chain-lightning-max-arcs", 3);
        this.arcRange = configDouble("chain-lightning-arc-range", 6.0);
        this.telegraphTicks = configInt("chain-lightning-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Chain Lightning";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("chain-lightning-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Telegraph.line(origin.clone().add(0, 1.4, 0), ctx.target().getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK);
                    Fx.coloredRing(origin, STORM_YELLOW, 1.2f, 1.6, 26, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.storm_tyrant.chain_lightning", Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.3f, 1.3f);
                    Fx.sound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);
                    Fx.coloredBurst(origin.clone().add(0, 1.4, 0), STORM_WHITE, 2.0f, 40, 0.5);
                    Fx.coloredBurst(origin.clone().add(0, 1.4, 0), STORM_YELLOW, 1.5f, 20, 0.6);

                    List<Player> arena = ctx.arena().playersInside();
                    Set<UUID> hit = new HashSet<>();
                    Location prev = origin.clone().add(0, 1.4, 0);
                    Player current = ctx.target();
                    // Total chain = the target hit plus up to maxArcs jumps to the next nearest unhit player.
                    for (int i = 0; i <= maxArcs && current != null; i++) {
                        hit.add(current.getUniqueId());
                        Location currentLoc = current.getLocation().add(0, 1, 0);
                        Fx.line(prev, currentLoc, Particle.ELECTRIC_SPARK, 18);
                        Fx.coloredBurst(currentLoc, STORM_YELLOW, 1.4f, 19, 0.3);
                        if (currentLoc.getWorld() != null) {
                            currentLoc.getWorld().strikeLightningEffect(currentLoc);
                        }
                        current.damage(damage, ctx.boss());
                        Fx.bloodSpray(currentLoc);
                        Fx.sound(currentLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 1.6f);

                        Player next = null;
                        double bestDistSq = arcRange * arcRange;
                        for (Player candidate : arena) {
                            if (hit.contains(candidate.getUniqueId())) {
                                continue;
                            }
                            double distSq = candidate.getLocation().distanceSquared(current.getLocation());
                            if (distSq <= bestDistSq) {
                                bestDistSq = distSq;
                                next = candidate;
                            }
                        }
                        prev = currentLoc;
                        current = next;
                    }
                },
                12, onComplete);
    }
}
