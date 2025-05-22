package net.gabbage.discordRoleSync.util;

import net.gabbage.discordRoleSync.DiscordRoleSync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DiscordTaskQueue {

    private final DiscordRoleSync plugin;
    private final ExecutorService executorService; // Single thread executor to process tasks sequentially

    public DiscordTaskQueue(DiscordRoleSync plugin) {
        this.plugin = plugin;
        // Using a single thread executor ensures tasks are run one after another in the order they are submitted.
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DiscordTaskQueue-Processor");
            t.setDaemon(true); // Allow JVM to exit if this is the only thread running
            return t;
        });
        plugin.getLogger().info("DiscordTaskQueue initialized.");
    }

    public void submit(Runnable task) {
        if (executorService.isShutdown() || executorService.isTerminated()) {
            plugin.getLogger().warning("DiscordTaskQueue is shutdown. Cannot submit new task.");
            // Optionally, run the task immediately or handle error
            // For now, just log and drop.
            return;
        }
        executorService.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing Discord task: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down DiscordTaskQueue...");
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("DiscordTaskQueue did not terminate in 10 seconds. Forcing shutdown...");
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.getLogger().severe("DiscordTaskQueue did not terminate even after forcing.");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
        plugin.getLogger().info("DiscordTaskQueue shut down.");
    }
}
