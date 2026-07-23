package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** A line of spectral fangs bursts from the ground one after another, chasing straight at the target. */
public final class FangLineAttack extends BossAttack {

    private static final Color FANG_WHITE = Color.fromRGB(230, 230, 240);

    private final double damage;
    private final double hitRadius;
    private final int fangCount;
    private final double spacing;
    private final int telegraphTicks;

    public FangLineAttack(WeaponsPlugin plugin) {
        super(plugin, "hollow_choir");
        this.damage = configDouble("fang-line-damage", 6.0);
        this.hitRadius = configDouble("fang-line-hit-radius", 1.4);
        this.fangCount = configInt("fang-line-count", 6);
        this.spacing = configDouble("fang-line-spacing", 1.6);
        this.telegraphTicks = configInt("fang-line-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Fang Line";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("fang-line-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        Vector rawDirection = ctx.target().getLocation().toVector().subtract(origin.toVector());
        Vector direction = rawDirection.lengthSquared() > 1.0E-6 ? rawDirection.normalize() : new Vector(1, 0, 0);

        sequence(telegraphTicks,
                () -> {
                    for (int i = 1; i <= fangCount; i++) {
                        Fx.point(origin.clone().add(direction.clone().multiply(i * spacing)), Particle.CRIT, 2);
                    }
                },
                () -> {
                    BossAudio.play(origin, "boss.hollow_choir.fang_line", Sound.ENTITY_EVOKER_CAST_SPELL, 1.1f, 0.8f);
                    for (int i = 0; i < fangCount; i++) {
                        popFang(ctx, origin.clone().add(direction.clone().multiply((i + 1) * spacing)), i * 3L);
                    }
                },
                14, onComplete);
    }

    private void popFang(AttackContext ctx, Location spot, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!ctx.boss().isValid()) {
                    return;
                }
                Fx.coloredBurst(spot.clone().add(0, 0.3, 0), FANG_WHITE, 1.6f, 20, 0.3);
                Fx.sound(spot, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 1.0f);
                // A real evoker fang bursts from the ground here, not a particle standing in for one.
                EvokerFangs fangs = ctx.boss().getWorld().spawn(spot, EvokerFangs.class);
                fangs.setOwner(ctx.boss());
                for (Entity nearby : ctx.boss().getWorld().getNearbyEntities(spot, hitRadius, hitRadius, hitRadius)) {
                    if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                            && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                        target.damage(damage, ctx.boss());
                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                    }
                }
            }
        }.runTaskLater(plugin, delay);
    }
}
