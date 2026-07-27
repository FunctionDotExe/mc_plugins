package dev.rbm72.weaponsplugin.stone;

import dev.rbm72.weaponsplugin.commands.OpCooldownCommand;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Contributes each equipped stone's active-ability cooldown to the merged action bar. Was its own
 * repeating task writing its own line, which is exactly why using a movement stone while a weapon
 * cooldown was running made the bar strobe — see {@link ActionBarHub}.
 */
public final class StoneActionBarSource implements ActionBarHub.Source {

    private final StoneManager manager;
    private final OpCooldownCommand opCooldown;

    public StoneActionBarSource(StoneManager manager, OpCooldownCommand opCooldown) {
        this.manager = manager;
        this.opCooldown = opCooldown;
    }

    @Override
    public List<Component> segments(Player player) {
        boolean bypass = opCooldown.hasBypass(player);
        List<Component> parts = new ArrayList<>();
        for (Stone stone : manager.equipped(player)) {
            if (!stone.showsCooldownStatus()) {
                continue;
            }
            double remaining = manager.remainingCooldownSeconds(player, stone, bypass);
            parts.add(stone.actionBarStatus(player, remaining > 0, remaining));
        }
        return parts;
    }
}
