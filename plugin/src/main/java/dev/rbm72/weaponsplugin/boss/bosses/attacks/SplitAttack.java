package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * The Bulk sheds a chunk of itself mid-fight: it visibly shrinks and two lesser globs of the same
 * ooze peel off to fight independently. The one attack that changes its size outside a phase
 * transition — a live demonstration that the mass has to go somewhere.
 */
public final class SplitAttack extends BossAttack {

    private static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);

    private final double shrinkFactor;
    private final int addCount;
    private final double addHealth;
    private final int telegraphTicks;

    public SplitAttack(WeaponsPlugin plugin) {
        super(plugin, "amalgamated_bulk");
        this.shrinkFactor = configDouble("split-shrink-factor", 0.85);
        this.addCount = configInt("split-add-count", 2);
        this.addHealth = configDouble("split-add-health", 20.0);
        this.telegraphTicks = configInt("split-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Split";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("split-cooldown-seconds", 20.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location origin = boss.getLocation();

        sequence(telegraphTicks,
                () -> Fx.coloredBurst(origin.clone().add(0, 1, 0), OOZE_GREEN, 1.2f, 12, 0.5),
                () -> {
                    BossAudio.play(origin, "boss.amalgamated_bulk.split", Sound.ENTITY_SLIME_SQUISH, 1.3f, 0.5f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), OOZE_GREEN, 2.2f, 46, 0.8);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.ITEM_SLIME, 36, 0.7);

                    AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
                    if (scaleAttr != null) {
                        scaleAttr.setBaseValue(Math.max(0.5, scaleAttr.getBaseValue() * shrinkFactor));
                    }

                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < addCount; i++) {
                        double angle = 2 * Math.PI * i / addCount;
                        Location spot = origin.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                        Fx.coloredBurst(spot.clone().add(0, 0.5, 0), OOZE_GREEN, 1.4f, 20, 0.3);
                        adds.spawn(spot.getWorld(), spot, EntityType.SLIME, entity -> {
                            entity.customName(Component.text("Bulkling", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
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
                12, onComplete);
    }
}
