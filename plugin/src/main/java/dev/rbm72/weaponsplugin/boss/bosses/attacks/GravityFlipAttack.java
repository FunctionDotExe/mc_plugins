package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Reality tilts: every player in the arena floats upward while chunks of the End are hurled after them. */
public final class GravityFlipAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);

    private final double throwDamage;
    private final float impactPower;
    private final int blocks;
    private final int levitationTicks;
    private final int levitationAmplifier;
    private final int telegraphTicks;

    public GravityFlipAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.throwDamage = configDouble("gravity-flip-throw-damage", 6.0);
        this.impactPower = (float) configDouble("gravity-flip-impact-power", 1.5);
        this.blocks = configInt("gravity-flip-blocks", 3);
        this.levitationTicks = configInt("gravity-flip-levitation-ticks", 60);
        this.levitationAmplifier = configInt("gravity-flip-levitation-amplifier", 1);
        this.telegraphTicks = configInt("gravity-flip-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Gravity Flip";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("gravity-flip-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, VOID_PURPLE, 1.5f, 4.5, 38, 0);
                    Fx.helixFrame(origin, Particle.PORTAL, 2.0, 5, telegraphTicks * 0.4, 1.0);
                },
                () -> {
                    BossAudio.play(origin, "boss.void_sovereign.gravity_flip", Sound.BLOCK_PORTAL_TRIGGER, 1.1f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_ENDERMAN_SCREAM, 0.9f, 0.7f);
                    Fx.expandingRings(plugin, origin, Particle.REVERSE_PORTAL, 6.5, 4, 3L);

                    for (Player player : ctx.arena().playersInside()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, levitationTicks, levitationAmplifier));
                        Fx.burst(player.getLocation().add(0, 1, 0), Particle.PORTAL, 28, 0.4);
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), VOID_PURPLE, 1.2f, 20, 0.4);
                    }
                    for (int i = 0; i < blocks; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.2, 0), ctx.target(), Material.END_STONE, throwDamage, impactPower);
                    }
                },
                12, onComplete);
    }
}
