package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.InputValidator;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Step-by-step chat wizard for creating new discs.
 */
public class DiscCreationWizard implements Listener {

    private final CustomJukebox plugin;
    private final Map<UUID, CreationSession> activeSessions = new HashMap<>();

    public DiscCreationWizard(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the disc creation wizard.
     */
    public void startWizard(Player player) {
        CreationSession session = new CreationSession();
        activeSessions.put(player.getUniqueId(), session);

        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eDisc Creation Wizard §6§l(1/7)     ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eDisc ID §7(internal identifier):");
        player.sendMessage("§8Example: §7my_custom_disc");
        player.sendMessage("§8Format: §7lowercase, no spaces, use _ or -");
        player.sendMessage("");
        player.sendMessage("§7Type §ccancel §7to abort");
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
            player.sendMessage("§cDisc creation cancelled.");
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
                player.sendMessage(InputValidator.getLengthErrorMessage("Disc ID", InputValidator.MAX_DISC_ID_LENGTH));
            } else {
                player.sendMessage("§cInvalid ID! Use only lowercase letters, numbers, _ and -");
            }
            player.sendMessage("§7Please try again:");
            return;
        }

        if (plugin.getDiscManager().getDisc(input) != null) {
            player.sendMessage("§cA disc with ID §e" + input + " §calready exists!");
            player.sendMessage("§7Please choose a different ID:");
            return;
        }

        session.discId = input;
        session.currentStep++;

