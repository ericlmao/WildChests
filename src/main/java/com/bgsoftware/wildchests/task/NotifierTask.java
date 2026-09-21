package com.bgsoftware.wildchests.task;

import com.bgsoftware.wildchests.Locale;
import com.bgsoftware.wildchests.WildChestsPlugin;
import com.bgsoftware.wildchests.scheduler.ScheduledTask;
import com.bgsoftware.wildchests.scheduler.Scheduler;
import com.bgsoftware.wildchests.utils.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NotifierTask {

    private static final WildChestsPlugin plugin = WildChestsPlugin.getPlugin();

    private static SaleNotifications sales;
    private long nextCrafting;
    private static final Map<UUID, Set<CraftingDetails>> craftings = new HashMap<>();

    private static ScheduledTask task = null;

    private NotifierTask() {
        nextCrafting = System.currentTimeMillis() + plugin.getSettings().notifyInterval * 50L;
        task = Scheduler.runRepeatingTaskAsync(this::run, 20L);
    }

    public static void initialize() throws IOException {
        sales = new SaleNotifications(plugin.getDataFolder().toPath().resolve("notification-preferences.properties"));
    }

    public static SaleNotifications getSales() {
        return sales;
    }

    public static void start() {
        if (task != null) {
            task.cancel();
        }

        new NotifierTask();
    }

    private void run() {
        long now = System.currentTimeMillis();
        sales.drain(now, plugin.getSettings().notifyInterval * 50L).forEach((uuid, summary) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return; // Like the old notifier, expired offline summaries are discarded.
            Scheduler.runTask(player, () -> {
                if (!player.isOnline() || sales.getMode(uuid).equals("never")) return;
                Locale.SOLD_CHEST_HEADER.send(player);
                BigDecimal total = BigDecimal.ZERO;
                for (Map.Entry<String, SaleNotifications.Line> entry : summary.items.entrySet()) {
                    SaleNotifications.Line line = entry.getValue();
                    if (plugin.getSettings().detailedNotifier) {
                        Locale.SOLD_CHEST_LINE.send(player, line.amount, StringUtils.format(entry.getKey()),
                                plugin.getSettings().sellFormat ? StringUtils.fancyFormat(line.earnings) :
                                        StringUtils.format(line.earnings));
                    }
                    total = total.add(line.earnings);
                }
                Locale.SOLD_CHEST_FOOTER.send(player, plugin.getSettings().sellFormat ?
                        StringUtils.fancyFormat(total) : StringUtils.format(total));
            });
        });
        if (plugin.getSettings().notifyInterval <= 0 || now < nextCrafting) return;
        nextCrafting = now + plugin.getSettings().notifyInterval * 50L;
        synchronized (craftings) {
            craftings.forEach((uuid, transactions) -> {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                if (offlinePlayer.isOnline()) {
                    Set<CraftingDetails> itemsCrafted = craftings.get(uuid);
                    Locale.CRAFTED_ITEMS_HEADER.send(offlinePlayer.getPlayer());
                    int totalCrafted = 0;

                    for (CraftingDetails item : itemsCrafted) {
                        if (plugin.getSettings().detailedNotifier) {
                            String craftedItemType = StringUtils.format(item.getItemStack().getType().name());
                            Locale.CRAFTED_ITEMS_LINE.send(offlinePlayer.getPlayer(), item.getAmount(), craftedItemType);
                        }
                        totalCrafted += item.getAmount();
                    }

                    Locale.CRAFTED_ITEMS_FOOTER.send(offlinePlayer.getPlayer(), totalCrafted);
                }
            });
            craftings.clear();
        }
    }

    public static void addTransaction(UUID player, ItemStack itemStack, int amount, double amountEarned) {
        sales.add(player, itemStack.getType().name(), amount, amountEarned, System.currentTimeMillis(),
                plugin.getSettings().notifyInterval * 50L);
    }

    public static synchronized void addCrafting(UUID player, ItemStack itemStack, int amount) {
        Set<CraftingDetails> craftingDetails;

        synchronized (craftings) {
            craftingDetails = craftings.computeIfAbsent(player, p -> new HashSet<>());
        }

        for (CraftingDetails crafting : craftingDetails) {
            if (crafting.getItemStack().isSimilar(itemStack)) {
                crafting.increaseAmount(amount);
                return;
            }
        }

        craftingDetails.add(new CraftingDetails(itemStack, amount));
    }

}
