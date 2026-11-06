package dev.infrai.fintech;

public record WorkerConfig(String apiKey, int maxMessages, int visibilitySeconds, int permitsPerSecond) {
    public static WorkerConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        return new WorkerConfig(key, integer("MAX_MESSAGES", 5), integer("VISIBILITY_SECONDS", 30), integer("PERMITS_PER_SECOND", 4));
    }

    private static int integer(String name, int fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : Integer.parseInt(value);
    }
}
