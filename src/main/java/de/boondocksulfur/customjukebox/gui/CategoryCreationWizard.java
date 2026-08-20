package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import de.boondocksulfur.customjukebox.utils.InputValidator;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Step-by-step chat wizard for creating new categories.
 * Thread-safe implementation using ConcurrentHashMap.
 *
 * @author BoondockSulfur
 * @version 1.3.0
 * @since 1.3.0
 */
public class CategoryCreationWizard implements Listener {

    private final CustomJukebox plugin;
    private final Map<UUID, CreationSession> activeSessions = new ConcurrentHashMap<>();

    public CategoryCreationWizard(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the category creation wizard.
     */
    public void startWizard(Player player) {
        CreationSession session = new CreationSession();
        activeSessions.put(player.getUniqueId(), session);

        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eCategory Creation Wizard &6&l(1/3)  ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eCategory ID &7(internal identifier):");
        MessageUtil.sendMessage(player, "&8Example: &7ambient_music");
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
            MessageUtil.sendMessage(player, "&cCategory creation cancelled.");
            activeSessions.remove(player.getUniqueId());
            return;
        }

        // Only one message at a time may advance this session
        if (!session.claim()) {
            return;
        }

        // Handle confirmation step (step 3)
        if (session.currentStep == 3) {
            if (input.equalsIgnoreCase("confirm")) {
                SchedulerUtil.runPlayerTask(plugin, player, () -> {
                    try {
                        // Re-check permission - it may have been revoked mid-wizard
                        if (!player.hasPermission("customjukebox.admin")) {
                            MessageUtil.sendMessage(player, "&cYou no longer have permission to create categories!");
                            activeSessions.remove(player.getUniqueId());
                            return;
                        }

                        boolean success = plugin.getDiscManager().createCategory(
                            session.categoryId,
                            session.displayName,
                            session.description
                        );

                        if (success) {
                            MessageUtil.sendMessage(player, "");
                            MessageUtil.sendMessage(player, "&a&l✓ &aCategory &e" + session.categoryId + " &acreated successfully!");
                            MessageUtil.sendMessage(player, "");
                        } else {
                            MessageUtil.sendMessage(player, "&c&l✗ &cFailed to create category! Please try again.");
                        }

                        activeSessions.remove(player.getUniqueId());
                    } finally {
                        session.release();
                    }
                });
            } else {
                MessageUtil.sendMessage(player, "&cInvalid input! Type &aconfirm &cor &ccancel");
                session.release();
            }
            return;
        }

        // Process other steps
        SchedulerUtil.runPlayerTask(plugin, player, () -> {
            try {
                handleStep(player, session, input);
            } finally {
                session.release();
            }
        });
    }

    private void handleStep(Player player, CreationSession session, String input) {
        // Re-check permission - it may have been revoked mid-wizard
        if (!player.hasPermission("customjukebox.admin")) {
            MessageUtil.sendMessage(player, "&cYou no longer have permission to create categories!");
            activeSessions.remove(player.getUniqueId());
            return;
        }

        switch (session.currentStep) {
            case 0: // Category ID
                handleCategoryId(player, session, input);
                break;
            case 1: // Display Name
                handleDisplayName(player, session, input);
                break;
            case 2: // Description
                handleDescription(player, session, input);
                break;
        }
    }

    private void handleCategoryId(Player player, CreationSession session, String input) {
        // Normalize the same way the disc editor's inline category creation does
        String categoryId = input.toLowerCase(Locale.ROOT).replace(" ", "_");

        // Validate format AND length - the length limit was previously missing
        // here, so arbitrarily long IDs could be written into disc.json
        if (!InputValidator.isValidCategoryId(categoryId)) {
            MessageUtil.sendMessage(player, "&cInvalid ID! Use only letters, numbers, _ and - (max "
                + InputValidator.MAX_CATEGORY_ID_LENGTH + " characters)");
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        if (plugin.getDiscManager().getCategory(categoryId) != null) {
            MessageUtil.sendMessage(player, "&cA category with ID &e" + categoryId + " &calready exists!");
            MessageUtil.sendMessage(player, "&7Please choose a different ID:");
            return;
        }

        session.categoryId = categoryId;
        session.currentStep++;

        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eCategory Creation Wizard &6&l(2/3)  ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eDisplay Name &7(shown to players):");
        MessageUtil.sendMessage(player, "&8Example: &7&6Ambient Music");
        MessageUtil.sendMessage(player, "&8Colors: &7&a-&f, &#FF5555, <gradient:#FF0000:#0000FF>text</gradient>");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Type &ccancel &7to abort");
    }

    private void handleDisplayName(Player player, CreationSession session, String input) {
        if (!InputValidator.isValidLength(input, InputValidator.MAX_CATEGORY_NAME_LENGTH)) {
            MessageUtil.sendMessage(player,
                InputValidator.getLengthErrorMessage("Display Name", InputValidator.MAX_CATEGORY_NAME_LENGTH));
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        // Translate color codes (supports legacy, HEX, gradients)
        String displayName = AdventureUtil.toLegacy(AdventureUtil.parseComponent(input));
        session.displayName = displayName;
        session.currentStep++;

        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eCategory Creation Wizard &6&l(3/3)  ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Enter the &eDescription &7(optional):");
        MessageUtil.sendMessage(player, "&8Example: &7Calm and relaxing ambient sounds");
        MessageUtil.sendMessage(player, "&8Leave empty to skip (just type 'skip')");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Type &ccancel &7to abort");
    }

    private void handleDescription(Player player, CreationSession session, String input) {
        if (!InputValidator.isValidLength(input, InputValidator.MAX_DESCRIPTION_LENGTH)) {
            MessageUtil.sendMessage(player,
                InputValidator.getLengthErrorMessage("Description", InputValidator.MAX_DESCRIPTION_LENGTH));
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        String description = input.equalsIgnoreCase("skip") ? "" : AdventureUtil.toLegacy(AdventureUtil.parseComponent(input));
        session.description = description;

        // Show summary
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l╔════════════════════════════════════╗");
        MessageUtil.sendMessage(player, "&6&l║  &eCategory Summary                ║");
        MessageUtil.sendMessage(player, "&6&l╚════════════════════════════════════╝");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7ID: &e" + session.categoryId);
        MessageUtil.sendMessage(player, "&7Display Name: " + session.displayName);
        MessageUtil.sendMessage(player, "&7Description: &f" + (session.description.isEmpty() ? "&8(none)" : session.description));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Type &aconfirm &7to create or &ccancel &7to abort");

        session.currentStep++; // Move to confirmation step
    }

    /**
     * Checks if a player has an active session.
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Cancels an active session for a player.
     */
    public void cancelSession(UUID playerId) {
        activeSessions.remove(playerId);
    }

    /**
     * Internal class to track a player's creation session.
     */
    private static class CreationSession {
        int currentStep = 0;
        String categoryId;
        String displayName;
        String description;

        /**
         * Guards against two chat messages being handled for the same session at
         * once. AsyncChatEvent fires off the main thread, so a player spamming
         * two lines could otherwise have both processed against the same step.
         */
        private final AtomicBoolean processing = new AtomicBoolean(false);

        boolean claim() {
            return processing.compareAndSet(false, true);
        }

        void release() {
            processing.set(false);
        }
    }
}
