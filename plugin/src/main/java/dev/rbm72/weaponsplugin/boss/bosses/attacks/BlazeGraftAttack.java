package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * The blaze graft: marrow grafted from a fallen blaze ignites, hurling a volley of molten blocks and
 * setting the target alight, then briefly wakes a floating splinter of that same fire ready to keep
 * up the barrage.
 */
public final class BlazeGraftAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DEEP_RED = Color.fromRGB(150, 20, 20);

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int fireTicks;
    private final int telegraphTicks;
    private final double addHealth;

    public BlazeGraftAttack(WeaponsPlugin plugin) {
        super(plugin, "grafted_horror");
        this.damage = configDouble("blaze-graft-damage", 7.0);
        this.impactPower = (float) configDouble("blaze-graft-impact-power", 1.6);
        this.projectiles = configInt("blaze-graft-projectiles", 3);
        this.fireTicks = configInt("blaze-graft-fire-ticks", 60);
        this.telegraphTicks = configInt("blaze-graft-telegraph-ticks", 18);
        this.addHealth = configDouble("blaze-graft-add-health", 18.0);
    }

    @Override
    public String name() {
        return "Marrow Ignition";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("blaze-graft-cooldown-seconds", 13.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        EntityEquipment equipment = ctx.boss().getEquipment();
        if (equipment != null) {
            equipment.setItemInOffHand(new ItemStack(Material.BLAZE_ROD));
        }

        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.point(origin.clone().add(0, 1.4, 0), Particle.FLAME, 5);
                    Fx.coloredRing(origin, EMBER, 1.2f, 2.2, 26, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.grafted_horror.blaze_ignition", Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.7f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), DEEP_RED, 1.8f, 34, 0.6);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), EMBER, 1.4f, 20, 0.6);
                    for (int i = 0; i < projectiles; i++) {
                        Location from = origin.clone().add(
                                (Math.random() - 0.5) * 2.5, 2.6, (Math.random() - 0.5) * 2.5);
                        Fx.trail(from, Particle.FLAME, 10, 0.25, 0.02);
                        Grief.throwBlock(ctx, from, ctx.target(), Material.MAGMA_BLOCK, damage, impactPower);
                    }
                    ctx.target().setFireTicks(Math.max(ctx.target().getFireTicks(), fireTicks));

                    AddManager adds = ctx.instance().addManager();
                    Location spot = origin.clone().add(0, 2.5, 0);
                    adds.spawn(spot.getWorld(), spot, EntityType.BLAZE, entity -> {
                        entity.customName(Component.text("Marrow Splinter", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
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
                },
                14, onComplete);
    }
}
