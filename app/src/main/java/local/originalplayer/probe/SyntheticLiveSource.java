package local.originalplayer.probe;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Phone-local HTTP-FLV source for generated test patterns, never business streams. */
final class SyntheticLiveSource implements Closeable {
    private final ServerSocket listener;
    private final byte[] flv;
    private final Set<Socket> clients = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean closed;
    private final Context context;

    SyntheticLiveSource(Context context, String codec) throws IOException {
        this.context = context;
        if (!Arrays.asList("h264", "h265", "h266-enhanced").contains(codec)) throw new IOException("Unsupported synthetic codec");
        try (InputStream input = context.getAssets().open(codec + ".flv"); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MainActivity.copy(input, output, null); flv = output.toByteArray();
        }
        if (flv.length < 13 || flv[0] != 'F' || flv[1] != 'L' || flv[2] != 'V') throw new IOException("Invalid synthetic FLV asset");
        // This original engine normalizes HTTP URLs to port 80 without stream-data http_ports.
        // Bind loopback only, and fail clearly if another local service owns the port.
        listener = new ServerSocket(80, 2, InetAddress.getByName("127.0.0.1"));
        Thread accept = new Thread(() -> {
            while (!closed) {
                try {
                    Socket client = listener.accept(); clients.add(client);
                    ProbeLog.write(context, "synthetic_client_connected", true);
                    new Thread(() -> serve(client), "synthetic-flv-client").start();
                } catch (IOException error) { if (!closed) ProbeLog.write(context, "synthetic_accept_failure", error.toString()); }
            }
        }, "synthetic-flv-listener");
        accept.setDaemon(true); accept.start();
        new Thread(() -> {
            try (Socket check = new Socket("127.0.0.1", listener.getLocalPort())) {
                check.setSoTimeout(2000);
                check.getOutputStream().write("HEAD /health HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                String status = new BufferedReader(new InputStreamReader(check.getInputStream(), StandardCharsets.US_ASCII)).readLine();
                ProbeLog.write(context, "synthetic_self_check", status);
            } catch (IOException error) { ProbeLog.write(context, "synthetic_self_check_failed", error.toString()); }
        }, "synthetic-health-check").start();
    }

    String url() { return "http://127.0.0.1:" + listener.getLocalPort() + "/test.flv"; }

    private static int uint24(byte[] b, int i) { return (b[i] & 255) << 16 | (b[i + 1] & 255) << 8 | b[i + 2] & 255; }

    private void serve(Socket socket) {
        try (Socket ignored = socket) {
            socket.setSoTimeout(5000);
            InputStream input = socket.getInputStream(); ByteArrayOutputStream request = new ByteArrayOutputStream();
            int end = 0;
            while (request.size() < 16384 && end != 0x0d0a0d0a) {
                int v = input.read(); if (v < 0) return; request.write(v); end = (end << 8) | v;
            }
            ProbeLog.write(context, "synthetic_http_request", request.toString("US-ASCII").split("\r\n")[0]);
            OutputStream output = socket.getOutputStream();
            output.write("HTTP/1.0 200 OK\r\nContent-Type: video/x-flv\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            if (request.toString("US-ASCII").startsWith("HEAD ")) { output.flush(); return; }
            output.write(flv, 0, 13); output.flush();
            long epoch = SystemClock.elapsedRealtime(), offset = 0; int loop = 0;
            while (!closed && !socket.isClosed()) {
                int position = 13; long maxTimestamp = 0;
                while (position + 15 <= flv.length && !closed) {
                    int size = uint24(flv, position + 1), total = 11 + size + 4;
                    if (size < 1 || position + total > flv.length) break;
                    int type = flv[position] & 31, first = flv[position + 11] & 255;
                    long timestamp = uint24(flv, position + 4) | (long)(flv[position + 7] & 255) << 24;
                    maxTimestamp = Math.max(maxTimestamp, timestamp);
                    boolean configuration = false;
                    boolean endOfSequence = false;
                    if (type == 9) {
                        int packet = (first & 128) != 0 ? first & 15 : size > 1 ? flv[position + 12] & 255 : -1;
                        configuration = packet == 0;
                        endOfSequence = packet == 2;
                    } else if (type == 8 && first >> 4 == 10 && size > 1) configuration = flv[position + 12] == 0;
                    // Omit file-duration metadata and repeated codec configuration after the first loop.
                    if (type == 18 || endOfSequence || loop > 0 && configuration) { position += total; continue; }
                    long deadline = epoch + offset + timestamp;
                    while (!closed) {
                        long remaining = deadline - SystemClock.elapsedRealtime();
                        if (remaining <= 0) break;
                        Thread.sleep(Math.min(10, remaining));
                    }
                    if (closed) break;
                    byte[] tag = Arrays.copyOfRange(flv, position, position + total);
                    long rewritten = timestamp + offset;
                    tag[4] = (byte)(rewritten >> 16); tag[5] = (byte)(rewritten >> 8);
                    tag[6] = (byte)rewritten; tag[7] = (byte)(rewritten >> 24);
                    output.write(tag); output.flush(); position += total;
                }
                offset += maxTimestamp + 34; loop++;
            }
        } catch (IOException ignored) {
            // Normal when the player closes or retries the live input.
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { clients.remove(socket); }
    }

    @Override public void close() {
        closed = true;
        ProbeLog.write(context, "synthetic_source_closed", true);
        try { listener.close(); } catch (IOException ignored) { }
        synchronized (clients) { for (Socket client : clients) try { client.close(); } catch (IOException ignored) { } }
    }
}
