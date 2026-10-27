package dev.discordpresence.ipc;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transport for Discord's local IPC endpoint: a named pipe on Windows and a
 * Unix domain socket elsewhere.
 */
public abstract class IpcPipe implements Closeable {
    public abstract int read(byte[] buffer, int offset, int length) throws IOException;

    public abstract void write(byte[] data) throws IOException;

    public abstract boolean isOpen();

    public static IpcPipe openFirst() throws IOException {
        IOException failure = null;
        for (String location : locations()) {
            try {
                return isWindows() ? new WindowsPipe(location) : new UnixPipe(location);
            } catch (IOException | RuntimeException e) {
                failure = e instanceof IOException io ? io : new IOException(e.toString(), e);
            }
        }
        throw failure != null ? failure : new IOException("No Discord IPC endpoint found");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static List<String> locations() {
        ArrayList<String> locations = new ArrayList<String>();
        if (isWindows()) {
            for (int i = 0; i < 10; i++) {
                locations.add("\\\\?\\pipe\\discord-ipc-" + i);
            }
            for (int i = 0; i < 10; i++) {
                locations.add("\\\\.\\pipe\\discord-ipc-" + i);
            }
            return locations;
        }

        ArrayList<String> directories = new ArrayList<String>();
        addDirectory(directories, System.getenv("XDG_RUNTIME_DIR"));
        addDirectory(directories, System.getenv("TMPDIR"));
        addDirectory(directories, System.getenv("TMP"));
        addDirectory(directories, System.getenv("TEMP"));
        String home = System.getProperty("user.home");
        if (home != null) {
            addDirectory(directories, home + "/Library/Application Support/discord");
        }
        addDirectory(directories, "/tmp");

        ArrayList<String> prefixes = new ArrayList<String>();
        for (String directory : directories) {
            prefixes.add(directory);
            prefixes.add(directory + "/app/com.discordapp.Discord");
            prefixes.add(directory + "/app/com.discordapp.DiscordCanary");
            prefixes.add(directory + "/snap.discord");
            prefixes.add(directory + "/snap.discord-canary");
        }

        for (String prefix : prefixes) {
            for (int i = 0; i < 10; i++) {
                locations.add(prefix + "/discord-ipc-" + i);
            }
        }
        return locations;
    }

    private static void addDirectory(List<String> directories, String path) {
        if (path != null && !path.isBlank() && !directories.contains(path)) {
            directories.add(path);
        }
    }

    private static final class WindowsPipe extends IpcPipe {
        private final RandomAccessFile file;
        private volatile boolean open = true;

        WindowsPipe(String path) throws IOException {
            this.file = new RandomAccessFile(path, "rw");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return file.read(buffer, offset, length);
        }

        @Override
        public void write(byte[] data) throws IOException {
            file.write(data);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
            file.close();
        }
    }

    private static final class UnixPipe extends IpcPipe {
        private final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        private final InputStream input;
        private final OutputStream output;
        private volatile boolean open = true;

        UnixPipe(String path) throws IOException {
            try {
                channel.connect(UnixDomainSocketAddress.of(Path.of(path)));
            } catch (IOException | RuntimeException e) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // Preserve the original connection failure.
                }
                throw new IOException(e.toString(), e);
            }
            input = Channels.newInputStream(channel);
            output = Channels.newOutputStream(channel);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return input.read(buffer, offset, length);
        }

        @Override
        public void write(byte[] data) throws IOException {
            output.write(data);
            output.flush();
        }

        @Override
        public boolean isOpen() {
            return open && channel.isConnected();
        }

        @Override
        public void close() throws IOException {
            open = false;
            channel.close();
        }
    }
}
