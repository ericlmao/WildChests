package com.bgsoftware.wildchests.hooks;

import com.bgsoftware.common.shopsbridge.BulkTransaction;
import com.bgsoftware.common.shopsbridge.IShopsBridge;
import com.bgsoftware.common.shopsbridge.ShopsProvider;
import com.bgsoftware.common.shopsbridge.Transaction;
import com.bgsoftware.wildchests.WildChestsPlugin;
import com.bgsoftware.wildchests.api.hooks.PricesProvider;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PricesProvider_ShopsBridgeWrapper implements PricesProvider {

    private final IShopsBridge shopsBridge;
    private BulkTransaction bulkTransaction;

    // Cache for transaction lookups to avoid repeated calls to external plugins
    // Short TTL to handle multiple stacks of the same material in a single sell operation
    private final Cache<TransactionKey, Transaction> transactionCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .build();

    public PricesProvider_ShopsBridgeWrapper(ShopsProvider shopsProvider, IShopsBridge shopsBridge) {
        WildChestsPlugin.log(" - Using " + shopsProvider.getPluginName() + " as PricesProvider.");
        this.shopsBridge = shopsBridge;
    }

    @Override
    public double getPrice(OfflinePlayer offlinePlayer, ItemStack itemStack) {
        return getTransaction(offlinePlayer, itemStack).getPrice().doubleValue();
    }

    public Transaction getTransaction(OfflinePlayer offlinePlayer, ItemStack itemStack) {
        TransactionKey key = new TransactionKey(
                offlinePlayer.getUniqueId(),
                itemStack.getType().name(),
                itemStack.getDurability(),
                itemStack.getAmount()
        );

        Transaction cachedTransaction = transactionCache.getIfPresent(key);
        if (cachedTransaction != null) {
            return cachedTransaction;
        }

        Transaction transaction = (this.bulkTransaction == null ? this.shopsBridge : this.bulkTransaction)
                .getSellPrice(offlinePlayer, itemStack);

        transactionCache.put(key, transaction);
        return transaction;
    }

    public void startBulkTransaction() {
        this.bulkTransaction = this.shopsBridge.startBulkTransaction();
        // Clear cache when starting a new bulk transaction to ensure fresh data
        transactionCache.invalidateAll();
    }

    public void stopBulkTransaction() {
        this.bulkTransaction = null;
        // Clear cache when stopping bulk transaction
        transactionCache.invalidateAll();
    }

    /**
     * Cache key for transaction lookups.
     * Includes player, material type, data value, and amount.
     */
    private static class TransactionKey {
        private final UUID playerUuid;
        private final String materialName;
        private final short durability;
        private final int amount;

        TransactionKey(UUID playerUuid, String materialName, short durability, int amount) {
            this.playerUuid = playerUuid;
            this.materialName = materialName;
            this.durability = durability;
            this.amount = amount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TransactionKey that = (TransactionKey) o;
            return durability == that.durability &&
                    amount == that.amount &&
                    Objects.equals(playerUuid, that.playerUuid) &&
                    Objects.equals(materialName, that.materialName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerUuid, materialName, durability, amount);
        }
    }

}
