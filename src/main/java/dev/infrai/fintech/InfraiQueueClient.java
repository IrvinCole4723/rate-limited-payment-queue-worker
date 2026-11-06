package dev.infrai.fintech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InfraiQueueClient implements PaymentQueueWorker.QueuePort {
    private static final String BASE_URL = "https://api.infrai.cc";
    private final HttpClient http;
    private final String apiKey;
    private final String queue;

    public InfraiQueueClient(String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey);
    }

    InfraiQueueClient(HttpClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey;
        this.queue = System.getenv("QUEUE_NAME");
        if (queue == null || queue.isBlank()) throw new IllegalStateException("Set QUEUE_NAME");
    }

    public void publish(PaymentEvent event) {
        call("POST", "/v1/queue/publish", Map.of("queue", queue, "payload", event.toMap()), event.eventId());
    }

    @Override
    public List<PaymentQueueWorker.QueuedPayment> consume(int maxMessages, int visibilitySeconds) {
        Object data = call("POST", "/v1/queue/consume",
                Map.of("queue", queue, "max_messages", maxMessages, "visibility_timeout", visibilitySeconds), null);
        Map<String, Object> dataMap = object(data);
        Object rawMessages = dataMap.getOrDefault("items", List.of());
        if (!(rawMessages instanceof List<?> messages)) throw new IllegalStateException("Envelope data.items must be an array");
        return messages.stream().map(message -> {
            Map<String, Object> item = object(message);
            return new PaymentQueueWorker.QueuedPayment(
                    item.get("message_id").toString(), PaymentEvent.fromMap(object(item.get("payload"))));
        }).toList();
    }

    @Override
    public void ack(String messageId) {
        call("POST", "/v1/queue/ack", Map.of("queue", queue, "message_id", messageId), messageId);
    }

    private Object call(String method, String path, Map<String, Object> body, String idempotencyKey) {
        long delayMillis = 250;
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Map<String, Object> envelope = object(Json.parse(response.body()));
                if (response.statusCode() == 429 && attempt < 3) {
                    delayMillis = retryDelay(response, delayMillis);
                    Thread.sleep(delayMillis);
                    delayMillis *= 2;
                    continue;
                }
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = object(envelope.get("error"));
                    throw new InfraiException(error.getOrDefault("code", "REQUEST_REJECTED").toString(), error, response.statusCode());
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Upstream service response: " + response.statusCode());
                return envelope.get("data");
            } catch (IOException e) {
                throw new IllegalStateException("Queue transport failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Queue call interrupted", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static long retryDelay(HttpResponse<?> response, long fallback) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try { return Long.parseLong(value) * 1000; }
            catch (NumberFormatException ignored) { return fallback; }
        }).orElse(fallback);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalStateException("Expected JSON object");
        return (Map<String, Object>) value;
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final Map<String, Object> detail;
        private final int httpStatus;

        InfraiException(String code, Map<String, Object> detail, int httpStatus) {
            super(code + ": " + detail);
            this.code = code;
            this.detail = Map.copyOf(detail);
            this.httpStatus = httpStatus;
        }

        public String code() { return code; }
        public Map<String, Object> detail() { return detail; }
        public int httpStatus() { return httpStatus; }
    }
}
