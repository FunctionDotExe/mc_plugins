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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** A short storm of sun-fragments rains across the arena, each detonating where it lands. */
public final class SunfireRainAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 150, 40);

    private final double damage;
    private final double height;
    private final float impactPower;
    private final int meteors;
    private final int dropInterval;
    private final int telegraphTicks;

    public SunfireRainAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("sunfire-rain-damage", 12.0);
        this.height = configDouble("sunfire-rain-height", 22.0);
        this.impactPower = (float) configDouble("sunfire-rain-impact-power", 3.0);
        this.meteors = configInt("sunfire-rain-meteors", 5);
        this.dropInterval = configInt("sunfire-rain-drop-interval", 8);
        this.telegraphTicks = configInt("sunfire-rain-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Sunfire Rain";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("sunfire-rain-cooldown-seconds", 16.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, arenaRadius * 0.85, Particle.END_ROD);
                    Fx.coloredRing(ctx.bossLocation(), EMBER, 1.7f, 4.6, 42, 0);
                    Fx.point(ctx.bossLocation().clone().add(0, 3, 0), Particle.FLAME, 10);
                },
                () -> {
                    BossAudio.play(center, "boss.solar_colossus.sunfire_rain", Sound.ENTITY_GENERIC_EXPLODE, 1.45f, 0.5f);
                    Fx.sound(center, Sound.ENTITY_BLAZE_SHOOT, 1.15f, 0.6f);
                    new BukkitRunnable() {
                        int dropped = 0;
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (dropped >= meteors || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            if (ticks % dropInterval == 0) {
                                List<Player> players = ctx.arena().playersInside();
                                if (!players.isEmpty()) {
                                    Player victim = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                                    double ox = ThreadLocalRandom.current().nextDouble(-arenaRadius * 0.5, arenaRadius * 0.5);
                                    double oz = ThreadLocalRandom.current().nextDouble(-arenaRadius * 0.5, arenaRadius * 0.5);
                                    Location from = victim.getLocation().clone().add(ox, height, oz);
                                    Fx.trail(from, Particle.FLAME, 14, 0.4, 0.02);
                                    Fx.coloredBurst(from, EMBER, 1.5f, 20, 0.4);
                                    Fx.burst(from, Particle.FLAME, 14, 0.5);
                                    Grief.throwBlock(ctx, from, victim, Material.MAGMA_BLOCK, damage, impactPower);
                                    Fx.sound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.7f);
                                }
                                dropped++;
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                16, onComplete);
    }
}
