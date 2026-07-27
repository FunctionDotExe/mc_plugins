package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Real lingering clouds of withering miasma, dropped on the ground the group is standing on.
 * <p>
 * These are {@link AreaEffectCloud} entities, not a particle ring with a damage loop behind it — the same
 * object a lingering potion leaves, so it occupies space, is visible from across the arena, and can be
 * walked out of. Delete every particle call in this file and the attack is unchanged.
 * <p>
 * Picking the vanilla object also settles who it hurts for free: undead mobs are immune to Wither, so his
 * own army wades through the cloud untouched and only the group has to move. Nothing here has to check
 * whose side an entity is on.
 * <p>
 * Cloud <em>count</em> scales with the group, so a bigger group has more ground taken away from it. The
 * effect each cloud applies is identical at every size.
 */
public final class WitherCloudAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final double radius;
    private final int durationTicks;
    private final int witherAmplifier;
    private final int telegraphTicks;

    public WitherCloudAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.radius = configDouble("wither-cloud-radius", 4.0);
        this.durationTicks = configInt("wither-cloud-duration-ticks", 200);
        this.witherAmplifier = configInt("wither-cloud-wither-amplifier", 1);
        this.telegraphTicks = configInt("wither-cloud-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Wither Cloud";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("wither-cloud-cooldown-seconds", 13.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        List<Location> spots = pickSpots(ctx);
        sequence(telegraphTicks,
                () -> {
                    for (Location spot : spots) {
                        Telegraph.dangerZone(spot, radius);
                        Fx.coloredRing(spot, NECROTIC, 1.4f, radius, 36, 0);
                    }
                },
                () -> {
                    BossAudio.play(ctx.bossLocation(), "boss.necro_overlord.wither_cloud",
                            Sound.ENTITY_WITHER_SHOOT, 1.15f, 0.5f);
                    for (Location spot : spots) {
                        drop(ctx, spot);
                    }
                },
                12, onComplete);
    }

    /**
     * One cloud per two players, minimum one, each on a different player's feet. Falls back to the boss's
     * current target when nobody else is in the arena so the attack still lands solo.
     */
    private List<Location> pickSpots(AttackContext ctx) {
        List<Player> victims = Arena.combatants(ctx.bossLocation(), ctx.arena().radius());
        int clouds = Math.max(1, Math.min(configInt("wither-cloud-max-clouds", 3),
                1 + (victims.size() - 1) / 2));
        List<Location> spots = new ArrayList<>(clouds);
        for (int i = 0; i < clouds; i++) {
            Player victim = i < victims.size() ? victims.get(i) : ctx.target();
            if (victim != null) {
                spots.add(victim.getLocation());
            }
        }
        if (spots.isEmpty()) {
            spots.add(ctx.target().getLocation());
        }
        return spots;
    }

    private void drop(AttackContext ctx, Location spot) {
        if (spot.getWorld() == null) {
            return;
        }
        AreaEffectCloud cloud = spot.getWorld().spawn(spot, AreaEffectCloud.class, spawned -> {
            spawned.setSource(ctx.boss());
            spawned.setRadius((float) radius);
            spawned.setDuration(durationTicks);
            // Never shrinks: the point is a patch of floor the group has lost for a while, and a cloud that
            // quietly contracts leaves players standing in something they can no longer see the edge of.
            spawned.setRadiusPerTick(0f);
            spawned.setParticle(Particle.SCULK_SOUL);
            spawned.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 80, witherAmplifier), true);
            spawned.setPersistent(false);
        });
        ctx.instance().trackEntity(cloud);
        Fx.burst(spot.clone().add(0, 0.4, 0), Particle.SOUL, 18, radius * 0.4);
        Fx.sound(spot, Sound.PARTICLE_SOUL_ESCAPE, 1.1f, 0.6f);
    }
}
