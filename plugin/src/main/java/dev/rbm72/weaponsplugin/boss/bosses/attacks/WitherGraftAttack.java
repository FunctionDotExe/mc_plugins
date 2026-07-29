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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The wither graft: brittle wither-bone shards fused into the horror's ribs pulse outward with a
 * decaying ring of rot, then a lesser wither skeleton it dragged along wakes to keep swinging.
 * Wearing the donor's own skull is the one graft that changes how the horror looks, not just how
 * it fights.
 */
public final class WitherGraftAttack extends BossAttack {

    private static final Color DECAY_GREY = Color.fromRGB(60, 60, 60);

    private final double damagePerPulse;
    private final double maxRadius;
    private final int durationTicks;
    private final int witherTicks;
    private final int telegraphTicks;
    private final double addHealth;

    public WitherGraftAttack(WeaponsPlugin plugin) {
        super(plugin, "grafted_horror");
        this.damagePerPulse = configDouble("wither-graft-damage-per-pulse", 4.0);
        this.maxRadius = configDouble("wither-graft-max-radius", 6.5);
        this.durationTicks = configInt("wither-graft-duration-ticks", 40);
        this.witherTicks = configInt("wither-graft-wither-ticks", 60);
        this.telegraphTicks = configInt("wither-graft-telegraph-ticks", 20);
        this.addHealth = configDouble("wither-graft-add-health", 24.0);
    }

    @Override
    public String name() {
        return "Wither Sinew";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("wither-graft-cooldown-seconds", 16.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        EntityEquipment equipment = ctx.boss().getEquipment();
        if (equipment != null) {
            ItemStack skull = new ItemStack(Material.WITHER_SKELETON_SKULL);
            if (skull.getItemMeta() instanceof SkullMeta meta) {
                meta.displayName(Component.text("Grafted Skull", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                skull.setItemMeta(meta);
            }
            equipment.setHelmet(skull);
        }

        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, DECAY_GREY, 1.4f, 2.5, 24, 0);
                    Fx.point(origin.clone().add(0, 1.4, 0), Particle.SMOKE, 4);
                },
                () -> {
                    BossAudio.play(origin, "boss.grafted_horror.wither_sinew", Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.7f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), DECAY_GREY, 1.8f, 30, 0.6);
                    // The ground itself rots — real blocks, not a tint effect.
                    Grief.spread(ctx, origin, Material.SOUL_SOIL, maxRadius);

                    AddManager adds = ctx.instance().addManager();
                    Location spot = origin.clone().add(2.0, 0, 0);
                    adds.spawn(spot.getWorld(), spot, EntityType.WITHER_SKELETON, entity -> {
                        entity.customName(Component.text("Brittle Sinew", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
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

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            double radius = maxRadius * (ticks + 1) / (double) durationTicks;
                            Fx.coloredRing(origin, DECAY_GREY, 1.2f, radius, 28, ticks * 0.4);
                            Fx.ring(origin, Particle.SOUL, radius, 20, ticks * 0.4);
                            if (ticks % 6 == 0) {
                                for (Entity nearby : ctx.boss().getNearbyEntities(radius + 1.0, 3.0, radius + 1.0)) {
                                    if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                            && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                        double dist = target.getLocation().distance(origin);
                                        if (dist <= radius) {
                                            tickHurt(ctx, target, damagePerPulse);
                                            StatusEffectManager.apply(target, PotionEffectType.WITHER, witherTicks, 1);
                                        }
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
