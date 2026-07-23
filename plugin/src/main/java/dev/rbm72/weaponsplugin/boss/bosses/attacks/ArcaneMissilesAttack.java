package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** A volley of homing-ish arcane bolts: real spinning amethyst shards that chase the target and detonate on contact. */
public final class ArcaneMissilesAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(140, 50, 200);

    private final double damage;
    private final int missiles;
    private final double hitRadius;
    private final double speed;
    private final int maxLifeTicks;
    private final int telegraphTicks;

    public ArcaneMissilesAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.damage = configDouble("arcane-missiles-damage", 7.0);
        this.missiles = configInt("arcane-missiles-count", 4);
        this.hitRadius = configDouble("arcane-missiles-hit-radius", 1.5);
        this.speed = configDouble("arcane-missiles-speed", 0.65);
        this.maxLifeTicks = configInt("arcane-missiles-max-life-ticks", 80);
        this.telegraphTicks = configInt("arcane-missiles-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Arcane Missiles";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("arcane-missiles-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredRing(origin.clone().add(0, 1.2, 0), VOID_PURPLE, 1.3f, 1.4, 22, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.void_sovereign.arcane_missiles", Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.1f);
                    Fx.sound(origin, Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, 0.9f);
                    for (int i = 0; i < missiles; i++) {
                        launchMissile(ctx, i * 3L);
                    }
                },
                12, onComplete);
    }

    private void launchMissile(AttackContext ctx, long startDelay) {
        // Each bolt is a real spinning amethyst shard (ItemDisplay), not a bare particle stream.
        // It re-homes toward the target's live position each tick, damages on proximity, and
        // self-cancels on boss death or timeout so nothing lingers.
        double spawnAngle = Math.random() * Math.PI * 2;
        new BukkitRunnable() {
            int ticks = 0;
            Location pos;
            ItemDisplay icon;

            @Override
            public void run() {
                if (!ctx.boss().isValid() || !ctx.target().isOnline() || ticks >= maxLifeTicks) {
                    removeIcon();
                    cancel();
                    return;
                }
                if (pos == null) {
                    pos = ctx.bossLocation().add(Math.cos(spawnAngle) * 1.2, 1.4, Math.sin(spawnAngle) * 1.2);
                    icon = Fx.spinningIcon(plugin, pos, Material.AMETHYST_SHARD, 0.7f, maxLifeTicks + 5, 40.0);
                }
                Location targetPoint = ctx.target().getLocation().add(0, 1, 0);
                Vector toTarget = targetPoint.toVector().subtract(pos.toVector());
                if (toTarget.lengthSquared() <= hitRadius * hitRadius) {
                    ctx.target().damage(damage, ctx.boss());
                    Fx.coloredBurst(pos, VOID_PURPLE, 1.9f, 34, 0.4);
                    Fx.burst(pos, Particle.PORTAL, 30, 0.3);
                    Fx.coloredBurst(pos, Color.fromRGB(220, 180, 255), 1.4f, 16, 0.5);
                    Fx.sound(pos, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
                    Fx.bloodSpray(targetPoint);
                    removeIcon();
                    cancel();
                    return;
                }
                pos.add(toTarget.normalize().multiply(speed));
                if (icon != null && !icon.isDead()) {
                    icon.teleport(pos);
                }
                Fx.coloredBurst(pos, VOID_PURPLE, 1.1f, 9, 0.12);
                Fx.point(pos, Particle.REVERSE_PORTAL, 6);
                ticks++;
            }

            private void removeIcon() {
                if (icon != null && !icon.isDead()) {
                    icon.remove();
                }
            }
        }.runTaskTimer(plugin, startDelay, 1L);
    }
}
