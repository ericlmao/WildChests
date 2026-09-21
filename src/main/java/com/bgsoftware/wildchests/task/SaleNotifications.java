package com.bgsoftware.wildchests.task;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Chat summaries only. Never owns inventory, payments or offline balances. */
public final class SaleNotifications {
    public static final List<String> MODES = Collections.unmodifiableList(Arrays.asList("default", "always", "1m", "5m", "15m", "30m", "1h", "never"));
    private final Path file;
    private final Properties preferences = new Properties();
    private final Map<UUID, Summary> pending = new HashMap<>();

    public SaleNotifications(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                preferences.load(input);
            }
        }
    }

    public synchronized String getMode(UUID player) {
        String mode = preferences.getProperty(player.toString(), "default");
        return MODES.contains(mode) ? mode : "default";
    }

    public synchronized void setMode(UUID player, String mode, long now) throws IOException {
        if (!MODES.contains(mode))
            throw new IllegalArgumentException("Unknown notification mode");
        Properties updated = new Properties();
        updated.putAll(preferences);
        if (mode.equals("default")) updated.remove(player.toString());
        else updated.setProperty(player.toString(), mode);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "notifications-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                updated.store(output, "WildChests autosell notification preferences");
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        preferences.clear();
        preferences.putAll(updated);
        if (mode.equals("never")) pending.remove(player);
        else if (pending.containsKey(player)) pending.get(player).started = now;
    }

    public synchronized void add(UUID player, String material, int amount, double earnings, long now, long defaultInterval) {
        if (interval(getMode(player), defaultInterval) < 0) return;
        Summary summary = pending.computeIfAbsent(player, key -> new Summary(now));
        Line line = summary.items.computeIfAbsent(material, key -> new Line());
        line.amount += amount;
        line.earnings = line.earnings.add(BigDecimal.valueOf(earnings));
    }

    public synchronized Map<UUID, Summary> drain(long now, long defaultInterval) {
        Map<UUID, Summary> ready = new HashMap<>();
        pending.entrySet().removeIf(entry -> {
            long interval = interval(getMode(entry.getKey()), defaultInterval);
            if (interval < 0) return true;
            if (now - entry.getValue().started >= interval) {
                ready.put(entry.getKey(), entry.getValue());
                return true;
            }
            return false;
        });
        return ready;
    }

    private static long interval(String mode, long defaultInterval) {
        switch (mode) {
            case "never": return -1;
            case "always": return 0;
            case "1m": return 60000;
            case "5m": return 300000;
            case "15m": return 900000;
            case "30m": return 1800000;
            case "1h": return 3600000;
            default: return defaultInterval > 0 ? defaultInterval : -1;
        }
    }

    public static final class Summary {
        private long started;
        public final Map<String, Line> items = new LinkedHashMap<>();
        private Summary(long started) { this.started = started; }
    }

    public static final class Line {
        public long amount;
        public BigDecimal earnings = BigDecimal.ZERO;
    }
}
