package org.protege.editor.owl.server.change;

import org.protege.editor.owl.server.versioning.api.ChangeHistory;
import org.protege.editor.owl.server.versioning.api.HistoryFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ChangeDocumentPool {

    private static final Logger logger = LoggerFactory.getLogger(ChangeDocumentPool.class);

    public static final int DEFAULT_POOL_TIMEOUT = 900 * 1000; // 15 minutes

    private ScheduledExecutorService executorService;

    private final long timeout;

    private Map<HistoryFile, ChangeDocumentPoolEntry> pool = new TreeMap<>();

    private int consecutiveCleanupFailures = 0;

    public ChangeDocumentPool() {
        this(DEFAULT_POOL_TIMEOUT);
    }

    public ChangeDocumentPool(long timeout) {
        this.timeout = timeout;
        createTimeoutThread();
    }

    private void createTimeoutThread() {
        executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread th = new Thread(r, "Cache History Cleanup Thread");
                th.setDaemon(false);
                return th;
            }
        });
        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Entry<HistoryFile, ChangeDocumentPoolEntry> entry : new HashSet<>(pool.entrySet())) {
                        File f = entry.getKey();
                        ChangeDocumentPoolEntry poolEntry = entry.getValue();
                        synchronized (pool) {
                            long now = System.currentTimeMillis();
                            if (poolEntry.getLastTouch() + timeout < now) {
                                poolEntry.dispose();
                                pool.remove(f);
                                logger.info("Dispose in-memory cache history for " + f.getName());
                            }
                        }
                    }
                    consecutiveCleanupFailures = 0;
                }
                catch (Error t) {
                    logger.error("Exception caught cleaning change history pool.", t);
                    consecutiveCleanupFailures++;
                }
                catch (RuntimeException re) {
                    logger.error("Exception caught cleaning change history pool.", re);
                    consecutiveCleanupFailures++;
                }
                finally {
                    if (consecutiveCleanupFailures > 8) {
                        logger.error("Shutting down clean up thread for change history management.");
                        logger.error("Server could run out of memory");
                    }
                }
            }
        }, timeout, timeout, TimeUnit.MILLISECONDS);
    }

    public ChangeHistory lookup(HistoryFile historyFile) throws IOException {
        ChangeDocumentPoolEntry entry;
        synchronized (pool) {
            entry = pool.get(historyFile);
            if (entry == null) {
                entry = new ChangeDocumentPoolEntry(historyFile);
                pool.put(historyFile, entry);
                logger.info("Create in-memory cache history for " + historyFile.getName());
            }
        }
        return entry.readChangeHistory();
    }

    public void update(HistoryFile historyFile, ChangeHistory changeHistory) {
        synchronized (pool) {
            ChangeDocumentPoolEntry entry = pool.get(historyFile);
            if (entry == null) {
                entry = new ChangeDocumentPoolEntry(historyFile);
                entry.appendChangeHistory(changeHistory);
                pool.put(historyFile, entry);
            }
            else {
                entry.appendChangeHistory(changeHistory);
            }
        }
    }

    public void dispose() {
        synchronized (pool) {
            for (ChangeDocumentPoolEntry entry : pool.values()) {
                entry.dispose();
            }
            pool.clear();
        }
        executorService.shutdown();
    }

    public void sync() {
        List<ChangeDocumentPoolEntry> poolEntries;
        synchronized (pool) {
            poolEntries = new ArrayList<ChangeDocumentPoolEntry>(pool.values());
        }
        for (ChangeDocumentPoolEntry poolEntry : poolEntries) {
            poolEntry.sync();
        }
    }
}
