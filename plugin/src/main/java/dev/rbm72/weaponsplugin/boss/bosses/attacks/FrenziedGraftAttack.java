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
 * Full Metamorphosis, the enrage-only finale: every graft the horror has bolted onto itself fires at
 * once. Three warp-blinks chain a poison, a fire, and a wither hit onto whoever it lands behind, then
 * the horror tears itself open and every donor it ever grafted crawls out to fight one last time
 * alongside it.
 */
public final class FrenziedGraftAttack extends BossAttack {

    private static final Color[] BLINK_COLORS = {
            Color.fromRGB(90, 130, 60), Color.fromRGB(255, 120, 0), Color.fromRGB(60, 60, 60)
    };
    private static final PotionEffectType[] BLINK_DEBUFFS = {
            PotionEffectType.POISON, PotionEffectType.SLOWNESS, PotionEffectType.WITHER
    };

    private final double damagePerBlink;
    private final double range;
    private final int debuffTicks;
    private final int telegraphTicks;
    private final double addHealth;

    public FrenziedGraftAttack(WeaponsPlugin plugin) {
        super(plugin, "grafted_horror");
        this.damagePerBlink = configDouble("frenzied-graft-damage-per-blink", 6.0);
        this.range = configDouble("frenzied-graft-range", 2.8);
        this.debuffTicks = configInt("frenzied-graft-debuff-ticks", 60);
        this.telegraphTicks = configInt("frenzied-graft-telegraph-ticks", 22);
        this.addHealth = configDouble("frenzied-graft-add-health", 20.0);
    }

    @Override
    public String name() {
        return "Full Metamorphosis";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("frenzied-graft-cooldown-seconds", 32.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        int[] blinkCounter = {0};

        Runnable blink = () -> {
            int index = Math.min(blinkCounter[0], BLINK_COLORS.length - 1);
            Location targetLoc = ctx.target().getLocation();
            Vector behind = targetLoc.getDirection().clone().multiply(-1.6).setY(0);
            Location arriveAt = targetLoc.clone().add(behind);

            Fx.burst(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 30, 0.5);
            boss.teleport(arriveAt);
            Fx.coloredBurst(arriveAt.clone().add(0, 1, 0), BLINK_COLORS[index], 1.6f, 30, 0.5);
            Fx.flash(arriveAt.clone().add(0, 1, 0), 1);
            BossAudio.play(arriveAt, "boss.grafted_horror.metamorphosis_blink", Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.6f + index * 0.15f);

            for (Entity nearby : boss.getNearbyEntities(range, range, range)) {
                if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                    target.damage(damagePerBlink, boss);
                    StatusEffectManager.apply(target, BLINK_DEBUFFS[index], debuffTicks, 1);
                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                }
            }
            blinkCounter[0]++;
        };

        sequence(telegraphTicks,
                () -> {
                    for (Color color : BLINK_COLORS) {
                        Fx.coloredBurst(ctx.bossLocation().add(0, 1.4, 0), color, 1.0f, 3, 0.5);
                    }
                    Fx.ring(ctx.bossLocation(), Particle.SOUL_FIRE_FLAME, 2.5, 20);
                },
                () -> {
                    ctx.instance().showTitle(
                            Component.text("FULL METAMORPHOSIS", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                            Component.text("Every graft it ever took answers at once", NamedTextColor.GRAY));
                    blink.run();
                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), blink, 14L);
                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), blink, 28L);
                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> menagerieAwakens(ctx, boss), 40L);
                },
                50, onComplete);
    }

    private void menagerieAwakens(AttackContext ctx, LivingEntity boss) {
        if (!boss.isValid()) {
            return;
        }
        Location loc = boss.getLocation();
        Fx.expandingRings(plugin, loc, Particle.SOUL_FIRE_FLAME, 6.0, 4, 2L);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), Color.fromRGB(200, 30, 30), 2.0f, 50, 0.8);
        BossAudio.play(loc, "boss.grafted_horror.menagerie_awakens", Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.9f);
        // The ground itself splits open as every graft tears free — real terrain damage.
        Grief.breakCrater(ctx, loc, 3.0);

        EntityType[] types = {EntityType.CAVE_SPIDER, EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.ENDERMITE};
        String[] names = {"Skittering Limb", "Marrow Splinter", "Brittle Sinew", "Warp Mite"};
        AddManager adds = ctx.instance().addManager();
        for (int i = 0; i < types.length; i++) {
            double angle = 2 * Math.PI * i / types.length;
            Location spot = loc.clone().add(Math.cos(angle) * 3.5, 0, Math.sin(angle) * 3.5);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), Color.fromRGB(150, 30, 30), 1.4f, 20, 0.4);
            int nameIndex = i;
            adds.spawn(spot.getWorld(), spot, types[i], entity -> {
                entity.customName(Component.text(names[nameIndex], NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
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
