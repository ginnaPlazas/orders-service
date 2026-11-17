package co.andesexpress.orders.drivenadapters.postgresrepository;

import co.andesexpress.orders.domain.exception.DuplicateIdempotencyKeyException;
import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.Product;
import co.andesexpress.orders.domain.model.ShippingType;
import co.andesexpress.orders.drivenadapters.postgresrepository.entity.OrderEntity;
import co.andesexpress.orders.drivenadapters.postgresrepository.entity.ProductEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostgresOrderRepositoryAdapterTest {

    @Autowired
    PostgresOrderRepositoryAdapter adapter;

    @Autowired
    SpringDataOrderJpaRepository jpaRepository;

    private static List<Product> products() {
        return List.of(new Product("Caja", new BigDecimal("2.5")));
    }

    private static Order newOrder(String key, ShippingType shippingType) {
        return new Order(key, "cliente@andesexpress.com", new Address(5, 1), new Address(15, 2), products(), shippingType);
    }

    @Test
    void tipoDeEnvioYMotivoDeRechazo_seGuardanYSeLeen() {
        String key = UUID.randomUUID().toString();
        Order order = newOrder(key, ShippingType.EXPRESS);
        order.markRejected("El municipio no existe");

        adapter.save(order);
        Order leido = adapter.findByIdempotencyKey(key).orElseThrow();

        assertEquals(order.getId(), leido.getId());
        assertEquals(ShippingType.EXPRESS, leido.getShippingType());
        assertEquals(OrderStatus.REJECTED, leido.getStatus());
        assertEquals("El municipio no existe", leido.getRejectionReason());
        OrderEntity fila = jpaRepository.findById(order.getId()).orElseThrow();
        assertEquals("EXPRESS", fila.getShippingType());
        assertEquals("El municipio no existe", fila.getRejectionReason());
    }

    @Test
    void filaAntiguaSinTipoDeEnvio_seLeeComoStandard() {
        // Filas creadas antes de existir la columna: shippingType y rejectionReason quedan en NULL
        OrderEntity fila = new OrderEntity();
        fila.setId(UUID.randomUUID().toString());
        fila.setIdempotencyKey(UUID.randomUUID().toString());
        fila.setCustomerEmail("cliente@andesexpress.com");
        fila.setOriginDepartmentId(5);
        fila.setOriginCityId(1);
        fila.setDestinationDepartmentId(15);
        fila.setDestinationCityId(2);
        fila.setStatus("PENDING_VALIDATION");
        ProductEntity producto = new ProductEntity("Caja", new BigDecimal("2.5"));
        producto.setOrder(fila);
        fila.setProducts(List.of(producto));
        jpaRepository.save(fila);

        Order leido = adapter.findById(fila.getId()).orElseThrow();

        assertEquals(ShippingType.STANDARD, leido.getShippingType());
        assertNull(leido.getRejectionReason());
    }

    @Test
    void otraOrdenConLaMismaClave_lanzaDuplicateIdempotencyKeyYConservaLaPrimera() {
        String key = UUID.randomUUID().toString();
        Order primera = adapter.save(newOrder(key, ShippingType.STANDARD));

        DuplicateIdempotencyKeyException ex = assertThrows(DuplicateIdempotencyKeyException.class,
                () -> adapter.save(newOrder(key, ShippingType.EXPRESS)));

        assertEquals(key, ex.getIdempotencyKey());
        Order guardada = adapter.findByIdempotencyKey(key).orElseThrow();
        assertEquals(primera.getId(), guardada.getId());
        assertEquals(ShippingType.STANDARD, guardada.getShippingType());
    }
}
