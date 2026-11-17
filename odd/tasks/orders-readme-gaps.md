# Orders: close ReadMe.md gaps
Objective: make orders-service meet the assignment (`../ReadMe.md`): shipping type, notifications for both events, durable at-least-once events, S3 guide URL, correct idempotency.
Branch: `feat/orders-readme-gaps` (units built in isolated worktrees `feat/orders-messaging`, `feat/orders-creation`, merged back). Delivery: single-pr. Test runner: `./mvnw test` (H2, no Docker/AWS).

## Specs

- **S1 Shipping type (RF-1).** ReadMe RF-1: "un cliente ingresa origen, destino (departamento + municipio), peso y tipo de envío". User decision: "Solo informativo". `CreateOrderRequest.shippingType` accepts `STANDARD` | `EXPRESS`; an invalid value yields 400. Assumption: the field is optional and defaults to `STANDARD`, so the current frontend (which does not send it) keeps working. It is persisted, returned in every `OrderResponse` as `shippingType`, does NOT change the fare and is NOT sent to Coverage.
- **S2 Idempotent replay is correct.** Replay of `POST /orders` with an existing `idempotencyKey`:
  - existing order in `PENDING_VALIDATION` (Coverage failed earlier) → validation against Coverage runs again and the normal flow continues (`VALIDATED` → 201, rejected → `REJECTED` + 400, Coverage still down → 503 and the order stays `PENDING_VALIDATION`);
  - existing order in `REJECTED` → 400 again with the original rejection message (persist it);
  - any other status → the existing order is returned unchanged (201, as today).
  Two concurrent requests with the same key must not produce a 500: the loser returns the stored order (same rules as a replay).
- **S3 Fan-out through SNS.** ReadMe diagram: "(evento: PedidoConfirmado) → SQS/SNS" feeding both "AWS Lambda (genera PDF guía)" and "[Notifications MS]". Orders publishes its events to an SNS topic (`aws.sns.order-events-topic-arn`, env `ORDER_EVENTS_TOPIC_ARN`) instead of one SQS queue, with SNS message attribute `eventType` (String). Message body JSON is built with a JSON library (no hand-made escaping) and keeps today's shape for `PedidoConfirmado`: `{"eventType":"PedidoConfirmado","orderId":"…","customerEmail":"…"}`. Documented topology: topic → SQS queue for the Lambda (filter policy `eventType = PedidoConfirmado`) and topic → SQS queue for Notifications (no filter, Raw Message Delivery enabled so Notifications parses the body unchanged). Orders no longer publishes to SQS directly.
- **S4 Guide-ready event (RF-5).** ReadMe RF-5: "Notificar al cliente … cuando el pedido es confirmado y cuando la guía está lista." When `PUT /orders/{id}/guide` moves the order to `GUIDE_READY`, Orders emits `{"eventType":"GuiaLista","orderId":"…","customerEmail":"…"}` (attribute `eventType=GuiaLista`) through the same mechanism as S3/S5.
- **S5 Transactional outbox (at-least-once).** ReadMe: "las colas típicas entregan mensajes al menos una vez … el diseño debe tolerar duplicados". The state change of `confirm` and of `markGuideReady` and its event are stored in ONE database transaction (outbox table). A scheduled relay publishes pending outbox events to SNS, marks an event published only after SNS accepted it, and leaves it pending (retried on the next run, attempt count + last error recorded) when publishing fails. `POST /orders/{id}/confirm` returns 200 even when SNS is down; the event is published later. Domain/usecase stay framework-free (transaction and outbox behind output ports). The relay must never crash the app or the test context when AWS is unreachable.
- **S6 Guide stored in S3, URL presigned on read (RF-4).** ReadMe: "el microservicio de Orders debe exponer la URL (puede ser prefirmada)". Lambda contract: `PUT /orders/{id}/guide` body `{"guideKey":"<S3 object key>"}` (`guideKey` not blank, 400 otherwise). Orders persists the key, never a URL. Every response that returns an order in `GUIDE_READY` exposes `guideUrl` as a presigned S3 GET URL generated at response time (bucket `aws.s3.guides-bucket`, env `GUIDES_BUCKET`, TTL `aws.s3.guide-url-ttl`, default 15m); otherwise `guideUrl` is null. The response field name `guideUrl` is unchanged (the frontend reads it). The signer is an output port; no output port is called from an entry point.
- **S8 No WireMock stub.** User: "Quitar el stub WireMock (coveragestub) y apuntar al Coverage Service real." `coverage-stub/` is removed. `coverage.base-url` points to the real Coverage Service and is overridable with `COVERAGE_BASE_URL` (default `http://127.0.0.1:8081`).
- **S9 Idempotent MarkGuideReady.** User: "evitar error InvalidOrderStateException si llega dos veces MarkGuideReady". A repeated `PUT /orders/{id}/guide` with the same `guideKey` on a `GUIDE_READY` order returns 200 with the unchanged order, saves nothing and emits no second `GuiaLista`. A different `guideKey` on a `GUIDE_READY` order is a real conflict and stays 409. Other statuses keep today's 409.
- **S10 AWS profile.** User: "Configurar perfil application-aws.yaml para RDS/SQS reales." The `aws` profile declares RDS (`DB_PASSWORD`), the SNS topic (`ORDER_EVENTS_TOPIC_ARN`; SNS replaces direct SQS publishing per S3), the S3 bucket (`GUIDES_BUCKET`), the region (`AWS_REGION`) and Coverage (`COVERAGE_BASE_URL`). Defaults keep README "Opción B" (local app against RDS with only `DB_PASSWORD`) working.
- **S7 Docs.** `orders-service/README.md` documents the new request/response fields, the Lambda contract (S6), the SNS topology (S3), the outbox (S5), the idempotency rules (S2) and the new env vars.

