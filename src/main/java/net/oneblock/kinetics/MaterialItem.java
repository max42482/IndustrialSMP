package net.oneblock.kinetics;

import java.util.Locale;

public enum MaterialItem {
    IRON_SHEET("<#d8d8d8>Iron Sheet", "Pressed from an iron ingot."),
    COPPER_SHEET("<#e0835e>Copper Sheet", "Pressed from a copper ingot."),
    GOLDEN_SHEET("<#f5d64a>Golden Sheet", "Pressed from a gold ingot."),
    CRUSHED_RAW_IRON("<#c9b39c>Crushed Raw Iron", "Smelt for an ingot, or wash with a fan for 9+ nuggets."),
    CRUSHED_RAW_COPPER("<#d18a5c>Crushed Raw Copper", "Smelt for an ingot, or wash with a fan for 9+ nuggets."),
    CRUSHED_RAW_GOLD("<#e8c547>Crushed Raw Gold", "Smelt for an ingot, or wash with a fan for 9+ nuggets.");

    public final String displayName;
    public final String description;

    MaterialItem(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MaterialItem fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
