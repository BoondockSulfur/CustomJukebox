package de.boondocksulfur.customjukebox.commands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.subcommands.*;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main command handler for /cjb (CustomJukebox).
 * Delegates execution to registered subcommands for clean modular structure.
 *
 * Supported commands:
 * - /cjb reload
 * - /cjb give <player> <disc> [amount]
 * - /cjb fragment <player> <disc> [amount]
 * - /cjb list
 * - /cjb info <disc>
 * - /cjb gui
 * - /cjb help
 */
public class CJBCommand implements CommandExecutor, TabCompleter {

    private final CustomJukebox plugin;
    private final Map<String, SubCommand> subcommands;
    private final List<SubCommand> subcommandList;

    public CJBCommand(CustomJukebox plugin) {
        this.plugin = plugin;
        this.subcommands = new HashMap<>();
        this.subcommandList = new ArrayList<>();

        // Register all subcommands
        registerSubcommand(new ReloadSubcommand(plugin));
        registerSubcommand(new GiveSubcommand(plugin));
        registerSubcommand(new FragmentSubcommand(plugin));
        registerSubcommand(new ListSubcommand(plugin));
        registerSubcommand(new InfoSubcommand(plugin));
        registerSubcommand(new GuiSubcommand(plugin));
        registerSubcommand(new PlaySubcommand(plugin));
        registerSubcommand(new StopSubcommand(plugin));
        registerSubcommand(new VolumeSubcommand(plugin));
        registerSubcommand(new MuteSubcommand(plugin));
        registerSubcommand(new UnmuteSubcommand(plugin));
        registerSubcommand(new PlaylistSubcommand(plugin));
        // GeneratePackSubcommand removed - no internal pack generation

        // Help subcommand needs access to all subcommands
        registerSubcommand(new HelpSubcommand(plugin, subcommandList));
    }

    private void registerSubcommand(SubCommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(), subcommand);
        subcommandList.add(subcommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No arguments - show help
        if (args.length == 0) {
            return executeSubcommand(sender, "help", new String[0]);
        }

        String subcommandName = args[0].toLowerCase();

        // Remove the first argument (subcommand name)
        String[] subcommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subcommandArgs, 0, args.length - 1);

        return executeSubcommand(sender, subcommandName, subcommandArgs);
    }

    private boolean executeSubcommand(CommandSender sender, String name, String[] args) {
        SubCommand subcommand = subcommands.get(name);

        if (subcommand == null) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-unknown", "command", name));
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("command-unknown-help"));
            return true;
        }

        // Check permission
        String permission = subcommand.getPermission();
        if (permission != null && !sender.hasPermission(permission)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("no-permission"));
            return true;
        }

        // Execute subcommand
        try {
            return subcommand.execute(sender, args);
        } catch (Exception e) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("error-command-failed"));
            plugin.getLogger().log(Level.SEVERE, "Error executing subcommand '" + name + "'", e);
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Tab complete subcommand names
            return subcommandList.stream()
                .filter(sub -> {
                    String permission = sub.getPermission();
                    return permission == null || sender.hasPermission(permission);
                })
                .map(SubCommand::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        // Delegate tab completion to the subcommand
        String subcommandName = args[0].toLowerCase();
        SubCommand subcommand = subcommands.get(subcommandName);

        if (subcommand != null) {
            // Check permission for tab completion
            String permission = subcommand.getPermission();
            if (permission != null && !sender.hasPermission(permission)) {
                return new ArrayList<>();
            }

            // Remove first argument (subcommand name)
            String[] subcommandArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subcommandArgs, 0, args.length - 1);

            return subcommand.tabComplete(sender, subcommandArgs);
        }

        return new ArrayList<>();
    }
}
