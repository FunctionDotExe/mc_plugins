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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;

import java.util.concurrent.ThreadLocalRandom;

/** Raises rotting undead that swarm whoever the warden is pressuring. Cleared on every phase change. */
public final class SummonUndeadAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);

    private final int summonCount;
    private final int maxAdds;
    private final int telegraphTicks;

    public SummonUndeadAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.summonCount = configInt("summon-undead-count", 3);
        this.maxAdds = configInt("summon-undead-max-adds", 5);
        this.telegraphTicks = configInt("summon-undead-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Summon Undead";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("summon-undead-cooldown-seconds", 17.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.ring(origin, Particle.SPORE_BLOSSOM_AIR, 2.8, 24);
                    Fx.coloredRing(origin, TOXIC, 1.0f, 3.6, 30, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.plague_warden.summon", Sound.ENTITY_ZOMBIE_INFECT, 1.15f, 0.7f);
                    Fx.sound(origin, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.95f, 0.6f);
                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < summonCount && adds.aliveCount() < maxAdds; i++) {
                        Location spawnAt = origin.clone().add(
                                ThreadLocalRandom.current().nextDouble(-2, 2), 0,
                                ThreadLocalRandom.current().nextDouble(-2, 2));
                        Fx.burst(spawnAt.clone().add(0, 1, 0), Particle.SPORE_BLOSSOM_AIR, 34, 0.4);
                        Fx.coloredBurst(spawnAt.clone().add(0, 1, 0), TOXIC, 1.0f, 26, 0.4);
                        adds.spawn(origin.getWorld(), spawnAt, EntityType.ZOMBIE, entity -> {
                            entity.customName(Component.text("Plague Zombie", NamedTextColor.GREEN));
                            entity.setCustomNameVisible(true);
                            if (entity instanceof Monster monster) {
                                monster.setTarget(ctx.target());
                            }
                        });
                    }
                },
                10, onComplete);
    }
}
