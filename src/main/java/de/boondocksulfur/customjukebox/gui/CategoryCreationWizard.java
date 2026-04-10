package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
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

        // Handle confirmation step (step 3)
        if (session.currentStep == 3) {
            if (input.equalsIgnoreCase("confirm")) {
                SchedulerUtil.runPlayerTask(plugin, player, () -> {
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
                });
            } else {
                MessageUtil.sendMessage(player, "&cInvalid input! Type &aconfirm &cor &ccancel");
            }
            return;
        }

        // Process other steps
        SchedulerUtil.runPlayerTask(plugin, player, () -> handleStep(player, session, input));
    }

    private void handleStep(Player player, CreationSession session, String input) {
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
        // Validate ID
        if (!input.matches("[a-z0-9_-]+")) {
            MessageUtil.sendMessage(player, "&cInvalid ID! Use only lowercase letters, numbers, _ and -");
            MessageUtil.sendMessage(player, "&7Please try again:");
            return;
        }

        if (plugin.getDiscManager().getCategory(input) != null) {
            MessageUtil.sendMessage(player, "&cA category with ID &e" + input + " &calready exists!");
            MessageUtil.sendMessage(player, "&7Please choose a different ID:");
            return;
        }

        session.categoryId = input;
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
    }
}
