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
import org.bukkit.util.Vector;

/** An advancing wall of prismarine debris sweeps across the arena toward the target. */
public final class TsunamiAttack extends BossAttack {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);
    private static final Color DEEP_TEAL = Color.fromRGB(10, 90, 130);

    private final double damage;
    private final float impactPower;
    private final int wallWidth;
    private final double spacing;
    private final int telegraphTicks;

    public TsunamiAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damage = configDouble("tsunami-damage", 12.0);
        this.impactPower = (float) configDouble("tsunami-impact-power", 1.5);
        this.wallWidth = configInt("tsunami-wall-width", 5);
        this.spacing = configDouble("tsunami-spacing", 1.5);
        this.telegraphTicks = configInt("tsunami-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Tsunami";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("tsunami-cooldown-seconds", 15.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 1.2, 0);
        Vector dir = ctx.target().getLocation().toVector().subtract(origin.toVector()).setY(0);
        if (dir.lengthSquared() < 1.0e-4) {
            dir = ctx.boss().getLocation().getDirection().setY(0);
        }
        if (dir.lengthSquared() < 1.0e-4) {
            dir = new Vector(1, 0, 0);
        }
        final Vector direction = dir.normalize();
        final Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());

        sequence(telegraphTicks,
                () -> {
                    int half = wallWidth / 2;
                    Location left = origin.clone().add(perpendicular.clone().multiply(-half * spacing));
                    Location right = origin.clone().add(perpendicular.clone().multiply(half * spacing));
                    Telegraph.line(left, right, Particle.SPLASH);
                    Fx.coloredRing(origin, TEAL, 1.4f, 2.3, 30, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.tide_leviathan.tsunami", Sound.ITEM_TRIDENT_RIPTIDE_3, 1.35f, 0.4f);
                    Fx.sound(origin, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.25f, 0.5f);
                    int half = wallWidth / 2;
                    for (int i = -half; i <= half; i++) {
                        Location from = origin.clone().add(perpendicular.clone().multiply(i * spacing));
                        Fx.coloredBurst(from, DEEP_TEAL, 1.4f, 20, 0.4);
                        Fx.burst(from, Particle.SPLASH, 10, 0.4);
                        from.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, from, 72, 0.64, 1.6, 0.64, 0.05);
                        Grief.throwBlock(ctx, from, ctx.target(), Material.PRISMARINE, damage, impactPower);
                    }
                },
                14, onComplete);
    }
}
