# Orders Service — AndesExpress

Microservicio encargado del registro y ciclo de vida de los pedidos de AndesExpress. Construido con **arquitectura hexagonal** (puertos y adaptadores): el dominio de negocio está aislado de Spring, JPA, HTTP y AWS.

## Historias de usuario cubiertas

- **HU-01** Registrar un pedido con uno o más productos
- **HU-02** Rechazo de destino inválido (validación con Coverage)
- **HU-03** Confirmar pedido y recibir guía
- **HU-04** Evitar pedidos duplicados (idempotencia)
- **HU-07** Tolerancia a fallas de Coverage (reintentos con Resilience4j)
- **HU-08** Evento para la generación asíncrona de la guía
- **HU-09** Guardar y exponer la URL de la guía
- **HU-12** Consultar estado del pedido
- **HU-13** Ver el recorrido (origen y destino)
- **HU-14** Separación hexagonal
- **HU-15** Persistencia en base de datos gestionada (PostgreSQL en AWS RDS)

## Arquitectura

```
co.andesexpress.orders
├── domain              → entidades, reglas de negocio y puertos (sin dependencias externas)
├── usecase             → casos de uso, orquestan el dominio a través de los puertos
├── entrypoints/web     → controller REST, DTOs, mappers
├── drivenadapters
│   ├── postgresrepository  → JPA/Hibernate hacia PostgreSQL
│   ├── coverageclient      → WebClient hacia el microservicio Coverage
│   └── eventspublisher     → publicación de eventos en SQS
└── config              → wiring de beans
```

`domain` y `usecase` son Java puro, sin Spring ni JPA, y se prueban de forma aislada.

## Stack

- Java 21, Spring Boot 4.1.1
- PostgreSQL: Docker en local, AWS RDS en la nube
- H2 en memoria solo para pruebas
- Resilience4j (reintentos hacia Coverage)
- AWS SDK v2 (SQS y SNS)

## Perfiles de configuración

| Perfil | Base de datos | Eventos | Uso |
|---|---|---|---|
| (ninguno) | H2 en memoria | SQS | Solo pruebas (`mvn clean install`) |
| `prod` | PostgreSQL local (Docker) | SQS | Desarrollo local |
| `aws` | PostgreSQL en AWS RDS | SNS (fan-out) | Despliegue en la nube |

### Variables de entorno

Todo lo que depende de la cuenta del Learner Lab llega por variables de entorno (no hay endpoints de otra cuenta escritos en el código).

| Variable | Perfil | Para qué |
|---|---|---|
| `SERVER_PORT` | todos | Puerto HTTP (por defecto `8082`) |
| `COVERAGE_BASE_URL` | todos | URL de Coverage. Por defecto `http://127.0.0.1:8081`; en `aws` es **obligatoria** (Coverage corre en otra instancia) |
| `ORDERS_DB_URL` | `aws` | `jdbc:postgresql://<endpoint-RDS>:5432/orders_db` (obligatoria) |
| `ORDERS_DB_USER` | `aws` | Usuario de la RDS (por defecto `postgres`) |
| `DB_PASSWORD` | `aws` | Contraseña de la RDS (obligatoria) |
| `EVENTS_PUBLISHER` | todos | `sqs` (directo a una cola) o `sns` (tópico con fan-out). Por defecto `sqs`; en `aws` por defecto `sns` |
| `ORDER_EVENTS_TOPIC_ARN` | `aws` con `sns` | ARN del tópico SNS donde se publica `PedidoConfirmado` |
| `ORDER_EVENTS_QUEUE_URL` | con `sqs` | URL de la cola SQS |
| `AWS_REGION` | todos | Región (por defecto `us-east-1`) |

Las credenciales de AWS no se configuran aquí: en una instancia EC2 del Learner Lab se toma el rol de la instancia (`LabInstanceProfile`); en local, las variables `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN`.

## Cómo levantar el proyecto

### Opción A: base local con Docker

