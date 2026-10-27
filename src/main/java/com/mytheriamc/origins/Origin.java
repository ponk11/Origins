package com.mytheriamc.origins;

import org.bukkit.Material;

public enum Origin {
    BLAZE("blaze", "Blaze", Material.BLAZE_ROD),
    BREEZE("breeze", "Breeze", Material.WIND_CHARGE),
    SHULKER("shulker", "Shulker", Material.SHULKER_SHELL),
    GUARDIAN("guardian", "Guardian", Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE);

    private final String key;
    private final String name;
    private final Material icon;

    Origin(String key, String name, Material icon) {
        this.key = key;
        this.name = name;
        this.icon = icon;
    }

    public String key() { return key; }
    public String displayName() { return name; }
    public Material icon() { return icon; }
    public static Origin fromKey(String key) {
        for (Origin origin : values()) if (origin.key.equalsIgnoreCase(key)) return origin;
        return null;
    }
}
