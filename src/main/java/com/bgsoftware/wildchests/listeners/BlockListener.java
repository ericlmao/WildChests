package com.bgsoftware.wildchests.listeners;

import com.bgsoftware.wildchests.Locale;
import com.bgsoftware.wildchests.WildChestsPlugin;
import com.bgsoftware.wildchests.api.objects.chests.Chest;
import com.bgsoftware.wildchests.api.objects.data.ChestData;
import com.bgsoftware.wildchests.utils.ChestUtils;
import com.bgsoftware.wildchests.utils.ItemUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("unused")
public final class BlockListener implements Listener {

    private final WildChestsPlugin plugin;
    private final BlockFace[] blockFaces = new BlockFace[]{BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH};
    private final Map<Location, PendingChestDrop> pendingChestDrops = new HashMap<>();

    private static final EntityType WIND_CHARGE_TYPE = lookupEntityType("WIND_CHARGE");
    private static final EntityType BREEZE_WIND_CHARGE_TYPE = lookupEntityType("BREEZE_WIND_CHARGE");

    public BlockListener(WildChestsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChestPlaceNearAnother(BlockPlaceEvent e) {
        if (!ChestUtils.isChest(e.getBlockPlaced().getType()))
            return;

        boolean hasNearbyChest = false;

        for (BlockFace blockFace : blockFaces) {
            Block block = e.getBlockPlaced().getRelative(blockFace);
            Material blockMaterial = block.getType();
            if (ChestUtils.isChest(blockMaterial)) {
                hasNearbyChest = true;
                if (plugin.getChestsManager().getChest(block.getLocation()) != null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        ChestData chestData = plugin.getChestsManager().getChestData(e.getItemInHand());

        if (chestData == null)
            return;

        if (hasNearbyChest)
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestPlace(BlockPlaceEvent e) {
        if (!ChestUtils.isChest(e.getBlockPlaced().getType()))
            return;

        ChestData chestData = plugin.getChestsManager().getChestData(e.getItemInHand());

        if (chestData == null)
            return;

        Chest chest = plugin.getChestsManager().addChest(e.getPlayer().getUniqueId(), e.getBlockPlaced().getLocation(), chestData);

        plugin.getProviders().notifyChestPlaceListeners(chest);

        //chest.onPlace(e);

        Locale.CHEST_PLACED.send(e.getPlayer(), chestData.getName(), e.getItemInHand().getItemMeta().getDisplayName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestBreak(BlockBreakEvent e) {
        Chest chest = plugin.getChestsManager().getChest(e.getBlock().getLocation());

        if (chest == null)
            return;

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE && e.isDropItems()) {
            ChestData chestData = chest.getData();
            pendingChestDrops.put(e.getBlock().getLocation(), new PendingChestDrop(
                    chestData.getItemStack(), chestData.isAutoCollect(), chest.getLocation()));
        }

        chest.onBreak(e);

        plugin.getProviders().notifyChestBreakListeners(e.getPlayer(), chest);

        chest.remove();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChestDropItem(BlockDropItemEvent e) {
        PendingChestDrop pendingDrop = pendingChestDrops.remove(e.getBlock().getLocation());

        if (pendingDrop == null)
            return;

        e.getItems().clear();

        ItemUtils.dropOrCollect(e.getPlayer(), pendingDrop.itemStack, pendingDrop.autoCollect,
                pendingDrop.chestLocation, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestExplode(EntityExplodeEvent e) {
        if (e.blockList().isEmpty())
            return;

        EntityType entityType = e.getEntityType();
        if (entityType == WIND_CHARGE_TYPE || entityType == BREEZE_WIND_CHARGE_TYPE)
            return;

        List<Block> blockList = new LinkedList<>(e.blockList());

        Player sourcePlayer = null;

        if (e.getEntity() instanceof TNTPrimed && ((TNTPrimed) e.getEntity()).getSource() instanceof Player) {
            sourcePlayer = (Player) ((TNTPrimed) e.getEntity()).getSource();
        }

        for (Block block : blockList) {
            Chest chest = plugin.getChestsManager().getChest(block.getLocation());

            if (chest == null)
                continue;

            e.blockList().remove(block);

            if (plugin.getSettings().explodeDropChance > 0 && (plugin.getSettings().explodeDropChance == 100 ||
                    ThreadLocalRandom.current().nextInt(101) <= plugin.getSettings().explodeDropChance)) {
                ChestData chestData = chest.getData();
                ItemUtils.dropOrCollect(null, chestData.getItemStack(), false,
                        chest.getLocation(), false);
            }

            chest.onBreak(new BlockBreakEvent(block, sourcePlayer));

            plugin.getProviders().notifyChestBreakListeners(sourcePlayer, chest);

            chest.remove();
            block.setType(Material.AIR);
        }
    }

    private static final class PendingChestDrop {

        private final ItemStack itemStack;
        private final boolean autoCollect;
        private final Location chestLocation;

        PendingChestDrop(ItemStack itemStack, boolean autoCollect, Location chestLocation) {
            this.itemStack = itemStack;
            this.autoCollect = autoCollect;
            this.chestLocation = chestLocation;
        }

    }

    @Nullable
    private static EntityType lookupEntityType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

}