```bash
docker-compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Levanta Postgres en `localhost:5432`, base `orders_db`, usuario y contraseña `postgres`.

### Opción B: base en AWS RDS

La contraseña no está en el repositorio. Pídela al equipo y pásala como variable de entorno. Si solo quieres probar contra la RDS desde tu PC, publica los eventos en una cola con `EVENTS_PUBLISHER=sqs`; con `sns` necesitas el ARN del tópico.

CMD:
```cmd
set ORDERS_DB_URL=jdbc:postgresql://<endpoint-de-la-RDS>:5432/orders_db
set DB_PASSWORD=la_contraseña
set COVERAGE_BASE_URL=http://127.0.0.1:8081
set EVENTS_PUBLISHER=sqs
set ORDER_EVENTS_QUEUE_URL=<url-de-la-cola>
mvn spring-boot:run -Dspring-boot.run.profiles=aws
```

PowerShell:
```powershell
$env:ORDERS_DB_URL="jdbc:postgresql://<endpoint-de-la-RDS>:5432/orders_db"
$env:DB_PASSWORD="la_contraseña"
$env:COVERAGE_BASE_URL="http://127.0.0.1:8081"
$env:EVENTS_PUBLISHER="sqs"
$env:ORDER_EVENTS_QUEUE_URL="<url-de-la-cola>"
mvn spring-boot:run "-Dspring-boot.run.profiles=aws"
```

En una instancia EC2 el despliegue usa `EVENTS_PUBLISHER=sns` (valor por defecto del perfil) con `ORDER_EVENTS_TOPIC_ARN` y `COVERAGE_BASE_URL` apuntando a la instancia de Coverage.

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
| `POST` | `/orders/{orderId}/confirm` | Confirma un pedido validado y publica el evento. Si el pedido ya estaba generando la guía, vuelve a publicar el evento (reintento seguro) |
| `GET` | `/orders/{orderId}` | Consulta estado, origen, destino, tarifa, tipo de envío y URL de la guía |
| `PUT` | `/orders/{orderId}/guide` | Marca la guía como lista (la invoca la Lambda). Es idempotente: una llamada repetida devuelve el pedido sin error y conserva la primera URL |

### Ejemplo: crear un pedido

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-001",
    "customerEmail": "cliente@correo.com",
    "origin": { "departmentId": 3, "cityId": 143 },
    "destination": { "departmentId": 7, "cityId": 217 },
    "shippingType": "STANDARD",
    "products": [ { "name": "Caja", "weightKg": 10 } ]
  }'
```

Los IDs de departamento y municipio son los de la **API Colombia**, no códigos DANE. `shippingType` es opcional (`STANDARD` por defecto, o `EXPRESS`) e informativo: la tarifa depende solo de la zona y del peso. Este endpoint necesita Coverage (por defecto en `localhost:8081`, o donde apunte `COVERAGE_BASE_URL`). Sin Coverage, responde `503`.

## Ciclo de vida del pedido

```
PENDING_VALIDATION → VALIDATED → CONFIRMED → GUIDE_GENERATING → GUIDE_READY
        ↓
    REJECTED
```

## Integración con otros servicios

**Coverage (REST):** `POST /coverage/validate`. Orders envía origen, destino (IDs de la API Colombia) y peso total, y espera `valid`, `zone` y `fare`, o `valid: false` con un mensaje si el destino no existe. Una falla técnica de Coverage debe responder `503`.

**Evento `PedidoConfirmado` (SNS/SQS):** al confirmar un pedido, Orders publica una "foto" completa del pedido, de modo que quien la consuma no tenga que volver a preguntarle nada a Orders:

```json
{
  "eventType": "PedidoConfirmado",
  "orderId": "abc-123",
  "customerEmail": "cliente@correo.com",
  "shippingType": "STANDARD",
  "origin": { "departmentId": 3, "cityId": 143 },
  "destination": { "departmentId": 7, "cityId": 217 },
  "products": [ { "name": "Caja", "weightKg": 10 } ],
  "totalWeightKg": 10,
  "zone": "NEIGHBORING_DEPARTMENT",
  "fare": { "amount": 15000, "currency": "COP" },
  "occurredAt": "2026-11-20T22:00:00Z"
}
```

- **En local** (`EVENTS_PUBLISHER=sqs`): va directo a una cola que lee Notifications.
- **En AWS** (`EVENTS_PUBLISHER=sns`): va a un tópico SNS que reparte el mismo mensaje a dos colas SQS: la de Notifications (avisa por correo) y la de la Lambda (genera el PDF de la guía). Las suscripciones deben tener *raw message delivery* activado para que cada cola reciba el JSON tal cual, sin el sobre de SNS.
- El JSON lo arma Jackson, así que nombres de producto con comillas o saltos de línea no rompen el mensaje.
- Los consumidores deben tolerar duplicados (las colas entregan "al menos una vez").

## Manejo de errores

Centralizado en `GlobalExceptionHandler`:

| Excepción | HTTP |
|---|---|
| `DestinationRejectedException` | 400 |
| `OrderNotFoundException` | 404 |
| `CoverageServiceUnavailableException` | 503 |
| `IllegalStateException` (transición de estado inválida) | 409 |
| `IllegalArgumentException` (validación de dominio) | 400 |

## Pendientes conocidos

- Crear en AWS el tópico SNS y las dos colas suscritas (ver la guía de despliegue)
- Integración end-to-end con Coverage, Notifications y la Lambda de generación de guía
- Pruebas de integración de los adaptadores (`@DataJpaTest`, `@WebMvcTest`)
- Nota de diseño: guardar el pedido y publicar el evento no son atómicos. Si la publicación falla, reconfirmar (`POST /orders/{id}/confirm`) vuelve a publicarlo; un patrón *outbox* lo resolvería del todo y queda fuera del alcance.