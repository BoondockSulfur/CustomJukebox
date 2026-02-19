package de.boondocksulfur.customjukebox.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for all subcommands.
 * Provides a clean structure for modular command handling.
 */
public interface SubCommand {

    /**
     * Gets the name of this subcommand.
     * @return Command name (e.g. "reload", "give", "list")
     */
    String getName();

    /**
     * Gets a short description of this subcommand.
     * @return Description shown in help
     */
    String getDescription();

    /**
     * Gets the usage syntax for this subcommand.
     * @return Usage string (e.g. "/cjb give <player> <disc>")
     */
    String getUsage();

    /**
     * Gets the permission node required to use this subcommand.
     * @return Permission node or null if no permission required
     */
    String getPermission();

    /**
     * Executes the subcommand.
     * @param sender Command sender
     * @param args Command arguments (excluding the subcommand name)
     * @return true if command was handled successfully
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * Provides tab completion for this subcommand.
     * @param sender Command sender
     * @param args Current arguments
     * @return List of suggestions
     */
    List<String> tabComplete(CommandSender sender, String[] args);
}
