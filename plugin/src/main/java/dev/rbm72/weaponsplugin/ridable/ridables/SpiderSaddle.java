package dev.rbm72.weaponsplugin.ridable.ridables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.List;

/** Rides any live Spider. Web Trap: drops a short-lived patch of cobwebs where you're looking. */
public final class SpiderSaddle extends Ridable {

    private static final double RANGE = 10.0;
    private static final long WEB_DURATION_TICKS = 100L;

    public SpiderSaddle(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "spider_saddle";
    }

    @Override
    public Material material() {
        return Material.COBWEB;
    }

    @Override
    public String displayNameText() {
        return "Spider Saddle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public EntityType targetEntityType() {
        return EntityType.SPIDER;
    }

    /**
     * Spiders climb walls/ceilings in vanilla, which the ground-hugging raycast the movement task
     * uses for everything else can't represent — free vertical control (like the fliers) is a much
     * closer approximation of "goes wherever it wants" than trying to detect climbable surfaces.
     */
    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Tames and lets you ride any live", NamedTextColor.GRAY),
                Component.text("Spider you find. Climbs walls freely.", NamedTextColor.GRAY));
    }

    @Override
    public String abilityName() {
        return "Web Trap";
    }

    @Override
    public List<Component> abilityLore() {
        return List.of(Component.text("Drop a patch of cobwebs where you're looking.", NamedTextColor.GRAY));
    }

    @Override
    public double abilityCooldownSeconds() {
        return 12.0;
    }

    @Override
    public void ability(Player rider, LivingEntity mount) {
        RayTraceResult trace = rider.getWorld().rayTraceBlocks(rider.getEyeLocation(), rider.getEyeLocation().getDirection(), RANGE);
        Location target = trace != null && trace.getHitBlock() != null
                ? trace.getHitBlock().getLocation().add(0, 1, 0)
                : rider.getEyeLocation().add(rider.getEyeLocation().getDirection().multiply(RANGE));

        Block center = target.getBlock();
        placeTemporaryWeb(center);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            placeTemporaryWeb(center.getRelative(face));
        }

        Fx.burst(target, Particle.ITEM_COBWEB, 20, 0.5);
        Fx.sound(target, Sound.BLOCK_COBWEB_PLACE, 1.0f, 1.0f);
    }

    private void placeTemporaryWeb(Block block) {
        if (!block.getType().isAir()) {
            return;
        }
        BlockData previous = block.getBlockData();
        block.setType(Material.COBWEB);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (block.getType() == Material.COBWEB) {
                    block.setBlockData(previous);
                }
            }
        }.runTaskLater(plugin, WEB_DURATION_TICKS);
    }
}
