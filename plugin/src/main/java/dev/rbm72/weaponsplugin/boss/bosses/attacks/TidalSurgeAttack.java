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

/** Signature grief: a surging tide floods the arena floor with real water, slowing everyone caught standing in it. */
public final class TidalSurgeAttack extends BossAttack {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);
    private static final Color PALE = Color.fromRGB(160, 240, 250);

    private final double damage;
    private final double radius;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int telegraphTicks;

    public TidalSurgeAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damage = configDouble("tidal-surge-damage", 4.0);
        this.radius = configDouble("tidal-surge-radius", 6.0);
        this.slowTicks = configInt("tidal-surge-slow-ticks", 80);
        this.slowAmplifier = configInt("tidal-surge-slow-amplifier", 1);
        this.telegraphTicks = configInt("tidal-surge-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Tidal Surge";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("tidal-surge-cooldown-seconds", 14.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, radius, Particle.SPLASH);
                    Fx.coloredRing(center, TEAL, 1.6f, radius, 54, 0);
                },
                () -> {
                    BossAudio.play(center, "boss.tide_leviathan.tidal_surge", Sound.ITEM_TRIDENT_RIPTIDE_3, 1.25f, 0.5f);
                    Fx.sound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.1f, 0.6f);
                    center.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone().add(0, 0.5, 0),
                            570, radius * 1.12, 1.6, radius * 1.12, 0.05);
                    Fx.expandingRings(plugin, center, Particle.SPLASH, radius, 5, 2L);
                    Fx.coloredBurst(center.clone().add(0, 1, 0), PALE, 2.0f, 82, radius * 0.5);
                    Grief.spread(ctx, center, Material.WATER, radius);
                    for (Player player : ctx.arena().playersInside()) {
                        if (player.getLocation().distanceSquared(center) > radius * radius) {
                            continue;
                        }
                        player.damage(damage, ctx.boss());
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), TEAL, 1.2f, 16, 0.4);
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                    }
                },
                14, onComplete);
    }
}
