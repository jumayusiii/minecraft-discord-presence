package dev.discordpresence.client;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Detects whether Minecraft is running under Prism Launcher so the small
 * presence image can show the launcher instead of a generic mode icon.
 */
final class PrismDetector {
    private static final long RESCAN_INTERVAL_MILLIS = 30_000L;
    private static volatile boolean running;
    private static boolean started;

    private PrismDetector() {
    }

    static boolean isPrismRunning() {
        if (!started) {
            start();
        }
        return running;
    }

    private static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        if (hasPrismAncestor()) {
            running = true;
            return;
        }
        Thread watcher = new Thread(PrismDetector::watch, "discord-presence-prism");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void watch() {
        try {
            while (true) {
                running = isPrismProcessRunning();
                Thread.sleep(RESCAN_INTERVAL_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasPrismAncestor() {
        Optional<ProcessHandle> parent = ProcessHandle.current().parent();
        for (int depth = 0; depth < 8 && parent.isPresent(); depth++) {
            ProcessHandle handle = parent.get();
            if (isPrism(handle)) {
                return true;
            }
            parent = handle.parent();
        }
        return false;
    }

    private static boolean isPrismProcessRunning() {
        try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
            return processes.anyMatch(PrismDetector::isPrism);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isPrism(ProcessHandle handle) {
        String command = handle.info().command().orElse("");
        if (command.isEmpty()) {
            return false;
        }
        int separator = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        String name = (separator >= 0 ? command.substring(separator + 1) : command).toLowerCase(Locale.ROOT);
        return name.contains("prism");
    }
}
