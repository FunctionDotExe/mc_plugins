package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Several players are marked at once and given a few seconds to get away from each other. When the
 * timer lands, every mark detonates for damage scaled by how many people were standing near it.
 * <p>
 * This is the sharp, scripted version of what the chain and cone mechanics do continuously, and it
 * earns its place as an event because it is the one thing a clumped group cannot solve by out-healing
 * or out-damaging it — the only answer is to physically scatter, right now, which breaks whatever
 * comfortable formation the fight had settled into and leaves everyone repositioning afterwards.
 * <p>
 * It deliberately does not stop the fight. Five seconds of "run away from your friends" is only a
 * real decision while the boss is still swinging; frozen, it would be a free chore.
 */
public final class SpreadCheckEvent extends ScriptedEvent {

    private static final Color MARK_COLOR = Color.fromRGB(255, 90, 90);

    private final int windowTicks;
    private final double minSpacing;
    private final double baseDamage;
    private final double damagePerNeighbour;
    private final int markedCount;

    private final Set<UUID> marked = new HashSet<>();

    public SpreadCheckEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId, triggers);
        this.windowTicks = configInt("spread-window-ticks", 100);
        this.minSpacing = configDouble("spread-min-spacing", 7.0);
        this.baseDamage = configDouble("spread-base-damage", 8.0);
        this.damagePerNeighbour = configDouble("spread-damage-per-neighbour", 12.0);
        this.markedCount = configInt("spread-marked-count", 3);
    }

    @Override
    public String id() {
        return "spread_check";
    }

    @Override
    public boolean blocksCombat() {
        return false;
    }

    @Override
    protected int durationTicks() {
        return windowTicks;
    }

    @Override
    protected boolean begin(BossInstance instance) {
        marked.clear();
        List<Player> present = new ArrayList<>(combatants(instance));
        if (present.isEmpty()) {
            return false;
        }
        Collections.shuffle(present);
        int take = Math.min(Math.max(1, markedCount), present.size());
        for (int i = 0; i < take; i++) {
            Player player = present.get(i);
            marked.add(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, windowTicks + 20, 0, false, false));
            Fx.coloredBurst(player.getLocation().add(0, 2.2, 0), MARK_COLOR, 2.0f, 30, 0.4);
        }

        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
        instance.showTitle(
                Component.text("SKY FRACTURE", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Marked — get away from everyone", NamedTextColor.GRAY));
        return true;
    }

    @Override
    protected void tick(BossInstance instance, int ticks) {
        int secondsLeft = Math.max(0, (windowTicks - ticks) / 20);
        for (Player player : combatants(instance)) {
            if (!marked.contains(player.getUniqueId())) {
                continue;
            }
            Fx.coloredRing(player.getLocation(), MARK_COLOR, 1.4f, minSpacing, 24, ticks * 0.12);
            if (ticks % 20 == 0) {
                Fx.sound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f,
                        0.6f + 0.8f * (ticks / (float) Math.max(1, windowTicks)));
            }
        }
        if (ticks % 4 == 0) {
            showBars(instance, secondsLeft);
        }
    }

    private void showBars(BossInstance instance, int secondsLeft) {
        instance.mechanicBar().update(MechanicBar.Owner.EVENT, instance.barViewers(), viewer -> {
            int neighbours = neighboursOf(instance, viewer);
            boolean isMarked = marked.contains(viewer.getUniqueId());
            Component text = Component.text("SKY FRACTURE  ", NamedTextColor.RED)
                    .append(isMarked
                            ? Component.text("YOU ARE MARKED", NamedTextColor.RED)
                            : Component.text("stay clear of the marked", NamedTextColor.WHITE))
                    .append(Component.text("   " + neighbours + " too close", neighbours > 0
                            ? NamedTextColor.RED : NamedTextColor.GREEN))
                    .append(Component.text("   " + secondsLeft + "s", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text,
                    1.0 - Math.min(1.0, neighbours / 3.0),
                    neighbours > 0 ? BossBar.Color.RED : BossBar.Color.GREEN);
        });
    }

    private int neighboursOf(BossInstance instance, Player player) {
        int count = 0;
        for (Player other : combatants(instance)) {
            if (other.equals(player)) {
                continue;
            }
            if (flatDistance(player.getLocation(), other.getLocation()) <= minSpacing) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void expire(BossInstance instance) {
        boolean anyoneClean = false;
        for (Player player : combatants(instance)) {
            if (!marked.contains(player.getUniqueId())) {
                continue;
            }
            int neighbours = neighboursOf(instance, player);
            Location at = player.getLocation();
            Fx.coloredBurst(at.clone().add(0, 1.2, 0), MARK_COLOR, 2.6f, 60, minSpacing * 0.3);
            Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.9f);

            double damage = baseDamage + damagePerNeighbour * neighbours;
            hurt(instance, player, damage);
            for (Player other : combatants(instance)) {
                if (other.equals(player)) {
                    continue;
                }
                if (flatDistance(at, other.getLocation()) <= minSpacing) {
                    hurt(instance, other, damage * 0.6);
                }
            }
            if (neighbours == 0) {
                anyoneClean = true;
            }
        }
        if (anyoneClean) {
            // Somebody actually spread — the group engaged with what the event asked for.
            instance.recordExposure();
        }
        instance.showTitle(
                anyoneClean
                        ? Component.text("SCATTERED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true)
                        : Component.text("YOU STAYED TOGETHER", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text(anyoneClean ? "Most of it hit nothing" : "It cost you", NamedTextColor.GRAY));
    }

    @Override
    protected void onCleanup(BossInstance instance) {
        for (Player player : combatants(instance)) {
            if (marked.contains(player.getUniqueId()) && player.isOnline()) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        marked.clear();
    }
}
