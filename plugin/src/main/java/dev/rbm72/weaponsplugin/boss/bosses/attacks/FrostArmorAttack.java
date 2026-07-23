package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/** Defensive tempo move: the queen sheathes herself in frost armor, gaining Absorption for a few seconds. */
public final class FrostArmorAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(150, 220, 255);

    private final int absorptionTicks;
    private final int absorptionAmplifier;
    private final int telegraphTicks;

    public FrostArmorAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.absorptionTicks = configInt("frost-armor-absorption-ticks", 120);
        this.absorptionAmplifier = configInt("frost-armor-absorption-amplifier", 2);
        this.telegraphTicks = configInt("frost-armor-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Frost Armor";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("frost-armor-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, FROST, 1.3f, 2.4, 32, 0);
                    Fx.point(origin.clone().add(0, 1.5, 0), Particle.SNOWFLAKE, 7);
                },
                () -> {
                    ctx.boss().addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absorptionTicks, absorptionAmplifier));
                    BossAudio.play(origin, "boss.frost_queen.frost_armor", Sound.BLOCK_GLASS_PLACE, 1.3f, 0.7f);
                    Fx.sound(origin, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.1f, 1.3f);
                    Fx.expandingRings(plugin, origin, Particle.SNOWFLAKE, 3.0, 5, 3L);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), FROST, 2.3f, 64, 0.7);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), Color.fromRGB(230, 250, 255), 1.6f, 34, 0.8);
                    Fx.spinningIcon(plugin, origin.clone().add(0, 2.4, 0), Material.PACKED_ICE, 1.2f, 60, 12.0);
                    frostHelix(ctx);
                },
                12, onComplete);
    }

    /** Rising frost helix wrapping the queen as the armor forms; self-cancels when she dies. */
    private void frostHelix(AttackContext ctx) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !ctx.boss().isValid()) {
                    cancel();
                    return;
                }
                Fx.helixFrame(ctx.boss().getLocation(), Particle.SNOWFLAKE, 1.3, 5, ticks * 0.5, ticks * 0.15);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
