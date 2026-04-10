package de.boondocksulfur.customjukebox.model;

import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.ArrayList;
import java.util.List;

public class CustomDisc {

    private final String id;
    private final String displayName;
    private final String author;
    private final List<String> lore;
    private final Material discType;
    private final int customModelData;

    // JEXT-style extended fields
    private final String soundKey;           // e.g. "customjukebox:mysong" or "jext:newbeginnings"
    private final int durationTicks;         // Song duration in ticks (20 ticks = 1 second)
    private final int fragmentCount;         // How many fragments needed to craft this disc (0 = no fragments)
    private final String description;        // Optional description for GUI/tooltips
    private final String category;           // Optional category ID for organization

    public CustomDisc(String id, String displayName, String author, List<String> lore,
                     Material discType, int customModelData, String soundKey,
                     int durationTicks, int fragmentCount, String description, String category) {
        // Validate required fields
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Disc ID cannot be null or empty");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Display name cannot be null or empty");
        }
        if (discType == null || !discType.name().startsWith("MUSIC_DISC_")) {
            throw new IllegalArgumentException("Invalid disc type material: " + discType);
        }
        if (customModelData < 0) {
            throw new IllegalArgumentException("Custom model data cannot be negative");
        }
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        if (fragmentCount < 0) {
            throw new IllegalArgumentException("Fragment count cannot be negative");
        }

        this.id = id.trim();
        this.displayName = displayName;
        this.author = author != null ? author : "Unknown";
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
        this.discType = discType;
        this.customModelData = customModelData;
        this.soundKey = soundKey != null ? soundKey : "";
        this.durationTicks = durationTicks;
        this.fragmentCount = fragmentCount;
        this.description = description != null ? description : "";
        this.category = category != null ? category : "uncategorized";
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAuthor() {
        return author;
    }

    public List<String> getLore() {
        return lore;
    }

    public Material getDiscType() {
        return discType;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getSoundKey() {
        return soundKey;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getDurationSeconds() {
        return durationTicks / 20;
    }

    public int getFragmentCount() {
        return fragmentCount;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public boolean hasCategory() {
        return category != null && !category.isEmpty();
    }

    public boolean hasFragments() {
        return fragmentCount > 0;
    }

    public boolean hasCustomSound() {
        return soundKey != null && !soundKey.isEmpty();
    }

    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(discType);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Use Adventure API for display name
            meta.displayName(AdventureUtil.parseComponent(displayName));

            // Convert lore to Adventure Components
            List<Component> loreComponents = new ArrayList<>();
            for (String loreLine : lore) {
                loreComponents.add(AdventureUtil.parseComponent(loreLine));
            }

            if (author != null && !author.isEmpty()) {
                loreComponents.add(AdventureUtil.parseComponent("§7By: §e" + author));
            }
            meta.lore(loreComponents);

            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(cmd);

            // Hide vanilla item information
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            // HIDE_ADDITIONAL_TOOLTIP only exists in 1.20.5+, try to add it if available
            try {
                meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
            } catch (IllegalArgumentException ignored) {
                // Flag doesn't exist in this Minecraft version, ignore
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != discType) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        if (!meta.hasCustomModelDataComponent()) return false;
        List<Float> floats = meta.getCustomModelDataComponent().getFloats();
        return !floats.isEmpty() && floats.get(0) == (float) customModelData;
    }
}
