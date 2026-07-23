package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

import java.util.concurrent.ThreadLocalRandom;

/** Raises a mix of skeletons and zombies from the ground to swarm the target. Cleared on every phase change. */
public final class RaiseUndeadAttack extends BossAttack {

    private static final Color BONE = Color.fromRGB(235, 235, 210);

    private final int riseCount;
    private final int maxAdds;
    private final int telegraphTicks;

    public RaiseUndeadAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.riseCount = configInt("raise-undead-count", 4);
        this.maxAdds = configInt("raise-undead-max-adds", 6);
        this.telegraphTicks = configInt("raise-undead-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Raise Undead";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("raise-undead-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.ring(origin, Particle.SOUL, 3.0, 26);
                    Fx.coloredRing(origin, BONE, 1.1f, 3.7, 34, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.necro_overlord.raise_undead", Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
                    Fx.sound(origin, Sound.ENTITY_SKELETON_AMBIENT, 0.8f, 0.6f);
                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < riseCount && adds.aliveCount() < maxAdds; i++) {
                        Location spawnAt = origin.clone().add(
                                ThreadLocalRandom.current().nextDouble(-3, 3), 0,
                                ThreadLocalRandom.current().nextDouble(-3, 3));
                        Fx.burst(spawnAt.clone().add(0, 1, 0), Particle.SCULK_SOUL, 36, 0.4);
                        Fx.coloredBurst(spawnAt.clone().add(0, 1, 0), BONE, 1.4f, 30, 0.4);
                        Fx.point(spawnAt.clone().add(0, 0.2, 0), Particle.SOUL, 13);
                        EntityType type = (i % 2 == 0) ? EntityType.SKELETON : EntityType.ZOMBIE;
                        adds.spawn(origin.getWorld(), spawnAt, type, entity -> {
                            entity.customName(Component.text("Risen Dead", NamedTextColor.GRAY));
                            entity.setCustomNameVisible(true);
                            if (entity instanceof Mob mob) {
                                mob.setTarget(ctx.target());
                            }
                        });
                    }
                },
                10, onComplete);
    }
}
