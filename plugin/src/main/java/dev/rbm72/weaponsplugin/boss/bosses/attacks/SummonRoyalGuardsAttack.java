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
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/** Summons armed adds that target whoever the boss is currently pressuring. Cleared on every phase change. */
public final class SummonRoyalGuardsAttack extends BossAttack {

    private final int guardCount;
    private final int maxAdds;
    private final int telegraphTicks;

    public SummonRoyalGuardsAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.guardCount = configInt("summon-royal-guards-count", 2);
        this.maxAdds = configInt("summon-royal-guards-max-adds", 4);
        this.telegraphTicks = configInt("summon-royal-guards-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Summon Royal Guards";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("summon-royal-guards-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.ring(origin, Particle.SOUL, 2.8, 24);
                    Fx.coloredRing(origin, Color.fromRGB(180, 180, 200), 1.0f, 3.6, 30, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.fallen_king.summon", Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.15f, 0.8f);
                    AddManager adds = ctx.instance().addManager();
                    for (int i = 0; i < guardCount && adds.aliveCount() < maxAdds; i++) {
                        Location spawnAt = origin.clone().add(
                                ThreadLocalRandom.current().nextDouble(-2, 2), 0,
                                ThreadLocalRandom.current().nextDouble(-2, 2));
                        Fx.burst(spawnAt.clone().add(0, 1, 0), Particle.SOUL, 34, 0.4);
                        Fx.coloredBurst(spawnAt.clone().add(0, 1, 0), Color.fromRGB(180, 180, 200), 1.2f, 16, 0.4);
                        Fx.spinningIcon(plugin, spawnAt.clone().add(0, 1.2, 0), Material.IRON_SWORD, 0.7f, 15, 30.0);
                        adds.spawn(origin.getWorld(), spawnAt, EntityType.ZOMBIE, entity -> {
                            entity.customName(Component.text("Royal Guard", NamedTextColor.GRAY));
                            entity.setCustomNameVisible(true);
                            EntityEquipment equipment = entity.getEquipment();
                            if (equipment != null) {
                                equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                                equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                                equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                            }
                            if (entity instanceof Zombie zombie) {
                                zombie.setTarget(ctx.target());
                            }
                        });
                    }
                },
                10, onComplete);
    }
}
