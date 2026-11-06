package dev.infrai.fintech;

import java.time.Clock;

public final class PaymentWorkerApplication {
    private PaymentWorkerApplication() {}

    public static void main(String[] args) {
        WorkerConfig config = WorkerConfig.fromEnvironment();
        InfraiQueueClient infrai = new InfraiQueueClient(config.apiKey());
        PaymentQueueWorker worker = new PaymentQueueWorker(infrai, new PaymentDecision(),
                notice -> System.out.println("AUDIT " + Json.write(java.util.Map.of(
                        "event_id", notice.eventId(), "account_id", notice.accountId(),
                        "action", notice.action().name(), "recorded_at", notice.recordedAt().toString()))),
                Clock.systemUTC(), config.permitsPerSecond());

        if (args.length > 0 && args[0].equals("publish")) {
            infrai.publish(new PaymentEvent("pay-2026-0906", "acct-1042", 125_00, "USD", 18));
            System.out.println("Published payment pay-2026-0906");
            return;
        }
        System.out.println("Handled " + worker.runOnce(config.maxMessages(), config.visibilitySeconds()) + " payment(s)");
    }
}
