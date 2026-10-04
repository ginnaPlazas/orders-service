# Orders Service — AndesExpress

Microservicio encargado del registro y ciclo de vida de los pedidos de AndesExpress. Construido con **arquitectura hexagonal** (puertos y adaptadores), separando completamente el dominio de negocio de los detalles técnicos (Spring, JPA, HTTP, AWS).

## Épicas y historias de usuario cubiertas

- **HU-01** — Registrar un pedido con uno o más productos
- **HU-02** — Rechazo de destino inválido (vía validación con Coverage)
- **HU-03** — Confirmar pedido y recibir guía
- **HU-04** — Evitar pedidos duplicados (idempotencia)
- **HU-07** — Tolerancia a fallas del servicio de Coverage (reintentos con Resilience4j)
- **HU-08** — Disparo de evento para generación asíncrona de guía
- **HU-09** — Guardar y exponer la URL de la guía
- **HU-12** — Consultar estado del pedido
- **HU-13** — Ver el recorrido (origen → destino) del pedido
- **HU-14** — Separación hexagonal en el servicio
- **HU-15** — Persistencia en base de datos gestionada (PostgreSQL)

## Arquitectura

```
co.andesexpress.orders
├── domain              → entidades, reglas de negocio puras, puertos (sin dependencias externas)
├── usecase              → casos de uso, orquestan el dominio a través de los puertos
├── entrypoints/web      → controller REST, DTOs, mappers
├── drivenadapters
│   ├── postgresrepository  → persistencia con JPA/Hibernate hacia PostgreSQL
│   ├── coverageclient      → cliente HTTP (WebClient) hacia el microservicio Coverage
│   └── eventspublisher     → publicación de eventos hacia SQS
└── config               → wiring de beans de Spring
```

El `domain` y `usecase` no tienen ninguna dependencia de Spring, JPA ni HTTP — son Java puro, testeables de forma aislada.

## Stack

- Java 21 + Spring Boot 4.1.1
- PostgreSQL (persistencia principal)
- H2 en memoria (solo para pruebas)
- Resilience4j (retry ante fallas de Coverage)
- AWS SDK v2 (SQS, para disparar el evento `PedidoConfirmado`)

## Cómo levantar el proyecto localmente

### 1. Base de datos (PostgreSQL vía Docker)

```bash
docker-compose up -d
```

Esto levanta Postgres en `localhost:5432`, base `orders_db`, usuario/contraseña `postgres`/`postgres`.

### 2. Correr la aplicación

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

El perfil `prod` conecta contra el Postgres de Docker. Sin ese perfil, la app no levanta (no hay datasource por defecto fuera de tests).

### 3. Correr las pruebas

```bash
mvn clean install
```

Las pruebas usan H2 en memoria automáticamente (`src/test/resources/application.properties`), no requieren Docker.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/orders` | Crea un pedido (valida idempotencia y cobertura) |
| `POST` | `/orders/{orderId}/confirm` | Confirma un pedido validado, dispara evento de guía |
| `GET` | `/orders/{orderId}` | Consulta el estado y recorrido del pedido |
| `PUT` | `/orders/{orderId}/guide` | Marca la guía como lista (invocado por la Lambda) |

### Ejemplo — crear un pedido

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-001",
    "origin": { "departmentId": 5, "cityId": 105001 },
    "destination": { "departmentId": 76, "cityId": 76001 },
    "products": [ { "name": "Caja", "weightKg": 10 } ]
  }'
```

> Nota: este endpoint depende de que el microservicio **Coverage** esté corriendo en `localhost:8081` (configurable en `application.properties` del adaptador `coverageclient`). Sin Coverage disponible, la respuesta será `503 Service Unavailable`.

## Manejo de errores

Centralizado en `GlobalExceptionHandler`:

| Excepción de dominio | Código HTTP |
|---|---|
| `DestinationRejectedException` | 400 |
| `OrderNotFoundException` | 404 |
| `CoverageServiceUnavailableException` | 503 |
| `IllegalStateException` (transición de estado inválida) | 409 |
| `IllegalArgumentException` (validación de dominio) | 400 |

## Pendientes conocidos

- Conectar contra una instancia RDS real en AWS (actualmente PostgreSQL local vía Docker)
- Crear la cola SQS real en AWS (actualmente la URL en `application.yaml` es un placeholder)
- Pruebas de integración de los adaptadores (`@DataJpaTest`, `@WebMvcTest`)
- Integración end-to-end una vez Coverage y la Lambda de generación de guía estén desplegados