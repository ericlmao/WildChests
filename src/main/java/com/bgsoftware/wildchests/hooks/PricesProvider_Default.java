package com.bgsoftware.wildchests.hooks;

import com.bgsoftware.wildchests.api.hooks.PricesProvider;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public final class PricesProvider_Default implements PricesProvider {

    public static Map<String, Double> prices = new ConcurrentHashMap<>();

    // Cache for material prices to avoid repeated lookups
    private static final LoadingCache<String, Double> priceCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Double>() {
                @Override
                public Double load(String key) {
                    // Return the price from the prices map, or -1 if not found
                    return prices.getOrDefault(key, -1.0);
                }
            });

    /**
     * Clears the price cache. Should be called when prices are reloaded from config.
     */
    public static void clearCache() {
        priceCache.invalidateAll();
    }

    @Override
    public double getPrice(OfflinePlayer offlinePlayer, ItemStack itemStack) {
        String materialName = itemStack.getType().name();

        // First check for 'TYPE' price
        Double price = priceCache.getUnchecked(materialName);
        if (price != -1.0) {
            return price * itemStack.getAmount();
        }

        // Then check for 'TYPE:DATA' price
        String materialWithData = materialName + ":" + itemStack.getDurability();
        price = priceCache.getUnchecked(materialWithData);
        if (price != -1.0) {
            return price * itemStack.getAmount();
        }

        // Couldn't find a price for this item
        return -1;
    }
}
