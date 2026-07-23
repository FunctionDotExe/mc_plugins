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

/** Aerial bombardment: the elder hurls a volley of molten blocks down onto the target from above. */
public final class FireballBarrageAttack extends BossAttack {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);
    private static final Color EMBER = Color.fromRGB(255, 110, 0);

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int telegraphTicks;

    public FireballBarrageAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.damage = configDouble("fireball-barrage-damage", 8.0);
        this.impactPower = (float) configDouble("fireball-barrage-impact-power", 2.0);
        this.projectiles = configInt("fireball-barrage-projectiles", 4);
        this.telegraphTicks = configInt("fireball-barrage-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Fireball Barrage";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("fireball-barrage-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredRing(origin, EMBER, 1.4f, 2.5, 32, 0);
                    Fx.point(origin.clone().add(0, 1, 0), Particle.LAVA, 6);
                },
                () -> {
                    BossAudio.play(origin, "boss.dragon_elder.fireball_barrage", Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 0.7f);
                    Fx.sound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.3f, 0.6f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), DRAGON_RED, 2.0f, 38, 0.6);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), EMBER, 1.5f, 22, 0.7);
                    for (int i = 0; i < projectiles; i++) {
                        Location from = origin.clone().add(
                                (Math.random() - 0.5) * 3.0, 3.0, (Math.random() - 0.5) * 3.0);
                        Fx.trail(from, Particle.FLAME, 12, 0.3, 0.02);
                        Grief.throwBlock(ctx, from, ctx.target(), Material.MAGMA_BLOCK, damage, impactPower);
                    }
                },
                12, onComplete);
    }
}
