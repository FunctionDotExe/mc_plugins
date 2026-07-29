package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The breath system — and the one place in this boss where the deliberate choice is to build
 * <em>nothing</em> extra.
 * <p>
 * Batch-2 §3.4 names the tell explicitly: "real vanilla bubble meter". Vanilla already does everything
 * the Air row of the mechanics table asks for, for free, the instant {@link WaterLevel} puts real water
 * over the arena: a submerged player's {@code Player#getRemainingAir()} depletes on its own, and
 * drowning damage at zero air is armour-irrelevant and not something a heal reverses — which is exactly
 * "unhealable in practice". A parallel {@code PlayerMeter} skin (Chill/Static Charge/Infection/Void
 * Echo's shared system, see {@code boss.meter}) was considered and rejected: it would draw a second bar
 * showing a second number for the same thing the real bubble icons above the hotbar already show,
 * which is precisely the "phantom parallel meter with no visible bar" failure mode the task brief warns
 * against. Reading the vanilla state directly means there is only ever one air readout in this fight,
 * and it is the one every Minecraft player already knows how to read.
 * <p>
 * The only custom lever left is the conduit's actual grant: real vanilla conduits activate off a
 * specific prismarine/sea-lantern frame (16-42 blocks depending on shape) that would be a large, fragile
 * structure to place, track and restore mid-fight for no gameplay benefit — the frame's exact
 * completeness is not something a player under pressure could read at a glance anyway. Instead, this
 * class grants the real {@link PotionEffectType#CONDUIT_POWER} status effect directly to anyone near an
 * active conduit, each pulse. It is still the authentic vanilla effect (the same icon, the same
 * underwater-breathing/see-clearly behaviour) — only its trigger is simplified.
 */
final class Air {

    private final LeviathanFight fight;

    Air(LeviathanFight fight) {
        this.fight = fight;
    }

    void pulse(int intervalTicks) {
        int durationTicks = intervalTicks + 40;
        for (Player player : fight.combatants()) {
            if (fight.conduits().isNearActiveConduit(player.getLocation())) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, durationTicks, 0, true, false, false));
            }
        }
    }
}
