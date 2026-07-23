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

/** Encases the target in a ring of ice — heavy Slowness and a burst of shatter damage. */
public final class FrozenPrisonAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(150, 220, 255);

    private final double damage;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int ringColumns;
    private final int height;
    private final int durationTicks;
    private final int telegraphTicks;

    public FrozenPrisonAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damage = configDouble("frozen-prison-damage", 6.0);
        this.slowTicks = configInt("frozen-prison-slow-ticks", 40);
        this.slowAmplifier = configInt("frozen-prison-slow-amplifier", 4);
        this.ringColumns = configInt("frozen-prison-ring-columns", 8);
        this.height = configInt("frozen-prison-height", 3);
        this.durationTicks = configInt("frozen-prison-duration-ticks", 40);
        this.telegraphTicks = configInt("frozen-prison-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Frozen Prison";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("frozen-prison-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Location targetLoc = ctx.target().getLocation();
                    Telegraph.dangerZone(targetLoc, 1.5);
                    Fx.coloredRing(targetLoc, FROST, 1.6f, 1.6, 26, 0);
                    Telegraph.targetMarker(ctx.target());
                },
                () -> {
                    Player victim = ctx.target();
                    Location base = victim.getLocation().clone();
                    // A tight 1-wide ring of ice pillars around the target's feet.
                    Grief.raiseColumns(ctx, base, Material.ICE, height, ringColumns, 1.2, durationTicks);
                    victim.damage(damage, ctx.boss());
                    victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                    BossAudio.play(base, "boss.frost_queen.frozen_prison", Sound.BLOCK_GLASS_PLACE, 1.0f, 0.6f);
                    Fx.sound(base, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);
                    Fx.expandingRings(plugin, base, Particle.SNOWFLAKE, 2.8, 4, 3L);
                    Fx.coloredBurst(base.clone().add(0, 1.2, 0), FROST, 1.9f, 60, 0.7);
                    Fx.burst(base.clone().add(0, 1.2, 0), Particle.SNOWFLAKE, 30, 0.6);
                    base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0, 1, 0), 150, 1.6, 1.92, 1.6, 0.1,
                            Material.ICE.createBlockData());
                    Fx.bloodSpray(victim.getLocation().add(0, 1, 0));
                },
                14, onComplete);
    }
}
