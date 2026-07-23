package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The Colossus contracts on itself mid-fight — visibly shrinking as it trades bulk for speed and
 * fury, then rockets straight at the target in a short, erratic charge. The smaller it gets, the
 * faster and nastier this becomes.
 */
public final class FrenziedContractionAttack extends BossAttack {

    private static final Color SORROW_BLUE = Color.fromRGB(70, 120, 200);

    private final double shrinkFactor;
    private final double damage;
    private final double hitRadius;
    private final int chargeTicks;
    private final int telegraphTicks;

    public FrenziedContractionAttack(WeaponsPlugin plugin) {
        super(plugin, "weeping_colossus");
        this.shrinkFactor = configDouble("frenzied-contraction-shrink-factor", 0.82);
        this.damage = configDouble("frenzied-contraction-damage", 9.0);
        this.hitRadius = configDouble("frenzied-contraction-hit-radius", 2.5);
        this.chargeTicks = configInt("frenzied-contraction-charge-ticks", 24);
        this.telegraphTicks = configInt("frenzied-contraction-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Frenzied Contraction";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("frenzied-contraction-cooldown-seconds", 22.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location origin = boss.getLocation();

        sequence(telegraphTicks,
                () -> Fx.coloredBurst(origin.clone().add(0, 1, 0), SORROW_BLUE, 1.2f, 12, 0.4),
                () -> {
                    BossAudio.play(origin, "boss.weeping_colossus.contraction", Sound.ENTITY_GHAST_SCREAM, 1.3f, 1.3f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), SORROW_BLUE, 2.0f, 40, 0.7);
                    ctx.instance().showTitle(
                            Component.text("It's shrinking", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                            Component.text("And it's only getting faster", NamedTextColor.GRAY));

                    AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
                    if (scaleAttr != null) {
                        scaleAttr.setBaseValue(Math.max(0.5, scaleAttr.getBaseValue() * shrinkFactor));
                    }
                    AttributeInstance speedAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.15);
                    }

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= chargeTicks || !boss.isValid() || !ctx.target().isOnline()) {
                                cancel();
                                if (boss.isValid()) {
                                    // Skids to a stop hard enough to actually crack the ground.
                                    Grief.breakCrater(ctx, boss.getLocation(), 2.0);
                                }
                                return;
                            }
                            Vector toTarget = ctx.target().getLocation().toVector().subtract(boss.getLocation().toVector());
                            if (toTarget.lengthSquared() > 0.5) {
                                boss.setVelocity(toTarget.normalize().multiply(1.3));
                            }
                            Fx.trail(boss.getLocation(), Particle.SPLASH, 6, 0.2, 0.02);
                            for (Entity nearby : boss.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                                if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                    target.damage(damage * 0.15, boss);
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                14, onComplete);
    }
}