## Tasks

| ID | Specs | Route | Status | Commit |
|---|---|---|---|---|
| T1 | S1, S2 | delegated (writer "creation", worktree `feat/orders-creation`) — parallel-units trigger | done (34/34 green, parent re-run) | 14b2509 (on `feat/orders-creation`) |
| T2 | S3, S4, S5, S6 | delegated (writer "messaging", worktree `feat/orders-messaging`) — parallel-units trigger | done (41/41 green in worktree, parent re-run) | 0f720d7 (on `feat/orders-messaging`) |
| T3 | S7 + merge | inline (parent) | done (merged suite 63/63 green) | 812cd3b, a74bca3 (merges), README commit below |
| T5 | S8, S9, S10 | inline (parent; small, one understood change) | done (RED 1 failure → GREEN 65/65) | see commit after c7f6291 |
| T4 | all | independent verifier — Risk items 1 (schema: new columns + outbox table), 3 (PUT /guide contract), 4 (outbox relay concurrency), 5 (new S3 dependency) | pending | — |

## Log

- **L1 (2026-11-17, user):** "Pon a cada agente a realizar cada cosa, para que quede perfecto y cumpla con todo lo que dices" — refers to the Orders gap analysis: shipping type missing (RF-1), nobody publishes `GuiaLista` (RF-5), single SQS queue shared by Lambda and Notifications, event lost when SQS fails after save, order stuck in `PENDING_VALIDATION` on replay, presigned S3 URL that expires if stored.
- **L2 (2026-11-17, user decision):** shipping type = "Solo informativo".
- **L3:** baseline `./mvnw test` on `main` (94a1cf0) = green (exit 0). S1–S2 and S3–S6 touch overlapping files (`Order.java`, `OrderEntity`, web mapper), so each unit runs in its own worktree; the parent merges and owns `README.md`.
- **L4 (T1 done):** S1 + S2 implemented. RED observed (15 failures + 3 errors against stubs), GREEN 34/34. Adds nullable columns `shipping_type`, `rejection_reason`; new `DuplicateIdempotencyKeyException` (adapter translates the unique-constraint violation, use case re-reads and applies replay rules; fallback 409). Adapter reads are now `@Transactional(readOnly = true)` (fixed a pre-existing LazyInitializationException outside HTTP requests). Kept overloads `Order(5 args)` / `reconstruct(...)`; the merge with T2 must rename `guideUrl` → `guideKey` in the 12-arg `reconstruct` and the adapter mapping. Known limitation, out of scope: no optimistic locking (`@Version`), so a PENDING replay racing a confirm can lose an update.
- **L5 (T2 done):** S3-S6 implemented. RED was 18 failures and 2 errors against compiling skeletons; GREEN was 41/41.
  - SQS publisher and `sqs` dependency removed. New pieces: `SnsEventPublisherAdapter` (Jackson 3, attribute `eventType`), outbox (`outbox_events`, `TransactionPort`, `RelayOutboxEventsUseCase`, `OutboxRelayScheduler`), `GuideUrlSignerPort` + `S3GuideUrlSignerAdapter`, and `OrderView` returned by `GetOrderStatusInputPort.toView`.
  - Deviations: the relay skips later events of an order after one fails (keeps per-order order). The signer returns null (not 500) when the bucket or credentials are missing.
  - Known limits: no `SKIP LOCKED`, so several instances can double-publish (tolerated by at-least-once). Old GUIDE_READY rows have no `guide_key`.
- **L6 (T3 done):**
  - Merge conflicts in `Order.java`, `PostgresOrderRepositoryAdapter`, `FakeOrderRepository`: resolved by keeping both units, with `guideUrl` → `guideKey` in the 12-arg `reconstruct`.
  - `OrderWebMapperTest` adapted to `toResponse(OrderView)`. Merged suite 63/63 green.
  - README rewritten for S7.
- **L7 (2026-11-17, user):** "Quitar el stub WireMock (coveragestub) y apuntar al Coverage Service real. • Idempotencia: evitar duplicar pedidos en reintentos y evitar error InvalidOrderStateException si llega dos veces MarkGuideReady. • Configurar perfil application-aws.yaml para RDS/SQS reales. Esto ya esta hecho?". Status found:
  - The code already used the real Coverage URL, but `coverage-stub/` was still in the repo.
  - Create idempotency was already done (S2).
  - A repeated MarkGuideReady returned 409; the domain throws `IllegalStateException` (`InvalidOrderStateException` is unused).
  - The aws profile had RDS only.
  - Then the user said "haz todo lo que hace falta que te envie", which became S8-S10.
- **L8 (T5 done):**
  - RED: `guiaListaRepetidaConLaMismaClave…` failed with IllegalStateException; GREEN: 65/65.
  - `Order.markGuideReady` now returns `false` for a same-key replay, and the use case then skips the save and the outbox write.
  - The different-key test pins the 409 behavior. It also corrects the documented port 8080 → 8082 and local Postgres 5432 → 5434 (docker-compose.yml:10).
