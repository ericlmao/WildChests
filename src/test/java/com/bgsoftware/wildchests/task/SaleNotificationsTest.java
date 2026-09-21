package com.bgsoftware.wildchests.task;

import org.junit.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class SaleNotificationsTest {
    private SaleNotifications create() throws Exception {
        return new SaleNotifications(Files.createTempDirectory("sale-notifications").resolve("preferences.properties"));
    }

    @Test public void independentCadencesAndBoundedTotals() throws Exception {
        SaleNotifications queue = create();
        UUID minute = UUID.randomUUID(), hour = UUID.randomUUID(), never = UUID.randomUUID();
        queue.setMode(minute, "1m", 0);
        queue.setMode(hour, "1h", 0);
        queue.setMode(never, "never", 0);
        for (int i = 0; i < 10000; i++) {
            queue.add(minute, "STONE", 64, 0.1, 0, 120000);
            queue.add(hour, "STONE", 64, 0.1, 0, 120000);
            queue.add(never, "STONE", 64, 0.1, 0, 120000);
        }
        assertTrue(queue.drain(59999, 120000).isEmpty());
        Map<UUID, SaleNotifications.Summary> first = queue.drain(60000, 120000);
        assertEquals(1, first.size());
        assertEquals(1, first.get(minute).items.size());
        assertEquals(640000, first.get(minute).items.get("STONE").amount);
        assertEquals(new BigDecimal("1000.0"), first.get(minute).items.get("STONE").earnings);
        assertEquals(1, queue.drain(3600000, 120000).size());
        assertTrue(queue.drain(7200000, 120000).isEmpty());
    }

    @Test public void changesPreserveTotalsExceptNeverAndPersistOnlyPreferences() throws Exception {
        Path file = Files.createTempDirectory("sale-preferences").resolve("preferences.properties");
        SaleNotifications queue = new SaleNotifications(file);
        UUID player = UUID.randomUUID();
        queue.add(player, "STONE", 1, 2, 0, 120000);
        queue.setMode(player, "1h", 1000);
        assertTrue(queue.drain(3600000, 120000).isEmpty());
        assertEquals(1, queue.drain(3601000, 120000).get(player).items.get("STONE").amount);
        queue.add(player, "STONE", 1, 2, 3601000, 120000);
        SaleNotifications restarted = new SaleNotifications(file);
        assertEquals("1h", restarted.getMode(player));
        assertTrue(restarted.drain(9999999, 120000).isEmpty());
        queue.setMode(player, "never", 3601001);
        queue.setMode(player, "always", 3601002);
        assertTrue(queue.drain(9999999, 120000).isEmpty());
    }

    @Test public void defaultDisabledAndAlwaysAndDefaultReset() throws Exception {
        SaleNotifications queue = create();
        UUID player = UUID.randomUUID();
        queue.add(player, "STONE", 1, 2, 0, 0);
        assertTrue(queue.drain(9999999, 0).isEmpty());
        queue.add(player, "STONE", 1, 2, 0, 120000);
        assertTrue(queue.drain(119999, 120000).isEmpty());
        assertEquals(1, queue.drain(120000, 120000).size());
        queue.setMode(player, "always", 0);
        queue.add(player, "STONE", 1, 2, 0, 0);
        assertEquals(1, queue.drain(0, 0).size());
        queue.setMode(player, "default", 0);
        assertEquals("default", queue.getMode(player));
    }
    @Test public void concurrentProducersAndDrainNeverLoseSales() throws Exception {
        SaleNotifications queue = create();
        UUID player = UUID.randomUUID();
        queue.setMode(player, "always", 0);
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 10000; i++) queue.add(player, "STONE", 1, 1, 0, 0);
        });
        producer.start();
        long total = 0;
        do {
            SaleNotifications.Summary summary = queue.drain(0, 0).get(player);
            if (summary != null) total += summary.items.get("STONE").amount;
        } while (producer.isAlive());
        producer.join();
        SaleNotifications.Summary last = queue.drain(0, 0).get(player);
        if (last != null) total += last.items.get("STONE").amount;
        assertEquals(10000, total);
    }

}
