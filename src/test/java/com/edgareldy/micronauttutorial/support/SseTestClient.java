package com.edgareldy.micronauttutorial.support;

import io.restassured.path.json.JsonPath;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Minimal Server-Sent Events client on the JDK HttpClient: a daemon thread sends the request, records the status and
 * content type as soon as the headers arrive, then parses the body line by line into frames queued for the test, and
 * completes {@link #ended} when the server closes the stream.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// SSE testing technique: a Server-Sent Events response never ends by itself, so REST Assured (which waits for the full
// body) cannot read it. This client reads in a background thread and tests WAIT on the queue with a timeout.
public final class SseTestClient implements AutoCloseable {

    /**
     * One parsed Server-Sent Events frame: the event name and the JSON data line.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    public record Frame(String event, String data) {
        public JsonPath json() {
            return JsonPath.from(data);
        }

        public long orderId() {
            return json().getLong("data.id");
        }
    }

    public final CompletableFuture<Integer> status = new CompletableFuture<>();
    public final CompletableFuture<String> contentType = new CompletableFuture<>();
    /** Completed when the server ended the body normally (never on a client side close or a read error). */
    public final CompletableFuture<Void> ended = new CompletableFuture<>();
    public final LinkedBlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    public final List<Frame> all = new CopyOnWriteArrayList<>();
    private volatile Stream<String> body;
    private volatile boolean closedByClient;

    public SseTestClient(String url, String token) {
        Thread thread = new Thread(() -> run(url, token), "sse-test-client");
        thread.setDaemon(true);
        thread.start();
    }

    private void run(String url, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "text/event-stream")
                    .header("Authorization", AuthTestSupport.bearer(token))
                    .GET().build();
            HttpResponse<Stream<String>> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofLines());
            body = response.body();
            contentType.complete(response.headers().firstValue("Content-Type").orElse(""));
            status.complete(response.statusCode());
            String event = null;
            String data = null;
            for (var it = body.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.isEmpty()) {
                    if (event != null || data != null) {
                        Frame frame = new Frame(event, data);
                        all.add(frame);
                        frames.add(frame);
                    }
                    event = null;
                    data = null;
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data = line.substring(5).trim();
                }
            }
            if (!closedByClient) {
                ended.complete(null);
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            // Closing the client ends the read with an exception: nothing to report.
            status.completeExceptionally(e);
            ended.completeExceptionally(e);
        }
    }

    /** The next frame of any kind, or null after the timeout. */
    public Frame next(long millis) throws InterruptedException {
        return frames.poll(millis, TimeUnit.MILLISECONDS);
    }

    /** The next "order" frame (heartbeats are skipped), or null when none arrives within the timeout. */
    public Frame nextOrder(long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (true) {
            long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            Frame frame = left <= 0 ? null : frames.poll(left, TimeUnit.MILLISECONDS);
            if (frame == null || "order".equals(frame.event())) {
                return frame;
            }
        }
    }

    /** The order ids received so far, in order. */
    public List<Long> orderIds() {
        return all.stream().filter(f -> "order".equals(f.event())).map(Frame::orderId).toList();
    }

    @Override
    public void close() {
        closedByClient = true;
        Stream<String> stream = body;
        if (stream != null) {
            stream.close();
        }
    }
}
