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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Wolf;

/** Conjures a pair of translucent hounds from a memory of something that used to hunt — fast, and only half-real. */
public final class SpectralHoundsAttack extends BossAttack {

    private static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);

    private final int addCount;
    private final double addHealth;
    private final double addSpeedMultiplier;
    private final int telegraphTicks;

    public SpectralHoundsAttack(WeaponsPlugin plugin) {
        super(plugin, "hollow_choir");
        this.addCount = configInt("spectral-hounds-count", 2);
        this.addHealth = configDouble("spectral-hounds-add-health", 16.0);
        this.addSpeedMultiplier = configDouble("spectral-hounds-speed-multiplier", 1.6);
        this.telegraphTicks = configInt("spectral-hounds-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Spectral Hounds";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("spectral-hounds-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> Fx.point(origin.clone().add(0, 1, 0), Particle.SOUL, 6),
                () -> {
                    BossAudio.play(origin, "boss.hollow_choir.spectral_hounds", Sound.ENTITY_WOLF_GROWL, 1.2f, 0.6f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), PALE_VIOLET, 1.8f, 30, 0.6);

                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < addCount; i++) {
                        double angle = 2 * Math.PI * i / addCount;
                        Location spot = origin.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                        Fx.coloredBurst(spot.clone().add(0, 0.5, 0), PALE_VIOLET, 1.4f, 18, 0.3);
                        adds.spawn(spot.getWorld(), spot, EntityType.WOLF, entity -> {
                            entity.customName(Component.text("Spectral Hound", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
                            entity.setCustomNameVisible(true);
                            var maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
                            if (maxHealthAttr != null) {
                                maxHealthAttr.setBaseValue(addHealth);
                                entity.setHealth(addHealth);
                            }
                            var speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
                            if (speedAttr != null) {
                                speedAttr.setBaseValue(speedAttr.getBaseValue() * addSpeedMultiplier);
                            }
                            if (entity instanceof Wolf wolf) {
                                wolf.setAngry(true);
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
