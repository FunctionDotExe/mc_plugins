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
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Wolf;

/**
 * Full Requiem, the enrage finale: every illusion the choir has ever sung into being answers the
 * call at once — a swarm of wraiths, a pack of hounds, and a scream that saps everyone standing
 * in the arena.
 */
public final class GrandIllusionAttack extends BossAttack {

    private static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);

    private final double addHealth;
    private final int telegraphTicks;

    public GrandIllusionAttack(WeaponsPlugin plugin) {
        super(plugin, "hollow_choir");
        this.addHealth = configDouble("grand-illusion-add-health", 18.0);
        this.telegraphTicks = configInt("grand-illusion-telegraph-ticks", 26);
    }

    @Override
    public String name() {
        return "Full Requiem";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("grand-illusion-cooldown-seconds", 34.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, PALE_VIOLET, 1.6f, 3.5, 26, 0);
                    Fx.point(origin.clone().add(0, 1.4, 0), Particle.SOUL, 6);
                },
                () -> {
                    ctx.instance().showTitle(
                            Component.text("FULL REQUIEM", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                            Component.text("Every illusion it ever sang answers at once", NamedTextColor.GRAY));
                    BossAudio.play(origin, "boss.hollow_choir.full_requiem", Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 0.5f);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), PALE_VIOLET, 2.4f, 60, 1.0);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.SOUL, 50, 0.9);
                    // A real ring of evoker fangs erupts around it, not a particle ring standing in for one.
                    for (int fang = 0; fang < 6; fang++) {
                        double fangAngle = 2 * Math.PI * fang / 6;
                        Location fangSpot = origin.clone().add(Math.cos(fangAngle) * 3.0, 0, Math.sin(fangAngle) * 3.0);
                        EvokerFangs fangs = fangSpot.getWorld().spawn(fangSpot, EvokerFangs.class);
                        fangs.setOwner(ctx.boss());
                    }

                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < 3; i++) {
                        double angle = 2 * Math.PI * i / 3;
                        Location spot = origin.clone().add(Math.cos(angle) * 2.5, 1.5, Math.sin(angle) * 2.5);
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
                    for (int i = 0; i < 2; i++) {
                        double angle = Math.PI * i;
                        Location spot = origin.clone().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0);
                        Fx.coloredBurst(spot.clone().add(0, 0.5, 0), PALE_VIOLET, 1.4f, 18, 0.3);
                        adds.spawn(spot.getWorld(), spot, EntityType.WOLF, entity -> {
                            entity.customName(Component.text("Spectral Hound", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
                            entity.setCustomNameVisible(true);
                            var maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
                            if (maxHealthAttr != null) {
                                maxHealthAttr.setBaseValue(addHealth);
                                entity.setHealth(addHealth);
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
                20, onComplete);
    }
}
