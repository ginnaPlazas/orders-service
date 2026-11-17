# Orders Service — AndesExpress

Microservicio encargado del registro y ciclo de vida de los pedidos de AndesExpress. Construido con **arquitectura hexagonal** (puertos y adaptadores): el dominio de negocio está aislado de Spring, JPA, HTTP y AWS.

## Historias de usuario cubiertas

- **HU-01** Registrar un pedido con uno o más productos y tipo de envío
- **HU-02** Rechazo de destino inválido (validación con Coverage)
- **HU-03** Confirmar pedido y recibir guía
- **HU-04** Evitar pedidos duplicados (idempotencia)
- **HU-07** Tolerancia a fallas de Coverage (reintentos con Resilience4j)
- **HU-08** Evento para la generación asíncrona de la guía (outbox + SNS, al menos una vez)
- **HU-09** Guardar la guía en S3 y exponer su URL prefirmada
- **HU-12** Consultar estado del pedido
- **HU-13** Ver el recorrido (origen y destino)
- **HU-14** Separación hexagonal
- **HU-15** Persistencia en base de datos gestionada (PostgreSQL en AWS RDS)

## Arquitectura

```
co.andesexpress.orders
├── domain                 → entidades, reglas de negocio y puertos (sin dependencias externas)
├── usecase                → casos de uso, orquestan el dominio a través de los puertos
├── entrypoints
│   ├── web                → controller REST, DTOs, mappers
│   └── scheduler          → dispara el relay del outbox
├── drivenadapters
│   ├── postgresrepository → JPA/Hibernate hacia PostgreSQL (pedidos)
│   ├── outbox             → tabla outbox_events (eventos pendientes de publicar)
│   ├── transaction        → transacción de Spring detrás de TransactionPort
│   ├── coverageclient     → WebClient hacia el microservicio Coverage
│   ├── eventspublisher    → publicación de eventos en SNS
│   └── storage            → firma de URLs de S3 para las guías
└── config                 → wiring de beans
```

`domain` y `usecase` son Java puro, sin Spring ni JPA, y se prueban de forma aislada con fakes.

## Stack

- Java 21, Spring Boot 4.1.1
- PostgreSQL: Docker en local, AWS RDS en la nube
- H2 en memoria solo para pruebas
- Resilience4j (reintentos hacia Coverage)
- AWS SDK v2 (SNS para eventos, S3 para firmar las URLs de las guías)

## Perfiles de configuración

| Perfil | Base de datos | Uso |
|---|---|---|
| (ninguno) | H2 en memoria | Solo pruebas (`mvn clean install`) |
| `prod` | PostgreSQL local (Docker) | Desarrollo local |
| `aws` | PostgreSQL en AWS RDS | Base en la nube |

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_PASSWORD` | — | Contraseña de RDS (perfil `aws`) |
| `COVERAGE_BASE_URL` | `http://127.0.0.1:8081` | URL del Coverage Service real |
| `AWS_REGION` | `us-east-1` | Región de SNS y S3 (perfil `aws`) |
| `ORDER_EVENTS_TOPIC_ARN` | vacío | ARN del tópico SNS de eventos de pedidos. Si está vacío, los eventos quedan pendientes en el outbox |
| `GUIDES_BUCKET` | vacío | Bucket S3 donde la Lambda guarda los PDF. Si está vacío, `guideUrl` sale `null` |
| `GUIDE_URL_TTL` | `15m` | Vigencia de la URL prefirmada de la guía |
| `OUTBOX_RELAY_ENABLED` | `true` | Activa el relay del outbox |
| `OUTBOX_RELAY_INTERVAL_MS` | `5000` | Cada cuánto corre el relay |

