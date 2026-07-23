package dev.rbm72.weaponsplugin.ability;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks each player's active summoned allies so passives can find and buff them. */
public final class SummonManager {

    private final Map<UUID, List<Mob>> summons = new HashMap<>();

    public void add(Player owner, Mob mob) {
        summons.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(mob);
    }

    /** Live summons only — dead or removed entities are pruned before returning. */
    public List<Mob> activeSummons(Player owner) {
        List<Mob> list = summons.get(owner.getUniqueId());
        if (list == null) {
            return List.of();
        }
        list.removeIf(mob -> !mob.isValid());
        return list;
    }
}
