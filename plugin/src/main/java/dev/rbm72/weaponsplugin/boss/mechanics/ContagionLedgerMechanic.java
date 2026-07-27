package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ledger: every blow you land on the boss writes infection into <em>your</em> name, and the room
 * settles the account when the total comes due. The burst hits everyone, but it hits each player in
 * proportion to what they personally owe — so the group that funnels all its damage through one
 * player is the group that gets one player killed.
 * <p>
 * Personal debt only bleeds off while you are <em>not</em> hitting it. That makes this a rotation
 * mechanic rather than a throttle: the answer is not "everyone stop attacking" (which is the Fallen
 * King's wrath meter, and repeating it would be the exact failure this rework exists to fix) but
 * "take turns" — someone peels off to let their stacks decay while the rest keep the pressure on.
 * Both sides run continuously, alongside completely ordinary combat.
 * <p>
 * Solo: a lone player owns the whole ledger and simply has to pace themselves, which is survivable
 * but slow — the intended cost of bringing nobody.
 */
public final class ContagionLedgerMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 5L;
    private static final int BAR_SEGMENTS = 14;
    /** Grace period after a hit before that player's own stacks start bleeding off again. */
    private static final long DECAY_DELAY_MS = 2500L;

    private final Color color;
    private final double stacksPerDamage;
    private final double decayPerSecond;
    private final double ledgerCap;
    private final double burstBaseDamage;
    private final double burstDamagePerStack;
    private final int sicknessTicks;
    private final double bossHealPerUnpaidStack;

    private final Map<UUID, Double> debt = new HashMap<>();
    private final Map<UUID, Long> lastHitAtMs = new HashMap<>();
    private double ledger;

    public ContagionLedgerMechanic(BossInstance instance, Color color, double stacksPerDamage,
                                    double decayPerSecond, double ledgerCap, double burstBaseDamage,
                                    double burstDamagePerStack, int sicknessTicks,
                                    double bossHealPerUnpaidStack) {
        super(instance, TICK_INTERVAL);
        this.color = color;
        this.stacksPerDamage = stacksPerDamage;
        this.decayPerSecond = decayPerSecond;
        this.ledgerCap = Math.max(1.0, ledgerCap);
        this.burstBaseDamage = burstBaseDamage;
        this.burstDamagePerStack = burstDamagePerStack;
        this.sicknessTicks = sicknessTicks;
        this.bossHealPerUnpaidStack = bossHealPerUnpaidStack;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text("THE CONTAGION LEDGER", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Every wound you deal is written against your name", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 1.2f, 0.6f);
    }

    @Override
    protected void onStop() {
        debt.clear();
        lastHitAtMs.clear();
        ledger = 0.0;
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (stopped || damageDealt <= 0 || attacker == null) {
            return;
        }
        double added = damageDealt * stacksPerDamage;
        debt.merge(attacker.getUniqueId(), added, Double::sum);
        lastHitAtMs.put(attacker.getUniqueId(), System.currentTimeMillis());
        ledger = Math.min(ledgerCap, ledger + added);

        // Landing hits while the room still has headroom is exactly the engagement the phase floor is
        // asking about. Once the ledger is nearly full, more damage is the mistake, not the answer.
        if (ledger < ledgerCap * 0.75) {
            instance.recordExposure();
        }

        Fx.coloredBurst(attacker.getLocation().add(0, 1.6, 0), color, 0.8f, 5, 0.25);
        if (ledger >= ledgerCap) {
            settle();
        }
    }

    @Override
    protected void tick() {
        long now = System.currentTimeMillis();
        double step = TICK_INTERVAL / 20.0;
        List<Player> present = combatants();

        double total = 0.0;
        for (Player player : present) {
            UUID id = player.getUniqueId();
            double owed = debt.getOrDefault(id, 0.0);
            Long last = lastHitAtMs.get(id);
            boolean cooling = last == null || now - last >= DECAY_DELAY_MS;
            if (cooling && owed > 0) {
                owed = Math.max(0.0, owed - decayPerSecond * step);
                debt.put(id, owed);
                if (elapsedTicks % 20 == 0 && owed > 0) {
                    Fx.burst(player.getLocation().add(0, 1.4, 0), Particle.SPORE_BLOSSOM_AIR, 3, 0.25);
                }
            }
            total += owed;
        }

        // The ledger is the sum of what is actually owed by people still in the room — someone leaving
        // must not leave a phantom balance that detonates on the players who stayed.
        ledger = Math.min(ledgerCap, total);
        debt.keySet().removeIf(id -> present.stream().noneMatch(p -> p.getUniqueId().equals(id)));
        lastHitAtMs.keySet().retainAll(debt.keySet());

        if (ledger >= ledgerCap) {
            settle();
        }
        showBars();
    }

    private void showBars() {
        double fraction = Math.min(1.0, ledger / ledgerCap);
        instance.mechanicBar().update(instance.barViewers(), player -> {
            double owed = debt.getOrDefault(player.getUniqueId(), 0.0);
            int filled = (int) Math.round(BAR_SEGMENTS * fraction);
            NamedTextColor tone = fraction > 0.8 ? NamedTextColor.RED
                    : fraction > 0.5 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
            Component text = Component.text("Ledger  ", NamedTextColor.WHITE)
                    .append(Component.text("▮".repeat(filled), tone))
                    .append(Component.text("▯".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                    .append(Component.text("   your debt " + (int) Math.round(owed),
                            owed > ledgerCap * 0.35 ? NamedTextColor.RED : NamedTextColor.GRAY))
                    .append(Component.text("  (stop swinging to shed it)", NamedTextColor.DARK_GRAY));
            BossBar.Color barColor = fraction > 0.8 ? BossBar.Color.RED
                    : fraction > 0.5 ? BossBar.Color.YELLOW : BossBar.Color.GREEN;
            return MechanicBar.Readout.of(text, fraction, barColor);
        });
    }

    /**
     * The account comes due. Each player pays their own share, the ledger is wiped, and whatever the
     * room was carrying feeds the boss a little — so letting it fill is strictly worse than pacing it.
     */
    private void settle() {
        double carried = ledger;
        ledger = 0.0;

        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.6f, 70, 1.1);
        Fx.expandingRings(plugin, loc, Particle.SPORE_BLOSSOM_AIR, Math.min(14.0, instance.arena().radius() * 0.6), 4, 2L);
        Fx.sound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.4f, 0.5f);
        instance.showTitle(
                Component.text("THE ACCOUNT SETTLES", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Everyone pays what they owe", NamedTextColor.GRAY));

        for (Player player : combatants()) {
            double owed = debt.getOrDefault(player.getUniqueId(), 0.0);
            double damage = burstBaseDamage + owed * burstDamagePerStack;
            hurt(player, damage);
            if (player.isValid() && !player.isDead()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, sicknessTicks, 1));
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 1.6f, 24, 0.5);
            }
        }
        debt.clear();
        lastHitAtMs.clear();

        if (bossHealPerUnpaidStack > 0) {
            var attr = instance.entity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
            instance.entity().setHealth(Math.min(cap,
                    instance.entity().getHealth() + carried * bossHealPerUnpaidStack));
        }
    }
}
