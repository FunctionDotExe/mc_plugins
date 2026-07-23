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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** A cone of blinding light erupts from the colossus, searing and blinding anyone caught in front of it. */
public final class SolarFlareAttack extends BossAttack {

    private static final Color RADIANT_WHITE = Color.fromRGB(255, 250, 220);

    private final double damage;
    private final double range;
    private final double coneDot;
    private final int blindTicks;
    private final int telegraphTicks;

    public SolarFlareAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("solar-flare-damage", 8.0);
        this.range = configDouble("solar-flare-range", 8.0);
        this.coneDot = configDouble("solar-flare-cone-dot", 0.4);
        this.blindTicks = configInt("solar-flare-blind-ticks", 40);
        this.telegraphTicks = configInt("solar-flare-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Solar Flare";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("solar-flare-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 1.2, 0);
        Vector dir = ctx.target().getLocation().toVector().subtract(ctx.bossLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 1.0e-4) {
            dir = ctx.boss().getLocation().getDirection().setY(0);
        }
        final Vector coneDir = dir.normalize();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.cone(origin, coneDir, 70.0, range, Particle.END_ROD);
                    Fx.coloredBurst(origin.clone().add(coneDir.clone().multiply(1.5)), RADIANT_WHITE, 1.4f, 20, 0.6);
                },
                () -> {
                    Telegraph.cone(origin, coneDir, 70.0, range, Particle.FLAME);
                    Fx.coloredBurst(origin.clone().add(coneDir.clone().multiply(2.0)), RADIANT_WHITE, 1.9f, 75, 1.1);
                    Fx.burst(origin.clone().add(coneDir.clone().multiply(2.0)), Particle.END_ROD, 26, 0.6);
                    Fx.flash(origin.clone().add(coneDir.clone().multiply(2.0)), 3);
                    BossAudio.play(origin, "boss.solar_colossus.solar_flare", Sound.BLOCK_BEACON_ACTIVATE, 1.35f, 1.4f);
                    Fx.sound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
                    for (Player player : ctx.arena().playersInside()) {
                        Vector toPlayer = player.getLocation().toVector().subtract(ctx.bossLocation().toVector()).setY(0);
                        if (toPlayer.lengthSquared() > range * range || toPlayer.lengthSquared() < 1.0e-4) {
                            continue;
                        }
                        if (coneDir.dot(toPlayer.normalize()) < coneDot) {
                            continue;
                        }
                        player.damage(damage, ctx.boss());
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                    }
                },
                14, onComplete);
    }
}
