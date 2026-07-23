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

/** Enrage finisher: the sky itself burns — a sustained meteor barrage rains across the whole arena. */
public final class FirestormAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);

    private final double damage;
    private final double height;
    private final float impactPower;
    private final int fireTicks;
    private final int barrageTicks;
    private final int dropInterval;
    private final int telegraphTicks;

    public FirestormAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.damage = configDouble("firestorm-damage", 10.0);
        this.height = configDouble("firestorm-height", 20.0);
        this.impactPower = (float) configDouble("firestorm-impact-power", 3.0);
        this.fireTicks = configInt("firestorm-fire-ticks", 80);
        this.barrageTicks = configInt("firestorm-barrage-ticks", 40);
        this.dropInterval = configInt("firestorm-drop-interval", 5);
        this.telegraphTicks = configInt("firestorm-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Firestorm";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("firestorm-cooldown-seconds", 24.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, arenaRadius * 0.9, Particle.FLAME);
                    Fx.coloredRing(ctx.bossLocation(), EMBER, 1.8f, 4.0, 48, 0);
                    Fx.point(ctx.bossLocation().clone().add(0, 3, 0), Particle.LAVA, 13);
                },
                () -> {
                    BossAudio.play(center, "boss.inferno_warlord.firestorm", Sound.ENTITY_GENERIC_EXPLODE, 1.7f, 0.4f);
                    Fx.sound(center, Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.6f);
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= barrageTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 5, 0),
                                    144, arenaRadius * 1.12, 4.8, arenaRadius * 1.12, 0.02);
                            // Previously every player in the arena was force-ignited on every drop, no
                            // matter where they stood — a guaranteed DoT nobody could dodge. Now only
                            // whoever a meteor actually lands near catches fire (Grief.throwBlock's own
                            // impact already gates damage on proximity to the landing spot).
                            if (ticks % dropInterval == 0) {
                                List<Player> players = ctx.arena().playersInside();
                                if (!players.isEmpty()) {
                                    Player victim = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                                    double ox = ThreadLocalRandom.current().nextDouble(-arenaRadius * 0.6, arenaRadius * 0.6);
                                    double oz = ThreadLocalRandom.current().nextDouble(-arenaRadius * 0.6, arenaRadius * 0.6);
                                    Location from = victim.getLocation().clone().add(ox, height, oz);
                                    Telegraph.dangerZone(victim.getLocation(), 3.0);
                                    Fx.trail(from, Particle.FLAME, 12, 0.4, 0.02);
                                    Grief.throwBlock(ctx, from, victim, Material.MAGMA_BLOCK, damage, impactPower);
                                    victim.setFireTicks(fireTicks);
                                }
                                Fx.sound(center, Sound.ENTITY_BLAZE_SHOOT, 1.3f, 0.6f);
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                16, onComplete);
    }
}
