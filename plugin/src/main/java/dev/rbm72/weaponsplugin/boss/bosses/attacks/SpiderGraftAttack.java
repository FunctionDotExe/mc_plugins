package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The spider graft: a leaping pounce that mats the landing zone in webbing and calls two skittering
 * cave-spider limbs to the boss's side — the first piece of another beast the Grafted Horror ever
 * bolted onto itself, and the one it always wakes up with.
 */
public final class SpiderGraftAttack extends BossAttack {

    private static final Color SICKLY_GREEN = Color.fromRGB(90, 130, 60);

    private final double damage;
    private final double radius;
    private final int webTicks;
    private final int telegraphTicks;
    private final int airTicks;
    private final int addCount;
    private final double addHealth;
    private final int webBlockDurationTicks;

    public SpiderGraftAttack(WeaponsPlugin plugin) {
        super(plugin, "grafted_horror");
        this.damage = configDouble("spider-graft-damage", 7.0);
        this.radius = configDouble("spider-graft-radius", 3.5);
        this.webTicks = configInt("spider-graft-web-ticks", 70);
        this.telegraphTicks = configInt("spider-graft-telegraph-ticks", 16);
        this.airTicks = configInt("spider-graft-air-ticks", 12);
        this.addCount = configInt("spider-graft-add-count", 2);
        this.addHealth = configDouble("spider-graft-add-health", 16.0);
        this.webBlockDurationTicks = configInt("spider-graft-web-block-duration-ticks", 300);
    }

    @Override
    public String name() {
        return "Skittering Pounce";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("spider-graft-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location landingSpot = ctx.target().getLocation().clone();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.point(boss.getLocation().add(0, 1.5, 0), Particle.ITEM_COBWEB, 3);
                },
                () -> {
                    Vector toTarget = landingSpot.toVector().subtract(boss.getLocation().toVector());
                    double horizontalDistance = Math.sqrt(toTarget.getX() * toTarget.getX() + toTarget.getZ() * toTarget.getZ());
                    Vector horizontal = new Vector(toTarget.getX(), 0, toTarget.getZ());
                    Vector launch = (horizontalDistance > 1.0E-3 ? horizontal.normalize() : new Vector(1, 0, 0))
                            .multiply(Math.min(1.4, horizontalDistance / 6.0)).setY(0.7);
                    boss.setVelocity(launch);
                    BossAudio.play(boss.getLocation(), "boss.grafted_horror.spider_pounce", Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 0.6f);
                    Fx.coloredRing(landingSpot, SICKLY_GREEN, 1.2f, radius * 0.5, 24, 0);

                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (elapsed >= airTicks || !boss.isValid()) {
                                cancel();
                                land(ctx, boss);
                                return;
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                10, onComplete);
    }

    private void land(AttackContext ctx, LivingEntity boss) {
        Location loc = boss.getLocation();
        Fx.expandingRings(plugin, loc, Particle.ITEM_COBWEB, radius, 3, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), SICKLY_GREEN, 1.6f, 40, 0.6);
        BossAudio.play(loc, "boss.grafted_horror.web_slam", Sound.BLOCK_COBWEB_BREAK, 1.2f, 0.7f);
        // Real cobweb, not a particle stand-in — it actually snags anyone who walks into it.
        Grief.raiseColumns(ctx, loc, Material.COBWEB, 1, addCount + 2, radius * 0.8, webBlockDurationTicks);
        for (Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                    && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                target.damage(damage, boss);
                StatusEffectManager.apply(target, PotionEffectType.SLOWNESS, webTicks, 2);
                StatusEffectManager.apply(target, PotionEffectType.JUMP_BOOST, webTicks, 128);
                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            }
        }

        AddManager adds = ctx.instance().addManager();
        for (int i = 0; i < addCount; i++) {
            double angle = 2 * Math.PI * i / addCount;
            Location spot = loc.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), SICKLY_GREEN, 1.2f, 16, 0.3);
            adds.spawn(spot.getWorld(), spot, EntityType.CAVE_SPIDER, entity -> {
                entity.customName(Component.text("Skittering Limb", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
                entity.setCustomNameVisible(true);
                var maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(addHealth);
                    entity.setHealth(addHealth);
                }
                if (entity instanceof Mob mob) {
                    mob.setTarget(ctx.target());
                }
            });
        }
    }
}
