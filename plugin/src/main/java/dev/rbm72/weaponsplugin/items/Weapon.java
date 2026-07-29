package dev.rbm72.weaponsplugin.items;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.util.TooltipFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Base type every weapon extends. Owns all the shared, easy-to-get-wrong
 * mechanics (item construction, identification, rarity scaling, lore
 * assembly) so each concrete weapon only has to describe itself and
 * implement whichever ability slots and passive hooks it uses. Ability2,
 * ability3, ultimate, and every passive hook default to no-ops so weapons
 * that only use ability1 need not override anything else.
 */
public abstract class Weapon {

    private static final String WEAPON_ID_KEY = "weapon_id";

    protected final WeaponsPlugin plugin;

    protected Weapon(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Flat melee damage bonus before rarity scaling. */
    public abstract double baseMeleeDamage();

    /** Right-click, main hand, not sneaking. */
    public abstract void ability1(Player player);

    public abstract double ability1CooldownSeconds();

    public abstract List<Component> ability1Lore();

    /** Short display name shown as the tooltip header for each ability. Empty = no header. */
    public String ability1Name() {
        return "";
    }

    public String ability2Name() {
        return "";
    }

    public String ability3Name() {
        return "";
    }

    public String ultimateName() {
        return "";
    }

    /**
     * True when ability1 fires from the vanilla spear lunge instead of from a bare right-click.
     * <p>
     * Spears are the one material in the game whose right-click is already a mechanic: holding it charges,
     * releasing it lunges the player forward. §0.1 says name the real Minecraft object rather than simulate
     * it, and the real object here is the lunge itself — so a spear weapon leaves the charge alone and hangs
     * its ability on the release, via {@link dev.rbm72.weaponsplugin.listeners.WeaponLungeListener}. The
     * interact listener sees this flag and stops cancelling the main-hand right-click, which is what lets
     * the charge happen at all.
     * <p>
     * The other three slots are unaffected: sneak, off-hand and sneak-off-hand still route through
     * {@code WeaponInteractListener} exactly as they do for a sword.
     */
    public boolean ability1OnLunge() {
        return false;
    }

    /**
     * Extra vanilla lunge power this weapon carries, added to whatever {@link org.bukkit.enchantments.Enchantment#LUNGE}
     * would give it.
     * <p>
     * Applied on every lunge, on cooldown or not: it is the weapon's <em>stat</em>, not its ability. A
     * player who just spent ability1 should still lunge like they are holding a great spear.
     */
    public int lungePowerBonus() {
        return 0;
    }

    /** Right-click, main hand, sneaking. No-op unless overridden. */
    public void ability2(Player player) {
    }

    public double ability2CooldownSeconds() {
        return 0;
    }

    public List<Component> ability2Lore() {
        return List.of();
    }

    /** Right-click, off hand, not sneaking. No-op unless overridden. */
    public void ability3(Player player) {
    }

    public double ability3CooldownSeconds() {
        return 0;
    }

    public List<Component> ability3Lore() {
        return List.of();
    }

    /** Right-click, off hand, sneaking. No-op unless overridden. */
    public void ultimate(Player player) {
    }

    public double ultimateCooldownSeconds() {
        return 0;
    }

    public List<Component> ultimateLore() {
        return List.of();
    }

    /**
     * How this weapon's ultimate is earned, or null — the default — for one gated purely on
     * {@link #ultimateCooldownSeconds()}.
     * <p>
     * Opt-in per weapon rather than roster-wide because the two gates say different things about a weapon.
     * A cooldown ultimate is a tool you plan around ("it's up in 40 seconds, save it for the adds"); a
     * charge ultimate is a payoff you build toward, and only suits a weapon whose normal use is the
     * building. Forcing every weapon onto a meter would make the fifty that are fine as timers worse, and
     * a null here is a considered answer, not an unfinished one.
     *
     * @see dev.rbm72.weaponsplugin.ability.ChargeSpec
     */
    public dev.rbm72.weaponsplugin.ability.ChargeSpec ultimateChargeSpec() {
        return null;
    }

    /**
     * What each ability slot hits for, in the same units {@link #baseMeleeDamage()} uses — before rarity
     * scaling, before crits, before the multi-hit an ability may land.
     * <p>
     * Exists so a balance sheet can be produced at all. Every weapon's ability damage currently lives in
     * private fields read from per-weapon config keys, which means there is no view of the roster's damage
     * distribution anywhere: 57 files each hold one number and nothing holds the comparison, so an outlier
     * is only discovered by a player finding it. Declaring the numbers here makes
     * {@code /weaponbalance} able to rank them.
     * <p>
     * Empty by default and filled in as each weapon goes through the §0.1 doctrine pass — the sheet reports
     * an undeclared profile as exactly that rather than as zero damage, so a partially-migrated roster
     * produces an honest table instead of a flattering one.
     *
     * @return slot -> nominal damage per cast. Slots the weapon does not use are simply absent.
     */
    public java.util.Map<dev.rbm72.weaponsplugin.ability.CooldownManager.Slot, Double> damageProfile() {
        return java.util.Map.of();
    }

    /**
     * Which boss verbs this weapon answers — see {@link dev.rbm72.weaponsplugin.items.kit.Counterplay}.
     * <p>
     * Declared as data rather than left implicit in the ability code so the tooltip can say it and the
     * balance sheet can audit coverage. A boss with no drop answering its own signature verb is a gap worth
     * seeing in a table; found by playing the fight, it is just a boss that feels unfair.
     */
    public java.util.Set<CounterVerb> counterVerbs() {
        return java.util.Set.of();
    }

    /** The vocabulary of things bosses do to players that a drop may be built to answer. */
    public enum CounterVerb {
        /** Armour-ignoring stacks — Chill, Static Charge, Infection, Void Echo. */
        METER("clears the boss's stacks"),
        /** Forced loss of audio cues, i.e. the Hollow Choir's darkness. */
        SILENCE("breaks silence"),
        /** Terrain the boss placed to wall you in or encase you. */
        PILLARS("shatters the boss's terrain"),
        /** Floor that cannot be stood on — ice, powder snow, magma. */
        FOOTING("restores footing"),
        /** Knockback and forced relocation. */
        DISPLACEMENT("resists displacement");

        private final String description;

        CounterVerb(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    public abstract Sound castSound();

    public abstract Sound hitSound();

    public abstract Sound readySound();

    /** Only overridden by weapons that fire a tagged projectile (see {@link #idKey}). */
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
    }

    /** Fires every 10 ticks while this weapon is held in the main hand. Drives passives. */
    public void onTick(Player player) {
    }

    /** Fires when this weapon (held main hand) lands a melee hit. Drives on-hit passives. */
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
    }

    /** Fires when an {@link #onMeleeDamage} hit was lethal. Drives on-kill passives. */
    public void onKill(Player attacker, LivingEntity victim) {
    }

    /** Fires when this weapon (held main hand) breaks a block. Drives tool-style passives (AoE break, etc). */
    public void onBlockBreak(Player player, BlockBreakEvent event) {
    }

    public final Component displayName() {
        return Component.text(displayNameText(), rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    public final double effectiveMeleeDamage() {
        return baseMeleeDamage() * rarity().statMultiplier();
    }

    /**
     * Chance [0,1] this weapon's melee hit rolls a critical strike, rolled independently of
     * vanilla's sprint-jump crit in {@code WeaponDamageListener}. Rarity-scaled by default so
     * rarity buys something beyond the flat damage stat; override per-weapon for a signature feel
     * (daggers high chance/low multiplier, hammers the reverse).
     */
    public double critChance() {
        return configDouble("crit-chance", 0.10 + rarity().ordinal() * 0.03);
    }

    /** Damage multiplier applied on a critical strike. Rarity-scaled by default, see {@link #critChance()}. */
    public double critMultiplier() {
        return configDouble("crit-multiplier", 1.5 + rarity().ordinal() * 0.1);
    }

    /** Fraction of max health below which this weapon's hits deal bonus "execute" damage. */
    public double executeThresholdFraction() {
        return configDouble("execute-threshold", 0.2);
    }

    /** Damage multiplier applied when the victim is at or below {@link #executeThresholdFraction()}. */
    public double executeBonusMultiplier() {
        return configDouble("execute-bonus-multiplier", 1.3);
    }

    /**
     * Heavy weapons (mauls, hammers, axes) carry poise-break: enough weight behind a hit to
     * interrupt a boss's cast or knock down a blocking player's guard. Derived from material by
     * default so the existing weapon roster needs no per-weapon changes; override to opt in/out.
     */
    public boolean isHeavyWeapon() {
        Material m = material();
        return m == Material.MACE || m.name().endsWith("_AXE");
    }

    /** Ticks a heavy weapon's hit staggers a boss (freezes its attack/movement) or disables a blocking player's shield. */
    public int poiseStaggerTicks() {
        return isHeavyWeapon() ? configInt("poise-stagger-ticks", 30) : 0;
    }

    /**
     * Scalar applied only when the victim is another player. Lets PvP balance move independently
     * of the PvE numbers that otherwise drive every hit this weapon lands — see
     * {@code combat.pvp-damage-multiplier} in config.yml for the server-wide default.
     */
    public double pvpDamageMultiplier() {
        return configDouble("pvp-damage-multiplier", plugin.getConfig().getDouble("combat.pvp-damage-multiplier", 0.65));
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("weapons." + id() + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("weapons." + id() + "." + key, def);
    }

    /** Leading glyph per ability slot (ability1, ability2, ability3, ultimate) — sells which trigger a block belongs to at a glance. */
    private static final String[] ABILITY_ICONS = {"⚔", "❋", "✦", "☠"};

    /**
     * Weapon ids with a custom item model + texture in the {@code resourcepack/} directory.
     * Only these get {@link ItemMeta#setItemModel}; every other weapon keeps its plain material
     * look, since pointing at a model that doesn't exist in the pack renders as a missing texture.
     */
    private static final Set<String> TEXTURED_IDS = Set.of(
            "anglers_hook", "anvilfall", "apotheosis", "arcane_staff", "arcpike",
            "ballista_crossbow", "blastcaller", "blood_reaper", "celestial_bow",
            "chainwhip", "chrono_blade", "cinder_cleaver", "cryoclasm", "crystalpike",
            "dawnbreaker", "dragon_fang", "dreadlance", "duskfall_mace",
            "earthbreaker_axe", "excavators_pick", "exsanguinator", "flame_katana",
            "frost_scythe", "glacial_scepter", "harrowpike", "hive_breaker", "hunters_crossbow",
            "ironclaw_knuckles", "kings_judgment", "legionnaires_pike", "lunar_blade",
            "maelstrom_trident", "meteor_maul", "mournsong", "necromancer_staff",
            "nullblade", "plague_scythe", "rotscourge", "sakura_blade",
            "serpentfang_crossbow", "shadow_daggers", "solar_greatsword", "soulcrown",
            "soulharvester", "spikequake_warpick", "spinelash", "starbreaker",
            "starfang", "storm_chakrams", "stormbreaker", "stormreach_halberd", "sunderpike",
            "tearfall", "tempest_maul", "tetherpike", "thunder_hammer", "tidal_trident",
            "venomtip_javelin", "vitriol", "void_blade", "wind_spear",
            "wyrmscale_bow");

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());

        List<Component> body = new ArrayList<>();
        body.add(Component.text("❁ Damage  ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format(Locale.ROOT, "+%.1f", effectiveMeleeDamage()), NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)));

        double[] cooldowns = {ability1CooldownSeconds(), ability2CooldownSeconds(), ability3CooldownSeconds(), ultimateCooldownSeconds()};
        List<List<Component>> blocks = List.of(ability1Lore(), ability2Lore(), ability3Lore(), ultimateLore());
        String[] names = {ability1Name(), ability2Name(), ability3Name(), ultimateName()};

        for (int i = 0; i < blocks.size(); i++) {
            List<Component> block = blocks.get(i);
            if (block.isEmpty()) {
                continue;
            }
            body.add(Component.empty());

            String name = names[i];
            boolean hasName = name != null && !name.isBlank();
            if (hasName) {
                body.add(Component.text(ABILITY_ICONS[i] + " ", rarity().color())
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(name, rarity().color())
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false)));
            }
            for (int line = 0; line < block.size(); line++) {
                Component text = block.get(line).decoration(TextDecoration.ITALIC, false);
                if (line == 0 && !hasName) {
                    text = Component.text(ABILITY_ICONS[i] + " ", rarity().color())
                            .decoration(TextDecoration.ITALIC, false)
                            .append(text);
                } else {
                    text = Component.text(" ").append(text);
                }
                body.add(text);
            }
            // An earned ultimate shows what fills it, not a timer. Printing the cooldown floor next to a
            // charge meter reads as "wait 8 seconds", which is the opposite of what the meter is for.
            boolean chargedUltimate = i == 3 && ultimateChargeSpec() != null;
            if (chargedUltimate) {
                body.add(Component.text(" ◈ ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Fills " + ultimateChargeSpec().label() + " in combat", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, true)));
            } else if (cooldowns[i] > 0) {
                body.add(Component.text(" ⏱ ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format(Locale.ROOT, "%.1fs", cooldowns[i]), NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, true)));
            }
        }

        // Boss counterplay, spelled out. A drop that answers a boss verb is worth carrying into that
        // fight for a reason no damage number communicates, so the tooltip has to say it out loud —
        // otherwise the design exists only in the ability code and nobody gears around it.
        if (!counterVerbs().isEmpty()) {
            body.add(Component.empty());
            body.add(Component.text("⛨ Counterplay", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            for (CounterVerb verb : counterVerbs()) {
                body.add(Component.text(" • " + verb.description(), NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        String footerLabel = rarity().label().toUpperCase(Locale.ROOT) + " WEAPON";
        int frameWidth = Math.max(TooltipFrame.widestLine(body), TooltipFrame.footerWidth(footerLabel));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(body);
        lore.add(Component.empty());
        lore.add(TooltipFrame.footer(footerLabel, rarity().color(), frameWidth));
        meta.lore(lore);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(rarity().glint());

        if (TEXTURED_IDS.contains(id())) {
            meta.setItemModel(new NamespacedKey("weaponsplugin", id()));
        }

        NamespacedKey damageKey = new NamespacedKey(plugin, id() + "_damage");
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                damageKey, effectiveMeleeDamage(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, WEAPON_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, WEAPON_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, WEAPON_ID_KEY);
    }
}
