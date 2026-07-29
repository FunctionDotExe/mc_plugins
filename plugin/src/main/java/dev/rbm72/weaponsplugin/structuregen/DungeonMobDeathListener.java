package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Routes a dungeon grunt's death back to its {@link DungeonInstance} so the room-clear count drops. */
public final class DungeonMobDeathListener implements Listener {

    private final WeaponsPlugin plugin;

    public DungeonMobDeathListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        String dungeonId = pdc.get(DungeonInstance.dungeonIdKey(plugin), PersistentDataType.STRING);
        Integer roomIndex = pdc.get(DungeonInstance.roomIndexKey(plugin), PersistentDataType.INTEGER);
        if (dungeonId == null || roomIndex == null) {
            return;
        }
        DungeonInstance.onMobDeath(UUID.fromString(dungeonId), mob, roomIndex);
    }
}
