package dev.infrai.fintech;

public final class PaymentDecision {
    public enum Action { RELEASE, HOLD_FOR_REVIEW }

    public Action evaluate(PaymentEvent event) {
        return event.riskScore() >= 70 || event.amountMinor() >= 1_000_000
                ? Action.HOLD_FOR_REVIEW : Action.RELEASE;
    }
}
