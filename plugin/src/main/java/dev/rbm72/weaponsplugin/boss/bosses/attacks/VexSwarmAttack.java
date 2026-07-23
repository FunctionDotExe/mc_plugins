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

/** Calls a flock of flickering illusions into being — erratic, fast, and quick to overwhelm if ignored. */
public final class VexSwarmAttack extends BossAttack {

    private static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);

    private final int addCount;
    private final double addHealth;
    private final int telegraphTicks;

    public VexSwarmAttack(WeaponsPlugin plugin) {
        super(plugin, "hollow_choir");
        this.addCount = configInt("vex-swarm-count", 3);
        this.addHealth = configDouble("vex-swarm-add-health", 8.0);
        this.telegraphTicks = configInt("vex-swarm-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Vex Swarm";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("vex-swarm-cooldown-seconds", 16.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> Fx.coloredBurst(origin.clone().add(0, 1.4, 0), PALE_VIOLET, 1.2f, 10, 0.4),
                () -> {
                    BossAudio.play(origin, "boss.hollow_choir.vex_swarm", Sound.ENTITY_VEX_CHARGE, 1.2f, 0.9f);
                    Fx.coloredBurst(origin.clone().add(0, 1.4, 0), PALE_VIOLET, 2.0f, 34, 0.7);
                    Fx.burst(origin.clone().add(0, 1.4, 0), Particle.WITCH, 26, 0.6);

                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < addCount; i++) {
                        double angle = 2 * Math.PI * i / addCount;
                        Location spot = origin.clone().add(Math.cos(angle) * 2.0, 1.5, Math.sin(angle) * 2.0);
                        Fx.burst(spot, Particle.WITCH, 14, 0.3);
                        adds.spawn(spot.getWorld(), spot, EntityType.VEX, entity -> {
                            entity.customName(Component.text("Choir Wraith", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
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
