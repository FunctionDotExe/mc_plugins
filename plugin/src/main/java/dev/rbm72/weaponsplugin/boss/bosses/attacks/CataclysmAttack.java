package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enrage finisher: the elder makes three thunderous strafing runs straight
 * across the arena, dragging a burning wake of fire and meteors behind it and
 * scorching everyone it passes over.
 */
public final class CataclysmAttack extends BossAttack {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);
    private static final Color EMBER = Color.fromRGB(255, 110, 0);

    private final double damage;
    private final double pathWidth;
    private final double strafeSpeed;
    private final int passes;
    private final int passTicks;
    private final int fireTicks;
    private final int meteorInterval;
    private final float meteorImpactPower;
    private final int telegraphTicks;

    public CataclysmAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.damage = configDouble("cataclysm-damage", 12.0);
        this.pathWidth = configDouble("cataclysm-path-width", 3.5);
        this.strafeSpeed = configDouble("cataclysm-strafe-speed", 1.2);
        this.passes = configInt("cataclysm-passes", 3);
        this.passTicks = configInt("cataclysm-pass-ticks", 30);
        this.fireTicks = configInt("cataclysm-fire-ticks", 100);
        this.meteorInterval = configInt("cataclysm-meteor-interval", 6);
        this.meteorImpactPower = (float) configDouble("cataclysm-meteor-impact-power", 2.5);
        this.telegraphTicks = configInt("cataclysm-telegraph-ticks", 26);
    }

    @Override
    public String name() {
        return "Cataclysm";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("cataclysm-cooldown-seconds", 22.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, arenaRadius * 0.9, Particle.FLAME);
                    Fx.coloredRing(ctx.bossLocation(), DRAGON_RED, 1.8f, 4.0, 48, 0);
                    Fx.point(ctx.bossLocation().clone().add(0, 2, 0), Particle.LAVA, 13);
                },
                () -> {
                    BossAudio.play(center, "boss.dragon_elder.cataclysm", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.4f);
                    Fx.sound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
                    LivingEntity boss = ctx.boss();
                    new BukkitRunnable() {
                        int pass = 0;
                        int passElapsed = 0;
                        Vector heading = new Vector(1, 0, 0);
                        Set<UUID> hitThisPass = new HashSet<>();

                        private void startPass() {
                            hitThisPass.clear();
                            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                            heading = new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize();
                            // Reposition to the upwind edge of the arena, a few blocks up, and dive across.
                            Location start = center.clone().add(heading.clone().multiply(-arenaRadius * 0.8)).add(0, 4, 0);
                            boss.teleport(start);
                            boss.setVelocity(heading.clone().multiply(strafeSpeed));
                            BossAudio.play(start, "boss.dragon_elder.cataclysm_pass", Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 0.5f);
                        }

                        @Override
                        public void run() {
                            if (!boss.isValid() || pass >= passes) {
                                cancel();
                                return;
                            }
                            if (passElapsed == 0) {
                                startPass();
                            }
                            boss.setVelocity(heading.clone().multiply(strafeSpeed));
                            Location loc = boss.getLocation();
                            Fx.trail(loc, Particle.FLAME, 20, 0.5, 0.03);
                            Fx.coloredBurst(loc, EMBER, 1.4f, 16, 0.5);
                            if (passElapsed % meteorInterval == 0) {
                                Location from = loc.clone().add(0, 14, 0);
                                Fx.trail(from, Particle.FLAME, 10, 0.4, 0.02);
                                Grief.throwBlock(ctx, from, ctx.target(), Material.MAGMA_BLOCK, damage, meteorImpactPower);
                            }
                            for (Player player : ctx.arena().playersInside()) {
                                if (player.getLocation().distanceSquared(loc) <= pathWidth * pathWidth
                                        && hitThisPass.add(player.getUniqueId())) {
                                    player.damage(damage, boss);
                                    player.setFireTicks(fireTicks);
                                    Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), DRAGON_RED, 1.6f, 24, 0.5);
                                }
                            }
                            passElapsed++;
                            if (passElapsed >= passTicks) {
                                passElapsed = 0;
                                pass++;
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                20, onComplete);
    }
}
