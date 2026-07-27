package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/** Enrage-only: the overlord tears open the earth and calls forth an entire legion, blanketing the arena in wither. */
public final class ArmyOfTheDeadAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final int summonCount;
    private final int maxAdds;
    private final double damage;
    private final double radius;
    private final double witherRadius;
    private final int witherTicks;
    private final int telegraphTicks;

    public ArmyOfTheDeadAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.summonCount = configInt("army-of-the-dead-count", 6);
        // Above the standing horde's own live-add cap on purpose. This gate counts every add in the
        // fight, and the Overlord's P4 already has a full wave-driven horde on the floor — at the old
        // default of 10 the enrage legion silently never spawned a single undead, because the cap was
        // already exceeded before the attack ever ran. It has to sit above the horde ceiling to be the
        // surge it is written as.
        this.maxAdds = configInt("army-of-the-dead-max-adds", 30);
        this.damage = configDouble("army-of-the-dead-damage", 12.0);
        this.radius = configDouble("army-of-the-dead-radius", 6.0);
        this.witherRadius = configDouble("army-of-the-dead-wither-radius", 14.0);
        this.witherTicks = configInt("army-of-the-dead-wither-ticks", 100);
        this.telegraphTicks = configInt("army-of-the-dead-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Army of the Dead";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("army-of-the-dead-cooldown-seconds", 20.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, NECROTIC, 1.6f, radius, 48, 0);
                    Fx.helixFrame(origin, Particle.SCULK_SOUL, 1.4, 9, System.currentTimeMillis() * 0.02, 1.4);
                },
                () -> {
                    BossAudio.play(origin, "boss.necro_overlord.army_of_the_dead", Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.4f);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 9, 0.96, 0.32, 0.96, 0);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), NECROTIC, 2.0f, 40, radius * 0.3);

                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < summonCount && adds.aliveCount() < maxAdds; i++) {
                        Location spawnAt = origin.clone().add(
                                ThreadLocalRandom.current().nextDouble(-4, 4), 0,
                                ThreadLocalRandom.current().nextDouble(-4, 4));
                        Fx.burst(spawnAt.clone().add(0, 1, 0), Particle.SCULK_SOUL, 32, 0.4);
                        Fx.point(spawnAt.clone().add(0, 0.2, 0), Particle.SOUL, 12);
                        EntityType type = (i % 2 == 0) ? EntityType.SKELETON : EntityType.ZOMBIE;
                        adds.spawn(origin.getWorld(), spawnAt, type, entity -> {
                            entity.customName(Component.text("Undead Legionnaire", NamedTextColor.DARK_GREEN));
                            entity.setCustomNameVisible(true);
                            if (entity instanceof Mob mob) {
                                mob.setTarget(ctx.target());
                            }
                        });
                    }

                    // Necrotic nova around the overlord.
                    Fx.expandingRings(plugin, origin, Particle.SOUL, radius, 5, 3L);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }

                    // The curse spreads outward from the overlord but still fades with distance —
                    // previously every player in the arena got withered no matter how far away they
                    // stood, with nothing to actually dodge.
                    for (Player player : ctx.arena().playersInside()) {
                        if (player.getLocation().distanceSquared(origin) > witherRadius * witherRadius) {
                            continue;
                        }
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, 1));
                    }
                },
                16, onComplete);
    }
}
