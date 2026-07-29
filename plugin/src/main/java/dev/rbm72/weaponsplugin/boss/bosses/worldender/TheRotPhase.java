package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.meter.MeterConditions;
import dev.rbm72.weaponsplugin.boss.meter.MeterSpec;
import dev.rbm72.weaponsplugin.boss.meter.MeterThresholds;
import dev.rbm72.weaponsplugin.boss.meter.PlayerMeter;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P5 — The Rot (52–40%) · removes healing.</b> Plague Warden's attrition, compressed into one sharp
 * idea: healing above a low per-player cap converts to Rot instead of health ({@link
 * MeterSpec#convertHealing}), and Rot both slows a player and makes them louder — the group's safety net
 * and their silence both cost the same currency. The cure is a real, stationary lit campfire — a pyre the
 * group must return to, which conflicts with every other phase's demand to keep moving.
 */
final class TheRotPhase extends WorldenderPhaseMechanic {

    private PlayerMeter rot;

    TheRotPhase(BossInstance instance) {
        super(instance, "The Rot");
    }

    @Override
    protected void onArm() {
        WorldenderSupplies.pyreKit(fight);
        placePyres();
        rot = buildRotMeter();
    }

    @Override
    protected void onDisarm() {
        if (rot != null) {
            instance.meters().detach(rot);
            rot = null;
        }
    }

    private void placePyres() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = fight.config().num("rot-pyre-count", 2);
        double fraction = fight.config().dbl("rot-pyre-fraction", 0.4);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.CAMPFIRE)) {
                if (block.getBlockData() instanceof Lightable lightable) {
                    lightable.setLit(true);
                    block.setBlockData(lightable, false);
                }
                Fx.coloredBurst(spot.clone().add(0, 1, 0), WorldenderFight.PLAGUE, 1.6f, 24, 0.5);
            }
        }
    }

    private PlayerMeter buildRotMeter() {
        var config = fight.config();
        MeterSpec spec = MeterSpec.builder("worldender_rot", "Rot")
                .accent(WorldenderFight.PLAGUE)
                .cap(100.0)
                .convertHealing(config.dbl("rot-heal-cap-per-second", 2.0),
                        config.dbl("rot-heal-burst-seconds", 1.5),
                        config.dbl("rot-meter-per-health", 4.0))
                .cure(MeterConditions.nearLitCampfire(config.dbl("rot-pyre-radius", 3.5)),
                        config.dbl("rot-cure-per-second", 40.0))
                .threshold(MeterThresholds.hit(config.dbl("rot-threshold-damage", 12.0)), 0.3)
                .thresholdCooldown(config.dbl("rot-threshold-cooldown-seconds", 8.0))
                .warnAt(0.5)
                .hints("get back to the pyre", "ROTTING — slower, and louder")
                .build();
        return instance.meters().attach(spec);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        if (rot == null) {
            return;
        }
        double slowThreshold = fight.config().dbl("rot-slow-fraction", 0.5);
        for (Player player : combatants()) {
            if (rot.fraction(player) < slowThreshold) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, intervalTicks + 5, 0, false, false));
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                fight.vibration().registerLoud(player, fight.config().dbl("rot-louder-loudness", 0.6));
            }
        }
    }

    @Override
    protected Component readoutText() {
        return Component.text("healing rots — return to the pyre", NamedTextColor.DARK_GREEN);
    }
}
