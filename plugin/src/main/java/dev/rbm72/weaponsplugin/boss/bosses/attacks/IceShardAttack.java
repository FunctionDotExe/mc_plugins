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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Freezes the surface and shatters it: jagged blue-ice shards fly at the target, chilling those they strike. */
public final class IceShardAttack extends BossAttack {

    private static final Color PALE_ICE = Color.fromRGB(180, 235, 255);

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final double slowRadius;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int telegraphTicks;

    public IceShardAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damage = configDouble("ice-shard-damage", 11.0);
        this.impactPower = (float) configDouble("ice-shard-impact-power", 1.0);
        this.projectiles = configInt("ice-shard-projectiles", 3);
        this.slowRadius = configDouble("ice-shard-slow-radius", 4.0);
        this.slowTicks = configInt("ice-shard-slow-ticks", 60);
        this.slowAmplifier = configInt("ice-shard-slow-amplifier", 1);
        this.telegraphTicks = configInt("ice-shard-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Ice Shard";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("ice-shard-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        Location targetLoc = ctx.target().getLocation().clone();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredRing(origin, PALE_ICE, 1.4f, 2.2, 32, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.tide_leviathan.ice_shard", Sound.BLOCK_GLASS_BREAK, 1.2f, 0.7f);
                    Fx.sound(origin, Sound.ITEM_TRIDENT_HIT_GROUND, 1.1f, 0.6f);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.SNOWFLAKE, 40, 0.5);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), PALE_ICE, 1.5f, 34, 0.5);
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.4, 0), ctx.target(), Material.BLUE_ICE, damage, impactPower);
                    }
                    for (Player player : ctx.arena().playersInside()) {
                        if (player.getLocation().distanceSquared(targetLoc) > slowRadius * slowRadius) {
                            continue;
                        }
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), PALE_ICE, 1.1f, 13, 0.3);
                    }
                },
                12, onComplete);
    }
}
