package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The elder snatches its target skyward on a levitation lift, then releases them
 * to plummet. Fall damage is applied manually and capped so it stings without
 * one-shotting, and the drop leaves them burning.
 */
public final class GrabAndDropAttack extends BossAttack {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);

    private final double dropDamage;
    private final int liftTicks;
    private final int levitationAmplifier;
    private final int fireTicks;
    private final int telegraphTicks;

    public GrabAndDropAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.dropDamage = configDouble("grab-and-drop-damage", 10.0);
        this.liftTicks = configInt("grab-and-drop-lift-ticks", 40);
        this.levitationAmplifier = configInt("grab-and-drop-levitation-amplifier", 4);
        this.fireTicks = configInt("grab-and-drop-fire-ticks", 80);
        this.telegraphTicks = configInt("grab-and-drop-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Grab and Drop";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("grab-and-drop-cooldown-seconds", 13.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Player victim = ctx.target();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(victim);
                    Fx.coloredRing(victim.getLocation(), DRAGON_RED, 1.5f, 2.0, 28, 0);
                },
                () -> {
                    BossAudio.play(victim.getLocation(), "boss.dragon_elder.grab", Sound.ENTITY_ENDER_DRAGON_FLAP, 1.3f, 0.8f);
                    Fx.sound(victim.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.1f, 0.7f);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, liftTicks, levitationAmplifier));
                    Fx.coloredBurst(victim.getLocation().add(0, 1, 0), DRAGON_RED, 1.6f, 40, 0.6);
                    Fx.helixFrame(victim.getLocation(), Particle.FLAME, 1.2, 5, 0, 1.0);

                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!victim.isOnline()) {
                            return;
                        }
                        victim.removePotionEffect(PotionEffectType.LEVITATION);
                        victim.damage(dropDamage, ctx.boss());
                        victim.setFireTicks(fireTicks);
                        Location loc = victim.getLocation();
                        Fx.coloredBurst(loc.add(0, 1, 0), DRAGON_RED, 1.8f, 44, 0.6);
                        Fx.burst(loc, Particle.FLAME, 34, 0.6);
                        Fx.point(loc, Particle.LAVA, 6);
                        Fx.sound(loc, Sound.ENTITY_GENERIC_BIG_FALL, 1.1f, 0.7f);
                        Fx.bloodSpray(loc);
                    }, liftTicks + 4L);
                },
                14, onComplete);
    }
}
