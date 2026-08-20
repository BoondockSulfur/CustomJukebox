package de.boondocksulfur.customjukebox.commands.subcommands;

import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.commands.SubCommand;
import de.boondocksulfur.customjukebox.model.CustomDisc;
import de.boondocksulfur.customjukebox.utils.GuiPageUtil;
import de.boondocksulfur.customjukebox.utils.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListSubcommand implements SubCommand {

    /** Discs per chat page - each entry prints several lines. */
    private static final int DISCS_PER_PAGE = 6;

    private final CustomJukebox plugin;

    public ListSubcommand(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List all custom discs";
    }

    @Override
    public String getUsage() {
        return "/cjb list [page]";
    }

    @Override
    public String getPermission() {
        return "customjukebox.list";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        List<CustomDisc> discs = new ArrayList<>(plugin.getDiscManager().getAllDiscs());

        int page = 0;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]) - 1; // Users count from 1
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(sender, plugin.getLanguageManager()
                    .getMessage("list-invalid-page", "value", args[0]));
                return true;
            }
        }
        int pageCount = GuiPageUtil.pageCount(discs.size(), DISCS_PER_PAGE);
        page = GuiPageUtil.clampPage(page, discs.size(), DISCS_PER_PAGE);

        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("list-header"));
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("list-total",
            "amount", String.valueOf(discs.size())));
        MessageUtil.sendMessage(sender, "");

        // Paged: a server with a few dozen discs used to push five lines each
        // through the chat window and scroll everything else away.
        for (CustomDisc disc : GuiPageUtil.slice(discs, page, DISCS_PER_PAGE)) {
            MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("list-disc-format", "disc", disc.getId()));
            MessageUtil.sendMessage(sender, "  &7Name: &f" + disc.getDisplayName());
            MessageUtil.sendMessage(sender, "  &7Author: &f" + disc.getAuthor());

            if (disc.hasCustomSound()) {
                MessageUtil.sendMessage(sender, "  &7Sound: &b" + disc.getSoundKey());
                MessageUtil.sendMessage(sender, "  &7Duration: &b" + disc.getDurationSeconds() + "s");
            } else {
                MessageUtil.sendMessage(sender, "  &7Sound: &8(vanilla)");
            }

            if (disc.hasFragments()) {
                MessageUtil.sendMessage(sender, "  &7Fragments: &a" + disc.getFragmentCount() + " required");
            } else {
                MessageUtil.sendMessage(sender, "  &7Fragments: &8(none)");
            }

            MessageUtil.sendMessage(sender, "");
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("pages", String.valueOf(pageCount));
        MessageUtil.sendMessage(sender, plugin.getLanguageManager().getMessage("list-page-footer", placeholders));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            int pageCount = GuiPageUtil.pageCount(plugin.getDiscManager().getAllDiscs().size(), DISCS_PER_PAGE);
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= pageCount; i++) {
                String candidate = String.valueOf(i);
                if (candidate.startsWith(args[0])) {
                    pages.add(candidate);
                }
            }
            return pages;
        }
        return new ArrayList<>();
    }
}
