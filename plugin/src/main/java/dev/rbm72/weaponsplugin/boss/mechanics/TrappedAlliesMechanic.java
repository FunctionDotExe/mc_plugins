package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The boss takes hostages — several at once — and starts killing them slowly while the fight carries
 * on around it. Allies free a captive by standing with them; nobody is locked out of anything, and
 * the boss never stops being hittable.
 * <p>
 * That last part is the whole difference between this and the old {@code RescueGate} it replaces.
 * The gate made the boss invulnerable while it held someone, which turned the rescue into a chore
 * with the fight paused around it: walk over, stand still, wait, resume. Here the boss keeps swinging
 * and keeps taking damage, so the group is genuinely choosing between damage and their teammates'
 * lives every second the hold lasts, and choosing wrong costs a life rather than a few seconds.
 * <p>
 * Solo behaviour is explicit: a lone captive channels their own escape at a reduced rate, so it is
 * always survivable alone and always slower than having help.
 */
public final class TrappedAlliesMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    /** How close an ally has to stand to count as working on a captive's release. */
    private static final double RESCUE_RADIUS = 3.2;

    private final String label;
    private final Color color;
    private final Material pillarMaterial;
    private final Sound captureSound;
    private final int captiveCount;
    private final int firstCaptureDelayTicks;
    private final int recaptureDelayTicks;
    private final int suffocateTicks;
    private final int rescueTicksNeeded;
    private final double damagePerSecondHeld;
    private final double failDamage;
    private final double bossHealOnDeath;

    private final Map<UUID, Captive> captives = new LinkedHashMap<>();
    private int cooldownTicks;

    private static final class Captive {
        final Player player;
        final Location anchor;
        final List<Display> props = new ArrayList<>();
        int heldTicks;
        int rescueProgress;

        Captive(Player player, Location anchor) {
            this.player = player;
            this.anchor = anchor;
        }
    }

    public TrappedAlliesMechanic(BossInstance instance, String label, Color color, Material pillarMaterial,
                                  Sound captureSound, int captiveCount, int firstCaptureDelayTicks,
                                  int recaptureDelayTicks, int suffocateTicks, int rescueTicksNeeded,
                                  double damagePerSecondHeld, double failDamage, double bossHealOnDeath) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.pillarMaterial = pillarMaterial;
        this.captureSound = captureSound;
        this.captiveCount = Math.max(1, captiveCount);
        this.firstCaptureDelayTicks = Math.max(1, firstCaptureDelayTicks);
        this.recaptureDelayTicks = Math.max(20, recaptureDelayTicks);
        this.suffocateTicks = Math.max(20, suffocateTicks);
        this.rescueTicksNeeded = Math.max(1, rescueTicksNeeded);
        this.damagePerSecondHeld = damagePerSecondHeld;
        this.failDamage = failDamage;
        this.bossHealOnDeath = bossHealOnDeath;
    }

    @Override
    protected void onStart() {
        cooldownTicks = firstCaptureDelayTicks;
        instance.showTitle(
                Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("It will take your allies — go and get them out", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        for (Captive captive : captives.values()) {
            release(captive);
        }
        captives.clear();
    }

    @Override
    protected void tick() {
        if (captives.isEmpty()) {
            cooldownTicks -= TICK_INTERVAL;
            if (cooldownTicks <= 0) {
                seize();
            }
            instance.mechanicBar().clear();
            return;
        }

        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Captive> entry : captives.entrySet()) {
            Captive captive = entry.getValue();
            Player player = captive.player;

            if (!player.isOnline() || !player.isValid() || player.isDead()) {
                release(captive);
                finished.add(entry.getKey());
                continue;
            }

            // A lone captive can dig themselves out, just slowly — otherwise a solo fight deadlocks
            // here with nobody alive who could ever reach them.
            boolean alone = Arena.combatants(instance.entity().getLocation(), instance.arena().radius())
                    .stream().noneMatch(p -> !p.equals(player));
            int rescuers = alone ? 1 : (int) Arena.combatants(player.getLocation(), RESCUE_RADIUS).stream()
                    .filter(p -> !p.equals(player) && !captives.containsKey(p.getUniqueId()))
                    .count();

            if (rescuers > 0) {
                // Solo self-release runs at half rate; extra hands genuinely speed it up.
                captive.rescueProgress += alone ? TICK_INTERVAL : (int) TICK_INTERVAL * rescuers;
                Fx.coloredBurst(player.getLocation().add(0, 1.0, 0), Color.fromRGB(255, 220, 140), 0.9f, 5, 0.25);
            }

            captive.heldTicks += TICK_INTERVAL;
            if (captive.heldTicks % 20 == 0) {
                tickHurt(player, damagePerSecondHeld);
                Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 1.4f);
            }
            if (elapsedTicks % 6 == 0) {
                Fx.coloredRing(captive.anchor, color, 1.4f, 1.8, 14, elapsedTicks * 0.12);
            }

            if (captive.rescueProgress >= rescueTicksNeeded) {
                freed(captive);
                finished.add(entry.getKey());
            } else if (captive.heldTicks >= suffocateTicks) {
                suffocated(captive);
                finished.add(entry.getKey());
            }
        }
        finished.forEach(captives::remove);

        if (captives.isEmpty()) {
            cooldownTicks = recaptureDelayTicks;
        }
        showBars();
    }

    private void showBars() {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            Captive own = captives.get(viewer.getUniqueId());
            if (own != null) {
                double left = 1.0 - Math.min(1.0, own.heldTicks / (double) suffocateTicks);
                Component text = Component.text("HELD — ", NamedTextColor.RED)
                        .append(Component.text(Math.max(0, (suffocateTicks - own.heldTicks) / 20) + "s of air",
                                NamedTextColor.WHITE))
                        .append(Component.text("   call for help", NamedTextColor.GRAY));
                return MechanicBar.Readout.of(text, left, BossBar.Color.RED);
            }
            // Everyone else sees the most urgent captive, so the whole room shares one deadline.
            Captive worst = null;
            for (Captive candidate : captives.values()) {
                if (worst == null || candidate.heldTicks > worst.heldTicks) {
                    worst = candidate;
                }
            }
            if (worst == null) {
                return null;
            }
            double progress = Math.min(1.0, worst.rescueProgress / (double) rescueTicksNeeded);
            Component text = Component.text(captives.size() + " held  ", NamedTextColor.AQUA)
                    .append(Component.text(worst.player.getName(), NamedTextColor.WHITE))
                    .append(Component.text("  " + Math.max(0, (suffocateTicks - worst.heldTicks) / 20) + "s left",
                            NamedTextColor.RED))
                    .append(Component.text("   stand with them to break it", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, progress, BossBar.Color.BLUE);
        });
    }

    private void seize() {
        List<Player> candidates = new ArrayList<>(combatants());
        if (candidates.isEmpty()) {
            cooldownTicks = recaptureDelayTicks;
            return;
        }
        int take = Math.min(captiveCount, Math.max(1, candidates.size() - (candidates.size() > 1 ? 1 : 0)));
        for (int i = 0; i < take && !candidates.isEmpty(); i++) {
            Player seized = candidates.remove(ThreadLocalRandom.current().nextInt(candidates.size()));
            Location anchor = seized.getLocation().clone();
            Captive captive = new Captive(seized, anchor);
            hold(seized, suffocateTicks + 20);
            raiseProps(captive, suffocateTicks + 20);
            captives.put(seized.getUniqueId(), captive);

            Fx.coloredBurst(anchor.clone().add(0, 1.2, 0), color, 2.0f, 45, 0.7);
            Fx.sound(anchor, captureSound, 1.1f, 0.6f);
        }
        instance.showTitle(
                Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text(captives.size() + " taken — cut them free before they suffocate", NamedTextColor.GRAY));
    }

    private void freed(Captive captive) {
        release(captive);
        instance.recordExposure();
        Location at = captive.player.getLocation();
        Fx.coloredBurst(at.clone().add(0, 1, 0), color, 2.2f, 45, 0.7);
        Fx.flash(at.clone().add(0, 1, 0), 1);
        Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.3f);
    }

    /**
     * The captive eats the full hit and the boss takes a bite out of what it drained. Deliberately
     * worth nothing to the group — abandoning a teammate must never be the efficient play.
     */
    private void suffocated(Captive captive) {
        Player player = captive.player;
        release(captive);
        hurt(player, failDamage);
        if (player.isValid() && !player.isDead()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 2.4f, 50, 0.7);
        }
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_PLAYER_HURT, 1.2f, 0.5f);
        if (bossHealOnDeath > 0 && instance.entity().isValid()) {
            var attr = instance.entity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
            instance.entity().setHealth(Math.min(cap, instance.entity().getHealth() + bossHealOnDeath));
        }
        instance.showTitle(
                Component.text("SUFFOCATED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("You left them in there", NamedTextColor.GRAY));
    }

    /**
     * Pins without teleport-locking: crippling slowness plus a jump-boost level high enough to zero
     * out jumping. Leaves the captive able to look around and call for help, which a hard freeze
     * would not.
     */
    private void hold(Player player, int durationTicks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 6));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, 6));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks, 128));
    }

    private void release(Captive captive) {
        Player player = captive.player;
        if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
        for (Display prop : captive.props) {
            if (prop.isValid()) {
                prop.remove();
            }
        }
        captive.props.clear();
    }

    /** Glowing pillars, not real blocks — the captive is visible across the arena without griefing the floor. */
    private void raiseProps(Captive captive, int durationTicks) {
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 * i;
            Location spot = captive.anchor.clone().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            Display pillar = Fx.glowPillar(plugin, spot, pillarMaterial, 0.4f, 2.6f, durationTicks);
            if (pillar != null) {
                captive.props.add(pillar);
                instance.trackEntity(pillar);
            }
        }
    }
}
