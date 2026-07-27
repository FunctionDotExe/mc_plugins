package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P3's optional objective: real end crystals on pillars. Vanilla already makes attacking one explode it
 * — real fire, no scripted "detonate" step needed — so this class only has to place them, recognise its
 * own crystals dying, and pay out the trade the design asks for: destroying one shortens the interval
 * between the real Sovereign's tell (making the phantom puzzle easier) but costs more of the arena's
 * remaining floor. Batch-1 §5.3: "a genuine risk/reward dilemma rather than a chore".
 */
final class EndCrystals {

    private final SovereignFight fight;
    private final Set<UUID> crystalIds = new HashSet<>();
    private final List<Block> pillars = new ArrayList<>();
    private Handler handler;

    EndCrystals(SovereignFight fight) {
        this.fight = fight;
    }

    void build() {
        if (!crystalIds.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        boolean solo = fight.playerCount() <= 1;
        int count = fight.config().num("crystal-count", solo ? 2 : 4);
        double fraction = fight.config().dbl("crystal-fraction", 0.65);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block pillar = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            Grief.setMechanicBlock(fight.griefContext(), pillar, Material.OBSIDIAN);
            pillars.add(pillar);
            EnderCrystal crystal = world.spawn(spot.clone().add(0.5, 1, 0.5), EnderCrystal.class);
            crystal.setShowingBottom(true);
            crystal.setPersistent(false);
            fight.instance().trackEntity(crystal);
            crystalIds.add(crystal.getUniqueId());
        }
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    int liveCount() {
        int live = 0;
        for (UUID id : crystalIds) {
            var entity = fight.plugin().getServer().getEntity(id);
            if (entity != null && entity.isValid()) {
                live++;
            }
        }
        return live;
    }

    int total() {
        return crystalIds.size();
    }

    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()));
        return spot;
    }

    void discard() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        for (UUID id : crystalIds) {
            var entity = fight.plugin().getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        crystalIds.clear();
        pillars.clear();
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(EntityDeathEvent event) {
            if (!crystalIds.remove(event.getEntity().getUniqueId())) {
                return;
            }
            Location at = event.getEntity().getLocation();
            fight.phantoms().shortenTellInterval();
            fight.rifts().open(at, fight.config().dbl("crystal-break-rift-radius", 3.5));
            Fx.coloredBurst(at.clone().add(0, 1, 0), SovereignFight.VOID_PURPLE, 2.6f, 60, 1.0);
            for (var player : fight.combatants()) {
                fight.plugin().actionBarHub().flash(player,
                        Component.text("A CRYSTAL FALLS — the tell comes faster now", NamedTextColor.LIGHT_PURPLE),
                        2400L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
            }
        }
    }
}
