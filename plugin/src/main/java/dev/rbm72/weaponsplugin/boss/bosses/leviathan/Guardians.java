package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P2-onward adds: real vanilla {@link EntityType#GUARDIAN}, spawned through {@link
 * dev.rbm72.weaponsplugin.boss.AddManager} (auto-despawned on phase change/fight end, per the
 * framework's own rule that a boss must never leave a stray mob behind).
 * <p>
 * No custom AI or beam code at all — a real guardian's charging-beam telegraph <em>is</em> the vanilla
 * behaviour batch-2 §3.4 asks for ("real guardians with the vanilla charging beam telegraph"), and
 * reimplementing it would only be a worse copy of the thing already in the game.
 */
final class Guardians {

    private final LeviathanFight fight;
    private final List<UUID> tracked = new ArrayList<>();

    Guardians(LeviathanFight fight) {
        this.fight = fight;
    }

    /** count = 1 + 1 per player (batch-2 §3.4). Tops up toward that target; never removes a live one. */
    void topUp() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        purgeDead();
        int desired = 1 + fight.playerCount();
        while (tracked.size() < desired) {
            spawnOne(world);
        }
    }

    private void purgeDead() {
        for (Iterator<UUID> it = tracked.iterator(); it.hasNext(); ) {
            var entity = Bukkit.getEntity(it.next());
            if (entity == null || !entity.isValid()) {
                it.remove();
            }
        }
    }

    private void spawnOne(World world) {
        Location centre = fight.instance().arena().center();
        double fraction = fight.config().dbl("guardian-spawn-fraction", 0.5);
        double dist = fight.instance().arena().radius() * fraction;
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        int floorY = fight.water().floorY();
        int height = Math.max(1, fight.config().num("water-full-submerge-height", 9)) - 1;
        Location at = centre.clone().add(Math.cos(angle) * dist,
                ThreadLocalRandom.current().nextInt(Math.max(1, height)), Math.sin(angle) * dist);

        LivingEntity guardian = fight.instance().addManager().spawn(world, at, EntityType.GUARDIAN, entity -> {
            entity.customName(Component.text("Abyssal Guardian", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            entity.setCustomNameVisible(false);
        });
        tracked.add(guardian.getUniqueId());
        Fx.coloredBurst(at, LeviathanFight.TEAL, 1.6f, 30, 0.6);
        Fx.burst(at, Particle.BUBBLE, 24, 0.5);
    }

    void discardAll() {
        tracked.clear();
    }
}
