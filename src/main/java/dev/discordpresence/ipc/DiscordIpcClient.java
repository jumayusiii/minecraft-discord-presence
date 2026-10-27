package dev.discordpresence.ipc;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Minimal Discord IPC client. It speaks the local socket protocol used by the
 * desktop Discord client, so no external library is needed.
 */
public final class DiscordIpcClient implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int MAX_FRAME_LENGTH = 65536;
    private static final long HANDSHAKE_TIMEOUT_MILLIS = 10_000L;
    private static final long RESPONSE_TIMEOUT_MILLIS = 10_000L;

    private final IpcPipe pipe;
    private final byte[] buffer = new byte[MAX_FRAME_LENGTH];
    private final AtomicLong nonce = new AtomicLong();
    private final ExecutorService readThread = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "discord-presence-reply");
        thread.setDaemon(true);
        return thread;
    });

    private int bufferStart;
    private int bufferEnd;
    private volatile boolean open = true;

    private DiscordIpcClient(IpcPipe pipe) {
        this.pipe = pipe;
    }

    public static DiscordIpcClient connect(String applicationId) throws IOException {
        return forPipe(IpcPipe.openFirst(), applicationId);
    }

    static DiscordIpcClient forPipe(IpcPipe pipe, String applicationId) throws IOException {
        DiscordIpcClient client = new DiscordIpcClient(pipe);
        try {
            client.handshake(applicationId);
            return client;
        } catch (IOException | RuntimeException e) {
            client.close();
            throw e;
        }
    }

    public void setActivity(JsonObject activity) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity == null ? JsonNull.INSTANCE : activity);

        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.add("args", args);

        String requestNonce = Long.toString(nonce.incrementAndGet());
        writeFrame(OP_FRAME, payload, requestNonce);
        awaitResponse("SET_ACTIVITY", requestNonce);
    }

    public boolean isOpen() {
        return open && pipe.isOpen();
    }

    @Override
    public void close() {
        open = false;
        readThread.shutdownNow();
        try {
            pipe.close();
        } catch (IOException e) {
            LOGGER.debug("Failed to close the Discord IPC pipe", e);
        }
    }

    private void handshake(String applicationId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("client_id", applicationId);
        writeFrame(OP_HANDSHAKE, payload, null);

        Frame frame = readFrameWithin(HANDSHAKE_TIMEOUT_MILLIS);
        String event = string(frame.payload(), "evt");
        if ("READY".equals(event)) {
            LOGGER.info("Discord accepted the connection, the presence is live now");
            return;
        }
        if (frame.opcode() == OP_CLOSE || "ERROR".equals(event)) {
            throw new IOException("Discord rejected the handshake: " + frame.payload());
        }
        throw new IOException("Unexpected handshake answer from Discord: " + frame.payload());
    }

    private void awaitResponse(String command, String requestNonce) throws IOException {
        for (int i = 0; i < 4; i++) {
            Frame frame = readFrameWithin(RESPONSE_TIMEOUT_MILLIS);
            JsonObject payload = frame.payload();
            String event = string(payload, "evt");
            if (frame.opcode() == OP_CLOSE) {
                throw new IOException("Discord closed the IPC connection: " + payload);
            }
            if ("ERROR".equals(event)) {
                LOGGER.warn("Discord rejected a presence update: {}", payload.get("data"));
                return;
            }
            if (command.equals(string(payload, "cmd"))
                    && (requestNonce == null || requestNonce.equals(string(payload, "nonce")))) {
                return;
            }
            LOGGER.debug("Ignoring unrelated Discord IPC frame: {}", payload);
        }
        throw new IOException("Discord did not confirm " + command);
    }

    private void writeFrame(int opcode, JsonObject payload, String requestNonce) throws IOException {
        if (requestNonce != null) {
            payload.addProperty("nonce", requestNonce);
        }
        byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(opcode);
        frame.putInt(json.length);
        frame.put(json);
        pipe.write(frame.array());
    }

    private Frame readFrameWithin(long timeoutMillis) throws IOException {
        Future<Frame> future = readThread.submit(this::readFrame);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            close();
            future.cancel(true);
            throw new IOException("Discord did not answer within " + timeoutMillis + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Discord", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException(String.valueOf(cause), cause);
        }
    }

    private Frame readFrame() throws IOException {
        ensureAvailable(8);
        int opcode = readInt();
        int length = readInt();
        if (length < 0 || length > MAX_FRAME_LENGTH) {
            throw new IOException("Invalid Discord IPC frame length: " + length);
        }
        ensureAvailable(length);
        JsonElement parsed = JsonParser.parseString(
                new String(buffer, bufferStart, length, StandardCharsets.UTF_8)
        );
        bufferStart += length;
        if (!parsed.isJsonObject()) {
            throw new IOException("Discord sent a non-object IPC frame");
        }
        return new Frame(opcode, parsed.getAsJsonObject());
    }

    private void ensureAvailable(int required) throws IOException {
        while (bufferEnd - bufferStart < required) {
            if (bufferStart > 0) {
                System.arraycopy(buffer, bufferStart, buffer, 0, bufferEnd - bufferStart);
                bufferEnd -= bufferStart;
                bufferStart = 0;
            }
            if (bufferEnd == buffer.length) {
                throw new IOException("Discord IPC frame is too large");
            }
            int read = pipe.read(buffer, bufferEnd, buffer.length - bufferEnd);
            if (read < 0) {
                throw new EOFException("Discord closed the IPC pipe");
            }
            bufferEnd += read;
        }
    }

    private int readInt() {
        int value = (buffer[bufferStart] & 0xFF)
                | (buffer[bufferStart + 1] & 0xFF) << 8
                | (buffer[bufferStart + 2] & 0xFF) << 16
                | (buffer[bufferStart + 3] & 0xFF) << 24;
        bufferStart += 4;
        return value;
    }

    private static String string(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private record Frame(int opcode, JsonObject payload) {
    }
}
