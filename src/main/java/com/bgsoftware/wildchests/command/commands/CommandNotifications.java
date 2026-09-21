package com.bgsoftware.wildchests.command.commands;

import com.bgsoftware.wildchests.Locale;
import com.bgsoftware.wildchests.WildChestsPlugin;
import com.bgsoftware.wildchests.command.ICommand;
import com.bgsoftware.wildchests.task.NotifierTask;
import com.bgsoftware.wildchests.task.SaleNotifications;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandNotifications implements ICommand {
    public String getLabel() { return "notifications"; }
    public String getUsage() { return "chests notifications [default|always|1m|5m|15m|30m|1h|never]"; }
    public String getPermission() { return "wildchests.notifications"; }
    public String getDescription() { return "Choose how often autosell summaries appear in chat."; }
    public int getMinArgs() { return 1; }
    public int getMaxArgs() { return 2; }

    public void perform(WildChestsPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            Locale.NOTIFICATIONS_PLAYER_ONLY.send(sender);
            return;
        }
        Player player = (Player) sender;
        if (args.length == 2) {
            String mode = args[1].toLowerCase(java.util.Locale.ENGLISH);
            if (!SaleNotifications.MODES.contains(mode)) {
                Locale.COMMAND_USAGE.send(sender, getUsage());
                return;
            }
            try {
                NotifierTask.getSales().setMode(player.getUniqueId(), mode, System.currentTimeMillis());
            } catch (IOException error) {
                plugin.getLogger().warning("Cannot save autosell notification preference: " + error.getMessage());
                Locale.NOTIFICATIONS_SAVE_FAILED.send(sender);
                return;
            }
        }
        Locale.NOTIFICATIONS_MODE.send(sender, NotifierTask.getSales().getMode(player.getUniqueId()));
    }

    public List<String> tabComplete(WildChestsPlugin plugin, CommandSender sender, String[] args) {
        return args.length == 2 ? SaleNotifications.MODES.stream()
                .filter(mode -> mode.startsWith(args[1].toLowerCase(java.util.Locale.ENGLISH)))
                .collect(Collectors.toList()) : Collections.emptyList();
    }
}
