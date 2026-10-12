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
- AWS SDK v2 (SQS)

## Perfiles de configuración

| Perfil | Base de datos | Uso |
|---|---|---|
| (ninguno) | H2 en memoria | Solo pruebas (`mvn clean install`) |
| `prod` | PostgreSQL local (Docker) | Desarrollo local |
| `aws` | PostgreSQL en AWS RDS | Base en la nube |

## Cómo levantar el proyecto

### Opción A: base local con Docker

```bash
docker-compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Levanta Postgres en `localhost:5432`, base `orders_db`, usuario y contraseña `postgres`.

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
| `POST` | `/orders/{orderId}/confirm` | Confirma un pedido validado y publica el evento |
| `GET` | `/orders/{orderId}` | Consulta estado, origen y destino |
| `PUT` | `/orders/{orderId}/guide` | Marca la guía como lista (la invoca la Lambda) |

### Ejemplo: crear un pedido

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-001",
    "customerEmail": "cliente@correo.com",
    "origin": { "departmentId": 3, "cityId": 143 },
    "destination": { "departmentId": 7, "cityId": 217 },
    "products": [ { "name": "Caja", "weightKg": 10 } ]
  }'
```

Los IDs de departamento y municipio son los de la **API Colombia**, no códigos DANE. Este endpoint necesita Coverage corriendo en `localhost:8081`. Sin Coverage, responde `503`.

## Ciclo de vida del pedido

```
PENDING_VALIDATION → VALIDATED → CONFIRMED → GUIDE_GENERATING → GUIDE_READY
        ↓
    REJECTED
```

## Integración con otros servicios

**Coverage (REST):** `POST /coverage/validate`. Orders envía origen, destino (IDs de la API Colombia) y peso total, y espera `valid`, `zone` y `fare`, o `valid: false` con un mensaje si el destino no existe. Una falla técnica de Coverage debe responder `503`.

**Notifications (SQS):** al confirmar un pedido, Orders publica:

```json
{"eventType": "PedidoConfirmado", "orderId": "abc-123", "customerEmail": "cliente@correo.com"}
```

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

- Crear la cola SQS real en AWS (la URL en `application.yaml` es un placeholder)
- Integración end-to-end con Coverage, Notifications y la Lambda de generación de guía
- Pruebas de integración de los adaptadores (`@DataJpaTest`, `@WebMvcTest`)