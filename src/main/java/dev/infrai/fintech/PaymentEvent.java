package dev.infrai.fintech;

import java.util.LinkedHashMap;
import java.util.Map;

public record PaymentEvent(String eventId, String accountId, long amountMinor, String currency, int riskScore) {
    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("event_id", eventId);
        value.put("account_id", accountId);
        value.put("amount_minor", amountMinor);
        value.put("currency", currency);
        value.put("risk_score", riskScore);
        return value;
    }

    public static PaymentEvent fromMap(Map<String, Object> value) {
        return new PaymentEvent(value.get("event_id").toString(), value.get("account_id").toString(),
                ((Number) value.get("amount_minor")).longValue(), value.get("currency").toString(),
                ((Number) value.get("risk_score")).intValue());
    }
}
