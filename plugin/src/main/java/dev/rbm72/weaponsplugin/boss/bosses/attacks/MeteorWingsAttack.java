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

/** The elder wheels overhead, raining a flight of meteors down onto the arena one after another. */
public final class MeteorWingsAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 110, 0);

    private final double damage;
    private final double height;
    private final float impactPower;
    private final int fireTicks;
    private final int meteors;
    private final int dropInterval;
    private final int telegraphTicks;

    public MeteorWingsAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.damage = configDouble("meteor-wings-damage", 9.0);
        this.height = configDouble("meteor-wings-height", 18.0);
        this.impactPower = (float) configDouble("meteor-wings-impact-power", 2.5);
        this.fireTicks = configInt("meteor-wings-fire-ticks", 80);
        this.meteors = configInt("meteor-wings-meteors", 5);
        this.dropInterval = configInt("meteor-wings-drop-interval", 8);
        this.telegraphTicks = configInt("meteor-wings-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Meteor Wings";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("meteor-wings-cooldown-seconds", 14.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, arenaRadius * 0.85, Particle.FLAME);
                    Fx.coloredRing(ctx.bossLocation(), EMBER, 1.6f, 3.3, 38, 0);
                    Fx.point(ctx.bossLocation().clone().add(0, 2, 0), Particle.LAVA, 10);
                },
                () -> {
                    BossAudio.play(center, "boss.dragon_elder.meteor_wings", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, 0.6f);
                    Fx.sound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
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
                                    double ox = ThreadLocalRandom.current().nextDouble(-3.0, 3.0);
                                    double oz = ThreadLocalRandom.current().nextDouble(-3.0, 3.0);
                                    Location from = victim.getLocation().clone().add(ox, height, oz);
                                    Fx.trail(from, Particle.FLAME, 13, 0.4, 0.02);
                                    Fx.coloredBurst(from, EMBER, 1.4f, 14, 0.4);
                                    Grief.throwBlock(ctx, from, victim, Material.MAGMA_BLOCK, damage, impactPower);
                                    victim.setFireTicks(fireTicks);
                                    Fx.sound(from, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
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
