package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.meter.MeterConditions;
import dev.rbm72.weaponsplugin.boss.meter.MeterSpec;
import dev.rbm72.weaponsplugin.boss.meter.MeterThresholds;
import dev.rbm72.weaponsplugin.boss.meter.PlayerMeter;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * <b>P3 — The Charge (76–64%) · removes safety in numbers.</b> Storm Tyrant's conduction, inverted into a
 * social problem: every player accumulates charge, and it climbs faster near another charged player —
 * expressed with {@link MeterSpec.Contagion}, the same primitive the roster already uses for
 * player-to-player spread, rather than a bespoke proximity loop. The group must spread to survive the
 * climb, but spreading means each player is individually loud and individually hunted — the two pressures
 * this phase is actually about.
 * <p>
 * The lightning rod is portable rather than fixed: the arena drops one as an item, and planting it (a
 * real {@link org.bukkit.Material#LIGHTNING_ROD} block placed by a player) is what cures charge for
 * anyone standing near it — and planting it registers as loud through the shared vibration model, so
 * discharging the group is never a free, silent action.
 */
final class TheChargePhase extends WorldenderPhaseMechanic implements Listener {

    private PlayerMeter charge;

    TheChargePhase(BossInstance instance) {
        super(instance, "The Charge");
    }

    @Override
    protected void onArm() {
        WorldenderSupplies.lightningRod(fight);
        charge = buildChargeMeter();
        fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
    }

    @Override
    protected void onDisarm() {
        HandlerList.unregisterAll(this);
        if (charge != null) {
            instance.meters().detach(charge);
            charge = null;
        }
    }

    private PlayerMeter buildChargeMeter() {
        var config = fight.config();
        MeterSpec spec = MeterSpec.builder("worldender_charge", "Charge")
                .accent(WorldenderFight.STORM)
                .cap(100.0)
                .gain(MeterConditions.always(), config.dbl("charge-ambient-per-second", 3.0))
                .contagion(config.dbl("charge-arc-radius", 4.0), 0.55,
                        config.dbl("charge-arc-factor", 2.0), 0.0,
                        config.num("charge-arc-max-carriers", 4))
                .cure(MeterConditions.touchingBlock(Material.LIGHTNING_ROD),
                        config.dbl("charge-discharge-per-second", 60.0))
                .threshold(MeterThresholds.lightningRod(
                        config.dbl("charge-chain-radius", 5.0),
                        config.dbl("charge-strike-damage", 9.0),
                        config.dbl("charge-chain-damage", 6.0),
                        config.dbl("charge-chain-spike", 30.0)),
                        0.0)
                .thresholdCooldown(config.dbl("charge-threshold-cooldown-seconds", 6.0))
                .warnAt(0.6)
                .hints("spread out, or plant the rod", "CHARGING — you are near someone else charged")
                .build();
        return instance.meters().attach(spec);
    }

    /** Planting the rod is loud, and it is what cures charge for anyone standing near it afterwards. */
    @EventHandler
    public void onPlaceRod(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.LIGHTNING_ROD) {
            return;
        }
        if (!instance.arena().isInside(event.getBlock().getLocation())) {
            return;
        }
        fight.vibration().registerLoud(event.getPlayer(), fight.config().dbl("charge-plant-loudness", 1.4));
        Fx.coloredBurst(event.getBlock().getLocation().add(0.5, 1.0, 0.5), WorldenderFight.STORM, 1.4f, 26, 0.5);
        Fx.sound(event.getBlock().getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.2f);
    }

    @Override
    protected Component readoutText() {
        return Component.text("spread out — charge arcs between you", NamedTextColor.YELLOW);
    }
}
