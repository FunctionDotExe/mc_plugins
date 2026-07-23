package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Three quick successive strikes — punishes players who trade instead of dodging. */
public final class TripleComboAttack extends BossAttack {

    private final double damagePerHit;
    private final double range;
    private final int telegraphTicks;

    public TripleComboAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damagePerHit = configDouble("triple-combo-damage-per-hit", 5.0);
        this.range = configDouble("triple-combo-range", 3.0);
        this.telegraphTicks = configInt("triple-combo-telegraph-ticks", 10);
    }

    @Override
    public String name() {
        return "Triple Combo";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("triple-combo-cooldown-seconds", 6.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        float[] pitches = {0.9f, 1.05f, 1.3f};
        int[] hitCounter = {0};

        Runnable strike = () -> {
            int index = Math.min(hitCounter[0], 2);
            BossAudio.play(ctx.bossLocation(), "boss.fallen_king.combo_hit", Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.15f, pitches[index]);
            Telegraph.groundRing(ctx.bossLocation(), range, Particle.CRIT);
            Fx.coloredBurst(ctx.bossLocation().add(0, 1, 0), Color.fromRGB(220, 60 + index * 40, 20), 1.2f + index * 0.3f, 26, 0.6);
            if (index == 2) {
                Fx.burst(ctx.bossLocation().add(0, 1, 0), Particle.CRIT, 24, 0.6);
            }
            for (Entity nearby : ctx.boss().getNearbyEntities(range, range, range)) {
                if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                    target.damage(damagePerHit, ctx.boss());
                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                }
            }
            hitCounter[0]++;
        };

        sequence(telegraphTicks,
                () -> Telegraph.groundRing(ctx.bossLocation(), range, Particle.CRIT),
                () -> {
                    strike.run();
                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), strike, 8L);
                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), strike, 16L);
                },
                24, onComplete);
    }
}
