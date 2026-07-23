package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The warp graft: eyes scavenged from something that saw between spaces let the horror blink behind
 * its prey, and a scatter of endermites left in the tear keep gnawing while the disorientation lingers.
 */
public final class EndermanGraftAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(90, 0, 120);

    private final double damage;
    private final double range;
    private final int blindnessTicks;
    private final int telegraphTicks;
    private final int addCount;
    private final double addHealth;

    public EndermanGraftAttack(WeaponsPlugin plugin) {
        super(plugin, "grafted_horror");
        this.damage = configDouble("enderman-graft-damage", 8.0);
        this.range = configDouble("enderman-graft-range", 2.5);
        this.blindnessTicks = configInt("enderman-graft-blindness-ticks", 50);
        this.telegraphTicks = configInt("enderman-graft-telegraph-ticks", 14);
        this.addCount = configInt("enderman-graft-add-count", 2);
        this.addHealth = configDouble("enderman-graft-add-health", 10.0);
    }

    @Override
    public String name() {
        return "Warp Sinew";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("enderman-graft-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        sequence(telegraphTicks,
                () -> {
                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 8, 0.3);
                    Fx.ring(boss.getLocation(), Particle.REVERSE_PORTAL, 1.6, 16);
                },
                () -> {
                    Location targetLoc = ctx.target().getLocation();
                    Vector behind = targetLoc.getDirection().clone().multiply(-1.6).setY(0);
                    Location arriveAt = targetLoc.clone().add(behind);

                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 40, 0.5);
                    Fx.coloredBurst(boss.getLocation().add(0, 1, 0), VOID_PURPLE, 1.5f, 30, 0.4);
                    boss.teleport(arriveAt);
                    Fx.burst(arriveAt.clone().add(0, 1, 0), Particle.PORTAL, 40, 0.4);
                    Fx.flash(arriveAt.clone().add(0, 1, 0), 2);
                    BossAudio.play(arriveAt, "boss.grafted_horror.warp_sinew", Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.7f);
                    // Reality tears where it lands — a real small crater, not just a light flash.
                    Grief.breakCrater(ctx, arriveAt, 1.5);

                    for (Entity nearby : boss.getNearbyEntities(range, range, range)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, boss);
                            StatusEffectManager.apply(target, PotionEffectType.BLINDNESS, blindnessTicks, 0);
                            StatusEffectManager.apply(target, PotionEffectType.NAUSEA, blindnessTicks, 0);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }

                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < addCount; i++) {
                        double angle = 2 * Math.PI * i / addCount;
                        Location spot = arriveAt.clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
                        Fx.burst(spot.clone().add(0, 1, 0), Particle.PORTAL, 14, 0.3);
                        adds.spawn(spot.getWorld(), spot, EntityType.ENDERMITE, entity -> {
                            entity.customName(Component.text("Warp Mite", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
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
                },
                10, onComplete);
    }
}
