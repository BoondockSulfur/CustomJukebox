package de.boondocksulfur.customjukebox.gui;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.utils.AdventureUtil;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Step-by-step chat wizard for creating new categories.
 *
 * @author BoondockSulfur
 * @version 1.3.0
 * @since 1.3.0
 */
public class CategoryCreationWizard implements Listener {

    private final CustomJukebox plugin;
    private final Map<UUID, CreationSession> activeSessions = new HashMap<>();

    public CategoryCreationWizard(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the category creation wizard.
     */
    public void startWizard(Player player) {
        CreationSession session = new CreationSession();
        activeSessions.put(player.getUniqueId(), session);

        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eCategory Creation Wizard §6§l(1/3)  ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eCategory ID §7(internal identifier):");
        player.sendMessage("§8Example: §7ambient_music");
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
            player.sendMessage("§cCategory creation cancelled.");
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
                        player.sendMessage("");
                        player.sendMessage("§a§l✓ §aCategory §e" + session.categoryId + " §acreated successfully!");
                        player.sendMessage("");
                    } else {
                        player.sendMessage("§c§l✗ §cFailed to create category! Please try again.");
                    }

                    activeSessions.remove(player.getUniqueId());
                });
            } else {
                player.sendMessage("§cInvalid input! Type §aconfirm §cor §ccancel");
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
            player.sendMessage("§cInvalid ID! Use only lowercase letters, numbers, _ and -");
            player.sendMessage("§7Please try again:");
            return;
        }

        if (plugin.getDiscManager().getCategory(input) != null) {
            player.sendMessage("§cA category with ID §e" + input + " §calready exists!");
            player.sendMessage("§7Please choose a different ID:");
            return;
        }

        session.categoryId = input;
        session.currentStep++;

        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eCategory Creation Wizard §6§l(2/3)  ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eDisplay Name §7(shown to players):");
        player.sendMessage("§8Example: §7§6Ambient Music");
        player.sendMessage("§8Colors: §7&a-&f, &#FF5555, <gradient:#FF0000:#0000FF>text</gradient>");
        player.sendMessage("");
        player.sendMessage("§7Type §ccancel §7to abort");
    }

    private void handleDisplayName(Player player, CreationSession session, String input) {
        // Translate color codes (supports legacy, HEX, gradients)
        String displayName = AdventureUtil.toLegacy(AdventureUtil.parseComponent(input));
        session.displayName = displayName;
        session.currentStep++;

        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eCategory Creation Wizard §6§l(3/3)  ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Enter the §eDescription §7(optional):");
        player.sendMessage("§8Example: §7Calm and relaxing ambient sounds");
        player.sendMessage("§8Leave empty to skip (just type 'skip')");
        player.sendMessage("");
        player.sendMessage("§7Type §ccancel §7to abort");
    }

    private void handleDescription(Player player, CreationSession session, String input) {
        String description = input.equalsIgnoreCase("skip") ? "" : AdventureUtil.toLegacy(AdventureUtil.parseComponent(input));
        session.description = description;

        // Show summary
        player.sendMessage("");
        player.sendMessage("§6§l╔════════════════════════════════════╗");
        player.sendMessage("§6§l║  §eCategory Summary                ║");
        player.sendMessage("§6§l╚════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7ID: §e" + session.categoryId);
        player.sendMessage("§7Display Name: " + session.displayName);
        player.sendMessage("§7Description: §f" + (session.description.isEmpty() ? "§8(none)" : session.description));
        player.sendMessage("");
        player.sendMessage("§7Type §aconfirm §7to create or §ccancel §7to abort");

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
