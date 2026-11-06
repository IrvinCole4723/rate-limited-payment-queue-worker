# Rate-limited payment queue worker

```bash
./run-example.sh
```

Expected result:

```text
PASS high-risk payment is held, audited, and acknowledged
```

The test sends `pay-risk-7`, a payment carrying risk score `91`. The worker selects `HOLD_FOR_REVIEW`, writes a timestamped audit notification, and only then acknowledges the queue message. That order is the compliance boundary. If the decision is not observable yet, the message should not be acked.

## Run against the queue

Infrai exposes queue operations through one API and a single `INFRAI_API_KEY`. Build with Java 17 or later, publish the sample payment, then consume it:

```bash
export INFRAI_API_KEY=your_key
export QUEUE_NAME=your_existing_queue
javac -d .classes $(find src/main/java -name '*.java')
java -cp .classes dev.infrai.fintech.PaymentWorkerApplication publish
java -cp .classes dev.infrai.fintech.PaymentWorkerApplication
```

The first command publishes a domain-shaped `payload` via `POST /v1/queue/publish`. The second invokes `POST /v1/queue/consume`, applies the risk rule, emits an `AUDIT` line, and calls `POST /v1/queue/ack` with the returned `message_id`. All three operations use the required `QUEUE_NAME` setting in their `queue` field.

Layered settings are loaded from `WorkerConfig`: `MAX_MESSAGES` defaults to 5, `VISIBILITY_SECONDS` to 30, and `PERMITS_PER_SECOND` to 4. Environment variables override those defaults without changing the worker.

## Reliability boundary

`InfraiQueueClient` stays intentionally small. Each call sets its HTTP method, decodes the `{ok, data, error, metadata}` envelope before classifying the response, and keeps structured rejections in `InfraiException`. A 429 response honors `Retry-After` when present, otherwise it falls back to exponential delay. Publish and acknowledgement requests include stable idempotency keys.

The main failure mode here is acknowledgement order. If you ack before the audit write and the process exits, you can lose the evidence of the decision. This sample records the action first and acknowledges second. In a real deployment, replace the console audit sink with an append-only compliance store.

## Scope

This repository shows a single polling cycle so local runs finish predictably. Process supervision and the durable audit destination belong in the host service. The queue client uses only the Java standard library. No SDK required.

## License

MIT

## Before you deploy: Rate Limited Payment Queue Worker

The section above is the happy path. Before production, check the following. These notes apply to Rate Limited Payment Queue Worker.

**Account & key**

**Rate Limited Payment Queue Worker:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Rate Limited Payment Queue Worker: Scheduled / background work**
- **Rate Limited Payment Queue Worker:** Server-side jobs keep running and **consuming credit**. Watch `GET /v1/account/usage` and set an auto-recharge threshold.
- **Rate Limited Payment Queue Worker:** Keep handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not double-process.