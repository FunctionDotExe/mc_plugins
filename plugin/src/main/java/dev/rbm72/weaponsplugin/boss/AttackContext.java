package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Everything a {@link BossAttack} needs to run once, bundled so nothing reaches for global state. */
public final class AttackContext {

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final Player target;

    public AttackContext(WeaponsPlugin plugin, BossInstance instance, Player target) {
        this.plugin = plugin;
        this.instance = instance;
        this.target = target;
    }

    public WeaponsPlugin plugin() {
        return plugin;
    }

    public BossInstance instance() {
        return instance;
    }

    /** The boss's current target. Never null when an attack is started. */
    public Player target() {
        return target;
    }

    public LivingEntity boss() {
        return instance.entity();
    }

    public Location bossLocation() {
        return instance.entity().getLocation();
    }

    public Arena arena() {
        return instance.arena();
    }
}
