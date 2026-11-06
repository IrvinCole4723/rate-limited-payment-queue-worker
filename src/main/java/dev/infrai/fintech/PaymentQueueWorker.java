package dev.infrai.fintech;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public final class PaymentQueueWorker {
    public interface QueuePort {
        List<QueuedPayment> consume(int maxMessages, int visibilitySeconds);
        void ack(String messageId);
    }

    public record QueuedPayment(String messageId, PaymentEvent event) {}
    public record AuditNotification(String eventId, String accountId, PaymentDecision.Action action, Instant recordedAt) {}

    private final QueuePort queue;
    private final PaymentDecision decision;
    private final Consumer<AuditNotification> auditSink;
    private final Clock clock;
    private final long intervalNanos;
    private long nextPermit;

    public PaymentQueueWorker(QueuePort queue, PaymentDecision decision, Consumer<AuditNotification> auditSink,
                              Clock clock, int permitsPerSecond) {
        this.queue = queue;
        this.decision = decision;
        this.auditSink = auditSink;
        this.clock = clock;
        this.intervalNanos = 1_000_000_000L / permitsPerSecond;
    }

    public int runOnce(int maxMessages, int visibilitySeconds) {
        int handled = 0;
        for (QueuedPayment queued : queue.consume(maxMessages, visibilitySeconds)) {
            acquirePermit();
            PaymentDecision.Action action = decision.evaluate(queued.event());
            auditSink.accept(new AuditNotification(queued.event().eventId(), queued.event().accountId(), action, clock.instant()));
            queue.ack(queued.messageId());
            handled++;
        }
        return handled;
    }

    private void acquirePermit() {
        long now = System.nanoTime();
        long wait = Math.max(0, nextPermit - now);
        if (wait > 0) {
            try { Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Worker interrupted", e); }
        }
        nextPermit = Math.max(now, nextPermit) + intervalNanos;
    }
}
