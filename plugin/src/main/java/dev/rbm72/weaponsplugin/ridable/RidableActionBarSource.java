package dev.rbm72.weaponsplugin.ridable;

import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Contributes the mounted saddle's ability name and cooldown to the merged action bar while a
 * player is riding a tracked mount. Nothing at all when they're on foot — see {@link ActionBarHub}.
 */
public final class RidableActionBarSource implements ActionBarHub.Source {

    private final RidableManager manager;

    public RidableActionBarSource(RidableManager manager) {
        this.manager = manager;
    }

    @Override
    public List<Component> segments(Player player) {
        Optional<Ridable> ridable = manager.mountedRidable(player);
        if (ridable.isEmpty()) {
            return List.of();
        }
        double remaining = manager.remainingAbilityCooldownSeconds(player);
        boolean onCooldown = remaining > 0;
        return List.of(Component.text(ridable.get().abilityName() + " ", NamedTextColor.AQUA)
                .append(Component.text(
                        onCooldown ? String.format(Locale.ROOT, "%.1fs", remaining) : "READY",
                        onCooldown ? NamedTextColor.YELLOW : NamedTextColor.GREEN)));
    }
}
