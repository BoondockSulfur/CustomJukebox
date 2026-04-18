package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.InputValidator;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step-by-step chat wizard for creating new discs.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class DiscCreationWizard implements Listener {

    private final CustomJukebox plugin;
    private final Map<UUID, CreationSession> activeSessions = new ConcurrentHashMap<>();

    public DiscCreationWizard(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the disc creation wizard.
     */
    public void startWizard(Player player) {
        CreationSession session = new CreationSession();
        activeSessions.put(player.getUniqueId(), session);

        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eDisc Creation Wizard &6&l(1/7)     ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eDisc ID &7(internal identifier):");
        MessageUtil.sendMessage(player, "&8Example: &7my_custom_disc");
        MessageUtil.sendMessage(player, "&8Format: &7lowercase, no spaces, use _ or -");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Type &ccancel &7to abort");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return; // Already handled by another GUI

        Player player = event.getPlayer();
        CreationSession session = activeSessions.get(player.getUniqueId());

        if (session == null) return;

        event.setCancelled(true);
        String input = AdventureUtil.toLegacy(event.message());

        if (input.equalsIgnoreCase("cancel")) {
            MessageUtil.sendMessage(player, "&cDisc creation cancelled.");
            activeSessions.remove(player.getUniqueId());
            return;
        }

        // Process step
        SchedulerUtil.runPlayerTask(plugin, player, () -> handleStep(player, session, input));
    }

    private void handleStep(Player player, CreationSession session, String input) {
        switch (session.currentStep) {
            case 0: // Disc ID
                handleDiscId(player, session, input);
                break;
            case 1: // Display Name
                handleDisplayName(player, session, input);
                break;
            case 2: // Author
                handleAuthor(player, session, input);
                break;
            case 3: // Sound Key
                handleSoundKey(player, session, input);
                break;
            case 4: // Duration
                handleDuration(player, session, input);
                break;
            case 5: // Category
                handleCategory(player, session, input);
                break;
            case 6: // Custom Model Data
                handleCustomModelData(player, session, input);
                break;
        }
    }

    private void handleDiscId(Player player, CreationSession session, String input) {
        // Validate ID format and length
        if (!InputValidator.isValidDiscId(input)) {
            if (input.length() > InputValidator.MAX_DISC_ID_LENGTH) {
                MessageUtil.sendMessage(player, InputValidator.getLengthErrorMessage("Disc ID", InputValidator.MAX_DISC_ID_LENGTH));
            } else {
                MessageUtil.sendMessage(player, "&cInvalid ID! Use only lowercase letters, numbers, _ and -");
            }
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        if (plugin.getDiscManager().getDisc(input) != null) {
            MessageUtil.sendMessage(player, "&cA disc with ID &e" + input + " &calready exists!");
            MessageUtil.sendMessage(player, "&7Please choose a different ID:");
            return;
        }

        session.discId = input;
        session.currentStep++;

        MessageUtil.sendMessage(player, "&a✓ Disc ID: &e" + input);
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eDisc Creation Wizard &6&l(2/7)     ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eDisplay Name &7(shown to players):");
        MessageUtil.sendMessage(player, "&8Example: &7&6Epic Journey");
        MessageUtil.sendMessage(player, "&8Colors: &7&a-&f, &#FF5555, <gradient:#FF0000:#0000FF>text</gradient>");
        MessageUtil.sendMessage(player, "&8Formats: &7&l, &o, &n, &m");
    }

    private void handleDisplayName(Player player, CreationSession session, String input) {
        // Validate length
        if (!InputValidator.isValidLength(input, InputValidator.MAX_DISPLAY_NAME_LENGTH)) {
            MessageUtil.sendMessage(player, InputValidator.getLengthErrorMessage("Display Name", InputValidator.MAX_DISPLAY_NAME_LENGTH));
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        session.displayName = input;
        session.currentStep++;

        MessageUtil.sendMessage(player, "&a✓ Display Name: &r" + input);
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eDisc Creation Wizard &6&l(3/7)     ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eAuthor &7(composer/creator):");
        MessageUtil.sendMessage(player, "&8Example: &7C418 or &#FF5555Custom Artist");
        MessageUtil.sendMessage(player, "&8Supports colors & gradients just like Display Name");
    }

    private void handleAuthor(Player player, CreationSession session, String input) {
        // Validate length
        if (!InputValidator.isValidLength(input, InputValidator.MAX_AUTHOR_LENGTH)) {
            MessageUtil.sendMessage(player, InputValidator.getLengthErrorMessage("Author", InputValidator.MAX_AUTHOR_LENGTH));
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        session.author = input;
        session.currentStep++;

        MessageUtil.sendMessage(player, "&a✓ Author: &f" + input);
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eDisc Creation Wizard &6&l(4/7)     ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eSound Key &7(from resource pack):");
        MessageUtil.sendMessage(player, "&8Format: &7namespace:sound_name");
        MessageUtil.sendMessage(player, "&8Example: &7minecraft:music_disc." + session.discId);
        MessageUtil.sendMessage(player, "&8Or: &7customjukebox:" + session.discId);
    }

    private void handleSoundKey(Player player, CreationSession session, String input) {
        // Validate format and length
        if (!InputValidator.isValidSoundKey(input)) {
            if (input.length() > InputValidator.MAX_SOUND_KEY_LENGTH) {
                MessageUtil.sendMessage(player, InputValidator.getLengthErrorMessage("Sound Key", InputValidator.MAX_SOUND_KEY_LENGTH));
            } else if (!input.contains(":")) {
                MessageUtil.sendMessage(player, "&cInvalid format! Sound key must contain ':'");
                MessageUtil.sendMessage(player, "&7Example: &eminecraft:music_disc.my_disc");
            } else {
                MessageUtil.sendMessage(player, "&cInvalid format! Use only lowercase letters, numbers, _ . and -");
            }
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        session.soundKey = input;
        session.currentStep++;

        MessageUtil.sendMessage(player, "&a✓ Sound Key: &b" + input);
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eDisc Creation Wizard &6&l(5/7)     ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eDuration &7in seconds:");
        MessageUtil.sendMessage(player, "&8Example: &7180 &8(for 3 minutes)");
        MessageUtil.sendMessage(player, "&8Common: &760s, 120s, 180s, 240s, 300s");
    }

    private void handleDuration(Player player, CreationSession session, String input) {
        try {
            int seconds = Integer.parseInt(input);
            if (seconds <= 0) {
                MessageUtil.sendMessage(player, "&cDuration must be greater than 0!");
                MessageUtil.sendMessage(player, "&7Please enter a valid number:");
                return;
            }

            session.durationSeconds = seconds;
            session.currentStep++;

            int minutes = seconds / 60;
            int secs = seconds % 60;
            MessageUtil.sendMessage(player, "&a✓ Duration: &e" + seconds + "s &7(" + minutes + "m " + secs + "s)");
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
            MessageUtil.sendMessage(player, "&6&l║  &eDisc Creation Wizard &6&l(6/7)     ║");
            MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&7Enter a &eCategory &7(optional):");
            MessageUtil.sendMessage(player, "&8Example: &7ambient, epic, nature");
            MessageUtil.sendMessage(player, "&8Or type: &7none &8(for no category)");

            // Show existing categories
            if (!plugin.getDiscManager().getAllCategories().isEmpty()) {
                MessageUtil.sendMessage(player, "");
                MessageUtil.sendMessage(player, "&7Existing categories:");
                plugin.getDiscManager().getAllCategories().forEach(cat ->
                    MessageUtil.sendMessage(player, "&8  - &e" + cat.getId() + " &7(" + cat.getDisplayName() + ")"));
            }

        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(player, "&cInvalid number! Please enter duration in seconds:");
        }
    }

    private void handleCategory(Player player, CreationSession session, String input) {
        if (input.equalsIgnoreCase("none")) {
            session.category = null;
            MessageUtil.sendMessage(player, "&a✓ Category: &8None");
        } else {
            String categoryId = input.toLowerCase();
            if (plugin.getDiscManager().getCategory(categoryId) == null) {
                MessageUtil.sendMessage(player, "&cCategory '" + input + "' does not exist!");
                MessageUtil.sendMessage(player, "&7The disc will be created with this category anyway.");
                MessageUtil.sendMessage(player, "&7You can create the category later with the admin GUI.");
            }
            session.category = categoryId;
            MessageUtil.sendMessage(player, "&a✓ Category: &e" + input);
        }

        session.currentStep++;

        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eDisc Creation Wizard &6&l(7/7)     ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter &eCustom Model Data &7(for texture):");
        MessageUtil.sendMessage(player, "&8Example: &71, 2, 3, ...");
        MessageUtil.sendMessage(player, "&8Default: &71 &8(uses default disc texture)");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Common values:");
        MessageUtil.sendMessage(player, "&8  1 &7- Default disc");
        MessageUtil.sendMessage(player, "&8  2-10 &7- Custom textures (if configured)");
    }

    private void handleCustomModelData(Player player, CreationSession session, String input) {
        try {
            int modelData = Integer.parseInt(input);
            if (modelData < 1) {
                MessageUtil.sendMessage(player, "&cCustom Model Data must be at least 1!");
                MessageUtil.sendMessage(player, "&7Please enter a valid number:");
                return;
            }

            session.customModelData = modelData;

            // Finish wizard
            finishWizard(player, session);

        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(player, "&cInvalid number! Please enter Custom Model Data:");
        }
    }

    private void finishWizard(Player player, CreationSession session) {
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║     &aCreating Disc...            &6&l║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Summary:");
        MessageUtil.sendMessage(player, "&8  ID: &e" + session.discId);
        MessageUtil.sendMessage(player, "&8  Name: &r" + session.displayName);
        MessageUtil.sendMessage(player, "&8  Author: &f" + session.author);
        MessageUtil.sendMessage(player, "&8  Sound: &b" + session.soundKey);
        MessageUtil.sendMessage(player, "&8  Duration: &e" + session.durationSeconds + "s");
        MessageUtil.sendMessage(player, "&8  Category: &e" + (session.category != null ? session.category : "None"));
        MessageUtil.sendMessage(player, "&8  Model Data: &e" + session.customModelData);
        MessageUtil.sendMessage(player, "");

        // Create disc
        boolean success = plugin.getDiscManager().createDisc(
            session.discId,
            session.displayName,
            session.author,
            session.soundKey,
            session.durationSeconds * 20, // Convert to ticks
            session.category,
            session.customModelData,
            new ArrayList<>() // Empty lore
        );

        if (success) {
            MessageUtil.sendMessage(player, "&a&l✓ Disc created successfully!");
            MessageUtil.sendMessage(player, "&7The disc has been saved to &edisc.json");
            MessageUtil.sendMessage(player, "");
            MessageUtil.sendMessage(player, "&7You can now:");
            MessageUtil.sendMessage(player, "&8  - &e/cjb give " + player.getName() + " " + session.discId + " &7- Get the disc");
            MessageUtil.sendMessage(player, "&8  - &e/cjb gui &7- Open GUI and edit via Admin Panel");
        } else {
            MessageUtil.sendMessage(player, "&c&l✗ Failed to create disc!");
            MessageUtil.sendMessage(player, "&7Please check console for errors.");
        }

        activeSessions.remove(player.getUniqueId());
    }

    public void cleanup(Player player) {
        activeSessions.remove(player.getUniqueId());
    }

    private static class CreationSession {
        int currentStep = 0;
        String discId;
        String displayName;
        String author;
        String soundKey;
        int durationSeconds;
        String category;
        int customModelData = 1;
    }
}
