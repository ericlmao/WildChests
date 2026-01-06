package com.bgsoftware.wildchests.task;

import com.bgsoftware.wildchests.api.objects.chests.Chest;
import com.bgsoftware.wildchests.handlers.ChestsHandler;

public class ParticleTask implements Runnable {

    private final ChestsHandler chestsHandler;

    public ParticleTask(ChestsHandler chestsHandler) {
        this.chestsHandler = chestsHandler;
    }

    @Override
    public void run() {
        for (Chest chest : chestsHandler.getChests()) {
            chest.sendParticles();
        }
    }
}
