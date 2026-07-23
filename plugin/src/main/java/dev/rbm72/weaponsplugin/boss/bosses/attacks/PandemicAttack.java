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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enrage-only finisher: the warden releases the Great Plague across the arena. Originally this hit
 * every player unconditionally — no position, no action, could change the outcome. Now a few pockets
 * of clean air (blue-marked) form around the rim as the plague spreads; reach one before it lands
 * and you're spared the infection entirely.
 */
public final class PandemicAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);
    private static final Color SICKLY = Color.fromRGB(150, 200, 80);

    private final double damage;
    private final int poisonAmplifier;
    private final int poisonTicks;
    private final int weaknessTicks;
    private final int hungerTicks;
    private final double cleanAirRadius;
    private final int cleanAirCount;
    private final int telegraphTicks;

    public PandemicAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.damage = configDouble("pandemic-damage", 14.0);
        this.poisonAmplifier = configInt("pandemic-poison-amplifier", 3); // POISON IV
        this.poisonTicks = configInt("pandemic-poison-ticks", 80); // 4s
        this.weaknessTicks = configInt("pandemic-weakness-ticks", 120);
        this.hungerTicks = configInt("pandemic-hunger-ticks", 120); // suppresses natural regen
        this.cleanAirRadius = configDouble("pandemic-clean-air-radius", 2.2);
        this.cleanAirCount = configInt("pandemic-clean-air-count", 3);
        this.telegraphTicks = configInt("pandemic-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Pandemic";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("pandemic-cooldown-seconds", 22.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();

        List<Location> cleanAirSpots = new ArrayList<>(cleanAirCount);
        double baseAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < cleanAirCount; i++) {
            double angle = baseAngle + (2 * Math.PI * i) / cleanAirCount;
            cleanAirSpots.add(center.clone().add(
                    Math.cos(angle) * arenaRadius * 0.8, 0, Math.sin(angle) * arenaRadius * 0.8));
        }

        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, arenaRadius * 0.9, Particle.SPORE_BLOSSOM_AIR);
                    Fx.coloredRing(origin, TOXIC, 1.6f, 4.4, 42, 0);
                    for (Location spot : cleanAirSpots) {
                        Telegraph.safeZone(spot, cleanAirRadius);
                    }
                },
                () -> {
                    BossAudio.play(origin, "boss.plague_warden.pandemic", Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.5f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.6f);
                    if (center.getWorld() != null) {
                        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 15, 0.96, 0.96, 0.96, 0);
                        center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0, 3, 0),
                                390, arenaRadius * 1.28, 6.4, arenaRadius * 1.28, 0.02);
                    }
                    Fx.expandingRings(plugin, origin, Particle.ITEM_SLIME, arenaRadius * 0.9, 7, 2L);
                    for (Player player : ctx.arena().playersInside()) {
                        boolean sheltered = cleanAirSpots.stream()
                                .anyMatch(spot -> spot.distanceSquared(player.getLocation()) <= cleanAirRadius * cleanAirRadius);
                        if (sheltered) {
                            Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(60, 140, 255), 1.4f, 18, 0.4);
                            continue;
                        }
                        player.damage(damage, ctx.boss());
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, poisonAmplifier));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, hungerTicks, 0));
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SICKLY, 1.8f, 40, 0.5);
                        Fx.burst(player.getLocation().add(0, 1, 0), Particle.SPORE_BLOSSOM_AIR, 16, 0.4);
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                    }
                },
                14, onComplete);
    }
}
