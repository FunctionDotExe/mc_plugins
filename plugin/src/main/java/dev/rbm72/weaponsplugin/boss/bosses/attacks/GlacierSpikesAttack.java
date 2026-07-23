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

/** Signature grief: erupts a cluster of packed-ice spikes from the ground under the target. */
public final class GlacierSpikesAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(150, 220, 255);

    private final double damage;
    private final int height;
    private final int spikeCount;
    private final double spread;
    private final int durationTicks;
    private final int telegraphTicks;

    public GlacierSpikesAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damage = configDouble("glacier-spikes-damage", 8.0);
        this.height = configInt("glacier-spikes-height", 3);
        this.spikeCount = configInt("glacier-spikes-count", 4);
        this.spread = configDouble("glacier-spikes-spread", 2.5);
        this.durationTicks = configInt("glacier-spikes-duration-ticks", 100);
        this.telegraphTicks = configInt("glacier-spikes-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Glacier Spikes";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("glacier-spikes-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        double damageRadius = spread + 2.0;
        int[] tick = {0};
        sequence(telegraphTicks,
                () -> {
                    Location targetLoc = ctx.target().getLocation();
                    Telegraph.dangerZone(targetLoc, spread + 1.0, tick[0]++ / (double) telegraphTicks);
                    Fx.coloredRing(targetLoc, FROST, 1.5f, spread, 38, 0);
                    Telegraph.targetMarker(ctx.target());
                },
                () -> {
                    Location base = ctx.target().getLocation().clone();
                    Grief.raiseColumns(ctx, base, Material.PACKED_ICE, height, spikeCount, spread, durationTicks);
                    BossAudio.play(base, "boss.frost_queen.glacier_spikes", Sound.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
                    Fx.sound(base, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.7f);
                    Fx.expandingRings(plugin, base, Particle.SNOWFLAKE, spread + 1.0, 4, 3L);
                    Fx.coloredBurst(base.clone().add(0, 1.5, 0), FROST, 1.9f, 68, spread * 0.5);
                    Fx.burst(base.clone().add(0, 1.5, 0), Particle.SNOWFLAKE, 30, spread * 0.4);
                    base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0, 1, 0), 195, spread * 0.8, 1.6, spread * 0.8, 0.1,
                            Material.PACKED_ICE.createBlockData());
                    for (Player player : ctx.arena().playersInside()) {
                        if (player.getLocation().distanceSquared(base) <= damageRadius * damageRadius) {
                            player.damage(damage, ctx.boss());
                            Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
