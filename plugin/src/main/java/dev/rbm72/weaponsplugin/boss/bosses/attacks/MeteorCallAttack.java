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

/** Calls down a scatter of void-touched meteors around the target — end stone and obsidian raining from above. */
public final class MeteorCallAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(110, 20, 160);

    private final double damage;
    private final float impactPower;
    private final int meteors;
    private final double scatterRadius;
    private final int telegraphTicks;

    public MeteorCallAttack(WeaponsPlugin plugin) {
        super(plugin, "voidwyrm");
        this.damage = configDouble("meteor-call-damage", 8.0);
        this.impactPower = (float) configDouble("meteor-call-impact-power", 1.8);
        this.meteors = configInt("meteor-call-count", 5);
        this.scatterRadius = configDouble("meteor-call-scatter-radius", 4.5);
        this.telegraphTicks = configInt("meteor-call-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Meteor Call";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("meteor-call-cooldown-seconds", 13.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.target().getLocation().clone();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(center, scatterRadius);
                    Fx.burst(center.clone().add(0, 6, 0), Particle.PORTAL, 8, 1.0);
                },
                () -> {
                    BossAudio.play(ctx.bossLocation(), "boss.voidwyrm.meteor_call", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.5f);
                    for (int i = 0; i < meteors; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double dist = Math.random() * scatterRadius;
                        Location from = center.clone().add(Math.cos(angle) * dist, 8.0, Math.sin(angle) * dist);
                        Fx.trail(from, Particle.PORTAL, 12, 0.3, 0.02);
                        Fx.coloredBurst(from, VOID_PURPLE, 1.2f, 10, 0.2);
                        Grief.throwBlock(ctx, from, ctx.target(), Material.END_STONE, damage, impactPower);
                    }
                },
                16, onComplete);
    }
}