Las credenciales de AWS (Learner Lab) se toman de la cadena por defecto del SDK (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`). Nunca se versionan.

## Cómo levantar el proyecto

### Opción A: base local con Docker

```bash
docker-compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Levanta Postgres en `localhost:5434`, base `orders_db`, usuario y contraseña `postgres`.

### Opción B: base en AWS RDS

La contraseña no está en el repositorio. Pídela al equipo y pásala como variable de entorno.

CMD:
```cmd
set DB_PASSWORD=la_contraseña
mvn spring-boot:run -Dspring-boot.run.profiles=aws
```

PowerShell:
```powershell
$env:DB_PASSWORD="la_contraseña"
mvn spring-boot:run "-Dspring-boot.run.profiles=aws"
```

Requisitos para conectarse a RDS:
- La instancia `orders-db` debe estar en estado "Disponible" (el laboratorio puede detenerla al terminar la sesión).
- El grupo de seguridad `orders-db-sg` debe permitir el puerto 5432 desde la IP de quien se conecta. Si cambias de red, hay que actualizar esa regla.

### Pruebas

```bash
mvn clean install
```

Usan H2 en memoria, no necesitan Docker ni AWS.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/orders` | Crea un pedido (idempotencia y validación de cobertura) |
| `POST` | `/orders/{orderId}/confirm` | Confirma un pedido validado y deja el evento `PedidoConfirmado` en el outbox |
| `GET` | `/orders/{orderId}` | Consulta estado, origen, destino, tipo de envío y URL de la guía |
| `PUT` | `/orders/{orderId}/guide` | Registra la guía generada (la invoca la Lambda) y deja el evento `GuiaLista` en el outbox |

### Ejemplo: crear un pedido

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-001",
    "customerEmail": "cliente@correo.com",
    "origin": { "departmentId": 3, "cityId": 143 },
    "destination": { "departmentId": 7, "cityId": 217 },
    "shippingType": "EXPRESS",
    "products": [ { "name": "Caja", "weightKg": 10 } ]
  }'
```

- Los IDs de departamento y municipio son los de la **API Colombia**, no códigos DANE.
- `shippingType` es opcional: `STANDARD` (por defecto) o `EXPRESS`. Un valor distinto responde `400`. Es informativo: **no cambia la tarifa** y no se envía a Coverage.
- Este endpoint llama al **Coverage Service real** (`COVERAGE_BASE_URL`, por defecto `localhost:8081`). No hay stub: sin Coverage, responde `503`.

La respuesta incluye `id`, `status`, `origin`, `destination`, `shippingType`, `zone`, `fare` y `guideUrl` (`null` hasta que la guía esté lista).

### Idempotencia (`idempotencyKey`)

Reenviar la misma solicitud nunca crea un pedido duplicado. Si la clave ya existe:

| Estado del pedido existente | Respuesta |
|---|---|
| `PENDING_VALIDATION` (Coverage falló antes) | Se valida otra vez contra Coverage: `201` si queda `VALIDATED`, `400` si queda `REJECTED`, `503` si Coverage sigue caído (el pedido sigue en `PENDING_VALIDATION`) |
| `REJECTED` | `400` con el mismo mensaje de rechazo original |
| Cualquier otro | `201` con el pedido tal cual está |

Si dos solicitudes con la misma clave llegan a la vez, la restricción `UNIQUE` de la base de datos deja pasar solo una. La otra lee el pedido guardado y aplica las mismas reglas, sin `500`.

## Ciclo de vida del pedido

```
PENDING_VALIDATION → VALIDATED → CONFIRMED → GUIDE_GENERATING → GUIDE_READY
        ↓
    REJECTED
```

## Integración con otros servicios

**Coverage (REST):** `POST /coverage/validate`. Orders envía origen, destino (IDs de la API Colombia) y peso total, y espera `valid`, `zone` y `fare`, o `valid: false` con un mensaje si el destino no existe. Una falla técnica de Coverage debe responder `503`.

### Eventos: outbox transaccional + SNS

Orders no publica directamente al confirmar. Así un fallo de AWS no deja el pedido en un estado sin evento:

1. `confirm` y `PUT /guide` guardan el cambio de estado **y** el evento en la tabla `outbox_events` dentro de **una misma transacción**.
2. Un relay programado (`OUTBOX_RELAY_INTERVAL_MS`) publica los eventos pendientes en SNS, en orden de creación, y solo los marca como publicados cuando SNS los aceptó.
3. Si SNS falla, el evento queda pendiente: se guardan el número de intentos y el último error, y se reintenta en la siguiente ejecución. `confirm` responde `200` aunque SNS esté caído.

La entrega es **al menos una vez**, así que un evento puede llegar duplicado. Los consumidores deben ser idempotentes: Notifications deduplica por `(orderId, type)`.

Mensajes publicados (atributo de mensaje SNS `eventType`, tipo `String`):

```json
{"eventType": "PedidoConfirmado", "orderId": "abc-123", "customerEmail": "cliente@correo.com"}
{"eventType": "GuiaLista", "orderId": "abc-123", "customerEmail": "cliente@correo.com"}
```

### Topología SNS → SQS (se crea en AWS, no la crea el código)

Una sola cola SQS no sirve, porque cada mensaje lo recibe un único consumidor. Hay que hacer *fan-out*:

```
Orders ──► SNS: order-events
              ├──► SQS: guide-generation-queue   (filtro: eventType = PedidoConfirmado) ──► Lambda (PDF)
              └──► SQS: notifications-queue      (sin filtro, Raw Message Delivery = true) ──► Notifications
```

- **Lambda:** suscripción con *filter policy* `{"eventType": ["PedidoConfirmado"]}`.
- **Notifications:** suscripción con **Raw Message Delivery** activado. Así el cuerpo del mensaje SQS es el JSON de arriba, sin el sobre de SNS. Notifications lee esa cola con `aws.sqs.order-confirmed-queue-url`.
- Cada cola SQS necesita una *access policy* que permita `sqs:SendMessage` desde el ARN del tópico.

### Contrato con la Lambda de guías (S3)

Cuando la Lambda termina de subir el PDF a S3, llama:

```bash
curl -X PUT http://localhost:8082/orders/{orderId}/guide \
  -H "Content-Type: application/json" \
  -d '{ "guideKey": "guias/abc-123.pdf" }'
```

- `guideKey` es la **clave del objeto en S3** dentro de `GUIDES_BUCKET`, no una URL. Si viene vacía, responde `400`.
- El pedido debe estar en `GUIDE_GENERATING`, si no responde `409`.
- **Idempotente:** si la llamada llega repetida con la **misma** `guideKey` sobre un pedido ya en `GUIDE_READY`, responde `200` con el pedido sin cambios y **no** emite otro `GuiaLista`. Con una `guideKey` **distinta** responde `409`, porque el pedido ya tiene otra guía registrada.
- Orders guarda solo la clave. Cada respuesta de un pedido en `GUIDE_READY` incluye `guideUrl` como **URL prefirmada** generada en ese momento, con vigencia `GUIDE_URL_TTL`. Por eso nunca se guarda una URL que después expire.

## Manejo de errores

Centralizado en `GlobalExceptionHandler`:

| Excepción | HTTP |
|---|---|
| `DestinationRejectedException` | 400 |
| `OrderNotFoundException` | 404 |
| `CoverageServiceUnavailableException` | 503 |
| `IllegalStateException` (transición de estado inválida) | 409 |
| `DuplicateIdempotencyKeyException` (respaldo, normalmente no se alcanza) | 409 |
| `IllegalArgumentException` (validación de dominio) | 400 |

Las validaciones de Bean Validation y el JSON mal formado responden `400` con el formato por defecto de Spring.

## Pendientes conocidos

- Crear en AWS el tópico SNS, las dos colas SQS y sus suscripciones (ver topología)
- La Lambda de generación de guía todavía no existe
- Sin bloqueo optimista (`@Version`): dos transiciones concurrentes sobre el mismo pedido pueden pisarse. Con varias instancias de Orders, el relay puede publicar un evento dos veces (lo tolera la entrega al menos una vez)
- Los pedidos que ya estaban en `GUIDE_READY` antes de este cambio guardaban una URL y no una clave, así que responden `guideUrl: null`