        player.sendMessage("§a✓ Disc ID: §e" + input);
        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eDisc Creation Wizard §6§l(2/7)     ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eDisplay Name §7(shown to players):");
        player.sendMessage("§8Example: §7&6Epic Journey");
        player.sendMessage("§8Colors: §7&a-&f, &#FF5555, <gradient:#FF0000:#0000FF>text</gradient>");
        player.sendMessage("§8Formats: §7&l, &o, &n, &m");
    }

    private void handleDisplayName(Player player, CreationSession session, String input) {
        // Validate length
        if (!InputValidator.isValidLength(input, InputValidator.MAX_DISPLAY_NAME_LENGTH)) {
            player.sendMessage(InputValidator.getLengthErrorMessage("Display Name", InputValidator.MAX_DISPLAY_NAME_LENGTH));
            player.sendMessage("§7Please try again:");
            return;
        }

        session.displayName = input;
        session.currentStep++;

        player.sendMessage("§a✓ Display Name: §r" + input.replace('&', '§'));
        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eDisc Creation Wizard §6§l(3/7)     ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eAuthor §7(composer/creator):");
        player.sendMessage("§8Example: §7C418 or &#FF5555Custom Artist");
        player.sendMessage("§8Supports colors & gradients just like Display Name");
    }

    private void handleAuthor(Player player, CreationSession session, String input) {
        // Validate length
        if (!InputValidator.isValidLength(input, InputValidator.MAX_AUTHOR_LENGTH)) {
            player.sendMessage(InputValidator.getLengthErrorMessage("Author", InputValidator.MAX_AUTHOR_LENGTH));
            player.sendMessage("§7Please try again:");
            return;
        }

        session.author = input;
        session.currentStep++;

        player.sendMessage("§a✓ Author: §f" + input);
        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eDisc Creation Wizard §6§l(4/7)     ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eSound Key §7(from resource pack):");
        player.sendMessage("§8Format: §7namespace:sound_name");
        player.sendMessage("§8Example: §7minecraft:music_disc." + session.discId);
        player.sendMessage("§8Or: §7customjukebox:" + session.discId);
    }

    private void handleSoundKey(Player player, CreationSession session, String input) {
        // Validate format and length
        if (!InputValidator.isValidSoundKey(input)) {
            if (input.length() > InputValidator.MAX_SOUND_KEY_LENGTH) {
                player.sendMessage(InputValidator.getLengthErrorMessage("Sound Key", InputValidator.MAX_SOUND_KEY_LENGTH));
            } else if (!input.contains(":")) {
                player.sendMessage("§cInvalid format! Sound key must contain ':'");
                player.sendMessage("§7Example: §eminecraft:music_disc.my_disc");
            } else {
                player.sendMessage("§cInvalid format! Use only lowercase letters, numbers, _ . and -");
            }
            player.sendMessage("§7Please try again:");
            return;
        }

        session.soundKey = input;
        session.currentStep++;

        player.sendMessage("§a✓ Sound Key: §b" + input);
        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eDisc Creation Wizard §6§l(5/7)     ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eDuration §7in seconds:");
        player.sendMessage("§8Example: §7180 §8(for 3 minutes)");
        player.sendMessage("§8Common: §760s, 120s, 180s, 240s, 300s");
    }

    private void handleDuration(Player player, CreationSession session, String input) {
        try {
            int seconds = Integer.parseInt(input);
            if (seconds <= 0) {
                player.sendMessage("§cDuration must be greater than 0!");
                player.sendMessage("§7Please enter a valid number:");
                return;
            }

            session.durationSeconds = seconds;
            session.currentStep++;

            int minutes = seconds / 60;
            int secs = seconds % 60;
            player.sendMessage("§a✓ Duration: §e" + seconds + "s §7(" + minutes + "m " + secs + "s)");
            player.sendMessage("");
            player.sendMessage("§6§l╔════════════════════════════════════╗");
            player.sendMessage("§6§l║  §eDisc Creation Wizard §6§l(6/7)     ║");
            player.sendMessage("§6§l╚════════════════════════════════════╝");
            player.sendMessage("");
            player.sendMessage("§7Enter a §eCategory §7(optional):");
            player.sendMessage("§8Example: §7ambient, epic, nature");
            player.sendMessage("§8Or type: §7none §8(for no category)");

            // Show existing categories
            if (!plugin.getDiscManager().getAllCategories().isEmpty()) {
                player.sendMessage("");
                player.sendMessage("§7Existing categories:");
                plugin.getDiscManager().getAllCategories().forEach(cat ->
                    player.sendMessage("§8  - §e" + cat.getId() + " §7(" + cat.getDisplayName() + ")"));
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid number! Please enter duration in seconds:");
        }
    }

    private void handleCategory(Player player, CreationSession session, String input) {
        if (input.equalsIgnoreCase("none")) {
            session.category = null;
            player.sendMessage("§a✓ Category: §8None");
        } else {
            session.category = input.toLowerCase();
            player.sendMessage("§a✓ Category: §e" + input);
        }

        session.currentStep++;

        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eDisc Creation Wizard §6§l(7/7)     ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter §eCustom Model Data §7(for texture):");
        player.sendMessage("§8Example: §71, 2, 3, ...");
        player.sendMessage("§8Default: §71 §8(uses default disc texture)");
        player.sendMessage("");
        player.sendMessage("§7Common values:");
        player.sendMessage("§8  1 §7- Default disc");
        player.sendMessage("§8  2-10 §7- Custom textures (if configured)");
    }

    private void handleCustomModelData(Player player, CreationSession session, String input) {
        try {
            int modelData = Integer.parseInt(input);
            if (modelData < 1) {
                player.sendMessage("§cCustom Model Data must be at least 1!");
                player.sendMessage("§7Please enter a valid number:");
                return;
            }

            session.customModelData = modelData;

            // Finish wizard
            finishWizard(player, session);

        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid number! Please enter Custom Model Data:");
        }
    }

    private void finishWizard(Player player, CreationSession session) {
        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║     §aCreating Disc...            §6§l║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Summary:");
        player.sendMessage("§8  ID: §e" + session.discId);
        player.sendMessage("§8  Name: §r" + session.displayName.replace('&', '§'));
        player.sendMessage("§8  Author: §f" + session.author);
        player.sendMessage("§8  Sound: §b" + session.soundKey);
        player.sendMessage("§8  Duration: §e" + session.durationSeconds + "s");
        player.sendMessage("§8  Category: §e" + (session.category != null ? session.category : "None"));
        player.sendMessage("§8  Model Data: §e" + session.customModelData);
        player.sendMessage("");

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
            player.sendMessage("§a§l✓ Disc created successfully!");
            player.sendMessage("§7The disc has been saved to §edisc.json");
            player.sendMessage("");
            player.sendMessage("§7You can now:");
            player.sendMessage("§8  - §e/cjb give " + player.getName() + " " + session.discId + " §7- Get the disc");
            player.sendMessage("§8  - §e/cjb gui §7- Open GUI and edit via Admin Panel");
        } else {
            player.sendMessage("§c§l✗ Failed to create disc!");
            player.sendMessage("§7Please check console for errors.");
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
