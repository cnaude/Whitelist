package com.cnaude.autowhitelist;

import java.io.File;
import org.bukkit.scheduler.BukkitTask;

public class FileWatcher {

    private final File watchFile;
    private long lastModified;
    private volatile boolean wasFileChanged;
    private final AutoWhitelist plugin;
    private final BukkitTask bt;
    protected final String fileName;

    public FileWatcher(File file, long interval, AutoWhitelist instance) {
        this.plugin = instance;
        this.watchFile = file;
        this.fileName = file.getName();
        
        plugin.logInfo("Watching " + watchFile.getName() + " for changes...");

        bt = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, new Runnable() {
            @Override
            public void run() {
                if (lastModified != watchFile.lastModified()) {
                    lastModified = watchFile.lastModified();
                    if (!wasFileChanged) {
                        wasFileChanged = true;
                        plugin.logInfo("Whitelist file, " + watchFile.getName() + ", was updated. Reloading the file...");
                    }
                }
            }
        }, 0L, interval);
    }

    /**
     *
     */
    public void cancel() {
        bt.cancel();
    }

    public boolean wasFileModified() {
        return this.wasFileChanged;
    }

    public void resetFileModifiedState() {
        this.wasFileChanged = false;
    }
}
