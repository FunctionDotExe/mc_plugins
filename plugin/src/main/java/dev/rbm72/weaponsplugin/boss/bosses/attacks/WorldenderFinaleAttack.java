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
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The Unmaking finale: a sustained arena-wide barrage that cycles every signature grief move — meteors, lightning, void rifts, ice — while an aura chews through everyone still standing. */
public final class WorldenderFinaleAttack extends BossAttack {

    private static final Color UNMAKING = Color.fromRGB(200, 40, 90);
    private static final int AMBIENT_INTERVAL_TICKS = 6;

    private final double auraDamage;
    private final double meteorDamage;
    private final float meteorImpactPower;
    private final double lightningDamage;
    private final int durationTicks;
    private final int waveIntervalTicks;
    private final int telegraphTicks;

    public WorldenderFinaleAttack(WeaponsPlugin plugin) {
        super(plugin, "worldender");
        this.auraDamage = configDouble("finale-aura-damage", 20.0);
        this.meteorDamage = configDouble("finale-meteor-damage", 12.0);
        this.meteorImpactPower = (float) configDouble("finale-meteor-impact-power", 3.0);
        this.lightningDamage = configDouble("finale-lightning-damage", 14.0);
        this.durationTicks = configInt("finale-duration-ticks", 120);
        this.waveIntervalTicks = configInt("finale-wave-interval-ticks", 11);
        this.telegraphTicks = configInt("finale-telegraph-ticks", 30);
    }

    @Override
    public String name() {
        return "The Unmaking";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("finale-cooldown-seconds", 22.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, arenaRadius * 0.9, Particle.FLAME);
                    Fx.coloredRing(ctx.bossLocation(), UNMAKING, 1.6f, 3.0, 24, 0);
                    Fx.point(ctx.bossLocation().add(0, 2, 0), Particle.SOUL_FIRE_FLAME, 4);
                },
                () -> {
                    BossAudio.play(center, "boss.worldender.finale", Sound.ENTITY_WITHER_SPAWN, 1.6f, 0.5f);
                    Fx.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.6f);
                    startBarrage(ctx, center, arenaRadius, onComplete);
                },
                0, () -> { });
    }

    private void startBarrage(AttackContext ctx, Location center, double arenaRadius, Runnable onComplete) {
        World world = center.getWorld();
        new BukkitRunnable() {
            int ticks = 0;
            int wave = 0;

            @Override
            public void run() {
                if (ticks >= durationTicks || !ctx.boss().isValid() || world == null) {
                    cancel();
                    onComplete.run();
                    return;
                }
                // This hand-rolled loop bypasses BossAttack#sequence's exception guard, so it needs
                // its own: a throw from any wave (grief calls touching bad world state, a player
                // disconnecting mid-loop) must never skip ticks++/wave++ below, or this tick/wave
                // replays forever, ticks never reaches durationTicks, onComplete never fires, and
                // the boss is frozen for the rest of the fight.
                try {
                    runWave();
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "The Unmaking barrage threw mid-wave — recovering instead of freezing the boss.", e);
                }
                ticks++;
            }

            private void runWave() {
                // Ash/ember cloud over the whole footprint — every AMBIENT_INTERVAL ticks rather than
                // every single tick (was 210 raw particles/tick for the whole 6s duration, bypassing
                // Fx's scale entirely), and routed through Fx so /bossparticles actually affects it.
                if (ticks % AMBIENT_INTERVAL_TICKS == 0) {
                    Fx.burst(center.clone().add(0, 4, 0), Particle.LARGE_SMOKE, 40, arenaRadius * 0.6);
                    Fx.burst(center.clone().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, 16, arenaRadius * 0.5);
                }

                if (ticks % waveIntervalTicks == 0) {
                    // Damage aura to everyone still in the arena.
                    for (Player player : ctx.arena().playersInside()) {
                        tickHurt(ctx, player, auraDamage);
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), UNMAKING, 2.0f, 24, 0.6);
                        Fx.flash(player.getLocation().add(0, 1, 0), 2);
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                    }
                    Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
                    Fx.sound(center, Sound.ENTITY_WITHER_HURT, 1.2f, 0.5f);

                    switch (wave % 4) {
                        case 0 -> meteorWave(ctx, center, arenaRadius);
                        case 1 -> lightningWave(ctx, world, center, arenaRadius);
                        case 2 -> riftWave(ctx, center, arenaRadius);
                        default -> iceWave(ctx, center, arenaRadius);
                    }
                    wave++;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void meteorWave(AttackContext ctx, Location center, double arenaRadius) {
        List<Player> players = ctx.arena().playersInside();
        for (int i = 0; i < 5; i++) {
            Location from;
            if (!players.isEmpty()) {
                Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                from = p.getLocation().clone().add(0, 20, 0);
                Grief.throwBlock(ctx, from, p, Material.MAGMA_BLOCK, meteorDamage, meteorImpactPower);
            } else {
                from = randomPoint(center, arenaRadius).add(0, 20, 0);
                Fx.burst(from, Particle.FLAME, 12, 0.4);
            }
        }
    }

    private void lightningWave(AttackContext ctx, World world, Location center, double arenaRadius) {
        for (int i = 0; i < 6; i++) {
            Location strike = randomPoint(center, arenaRadius);
            // Real bolt vs. cosmetic strike only changes the fire/block-ignition side effect — the
            // attack's own configured damage always applies so both modes hit identically (see
            // ThunderstrikeAttack/StormcallAttack for the same pattern).
            if (Grief.enabled(ctx)) {
                world.strikeLightning(strike);
            } else {
                world.strikeLightningEffect(strike);
            }
            for (Player player : ctx.arena().playersInside()) {
                if (player.getLocation().distanceSquared(strike) <= 9.0) {
                    player.damage(lightningDamage, ctx.boss());
                    Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                }
            }
            Fx.point(strike, Particle.ELECTRIC_SPARK, 20);
            Fx.coloredBurst(strike, UNMAKING, 1.4f, 12, 0.4);
        }
    }

    private void riftWave(AttackContext ctx, Location center, double arenaRadius) {
        for (int i = 0; i < 5; i++) {
            Location rift = randomPoint(center, arenaRadius);
            Fx.coloredBurst(rift.clone().add(0, 1, 0), UNMAKING, 2.2f, 32, 0.9);
            Fx.burst(rift.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 20, 0.6);
            Grief.breakCrater(ctx, rift, 2.5);
        }
    }

    private void iceWave(AttackContext ctx, Location center, double arenaRadius) {
        Location base = randomPoint(center, arenaRadius);
        Fx.coloredBurst(base.clone().add(0, 1, 0), Color.fromRGB(150, 230, 255), 1.8f, 24, 0.7);
        Grief.raiseColumns(ctx, base, Material.PACKED_ICE, 3, 4, 3.0, 80);
    }

    private static Location randomPoint(Location center, double radius) {
        double ox = ThreadLocalRandom.current().nextDouble(-radius, radius);
        double oz = ThreadLocalRandom.current().nextDouble(-radius, radius);
        return center.clone().add(ox, 0, oz);
    }
}
