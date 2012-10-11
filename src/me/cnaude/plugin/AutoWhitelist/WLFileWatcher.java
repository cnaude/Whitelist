package me.cnaude.plugin.AutoWhitelist;

import java.io.File;
import java.util.TimerTask;

public class WLFileWatcher extends TimerTask {

    private File watchFile;
    private long lastModified;
    private volatile boolean m_WasChanged;
    private final WLMain plugin;

    WLFileWatcher(File file, WLMain instance) {
        this.watchFile = file;
        plugin = instance;
        this.lastModified = this.watchFile.lastModified();
    }

    @Override
    public void run() {
        if (this.lastModified != this.watchFile.lastModified()) {
            this.lastModified = this.watchFile.lastModified();
            if (!this.m_WasChanged) {
                this.m_WasChanged = true;
                plugin.logInfo("Whitelist file, " + watchFile.getName() + ", was updated. Reloading the file...");
            }
        }
    }

    public boolean wasFileModified() {
        return this.m_WasChanged;
    }

    public void resetFileModifiedState() {
        this.m_WasChanged = false;
    }
}