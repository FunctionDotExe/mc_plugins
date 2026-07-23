package dev.rbm72.weaponsplugin.armor;

/** The four vanilla armor slots an {@link ArmorPiece} can occupy. */
public enum ArmorSlot {
    HELMET("Helmet"),
    CHESTPLATE("Chestplate"),
    LEGGINGS("Leggings"),
    BOOTS("Boots");

    private final String label;

    ArmorSlot(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
