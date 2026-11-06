package dev.infrai.fintech;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class PaymentQueueWorkerTest {
    public static void main(String[] args) {
        PaymentEvent risky = new PaymentEvent("pay-risk-7", "acct-9", 45_00, "USD", 91);
        FakeQueue queue = new FakeQueue(new PaymentQueueWorker.QueuedPayment("msg-7", risky));
        List<PaymentQueueWorker.AuditNotification> audit = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-09-06T08:00:00Z"), ZoneOffset.UTC);
        PaymentQueueWorker worker = new PaymentQueueWorker(queue, new PaymentDecision(), audit::add, clock, 1000);

        check(worker.runOnce(1, 30) == 1, "one payment handled");
        check(audit.size() == 1, "one audit notification");
        check(audit.get(0).action() == PaymentDecision.Action.HOLD_FOR_REVIEW, "high risk payment held");
        check(audit.get(0).recordedAt().equals(clock.instant()), "audit time is deterministic");
        check(queue.acked.equals(List.of("msg-7")), "decision acknowledged after audit");
        System.out.println("PASS high-risk payment is held, audited, and acknowledged");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeQueue implements PaymentQueueWorker.QueuePort {
        private final PaymentQueueWorker.QueuedPayment payment;
        private final List<String> acked = new ArrayList<>();
        private FakeQueue(PaymentQueueWorker.QueuedPayment payment) { this.payment = payment; }
        public List<PaymentQueueWorker.QueuedPayment> consume(int maxMessages, int visibilitySeconds) { return List.of(payment); }
        public void ack(String messageId) { acked.add(messageId); }
    }
}
