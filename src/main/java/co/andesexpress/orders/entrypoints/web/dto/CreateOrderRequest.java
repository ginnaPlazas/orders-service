package co.andesexpress.orders.entrypoints.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public class CreateOrderRequest {

    @NotBlank(message = "La clave de idempotencia es obligatoria")
    public String idempotencyKey;

    @NotNull(message = "El origen es obligatorio")
    @Valid
    public AddressDto origin;

    @NotNull(message = "El destino es obligatorio")
    @Valid
    public AddressDto destination;

    @NotEmpty(message = "El pedido debe tener al menos un producto")
    @Valid
    public List<ProductDto> products;

    public static class AddressDto {
        @Positive(message = "El id del departamento debe ser positivo")
        public int departmentId;

        @Positive(message = "El id de la ciudad debe ser positivo")
        public int cityId;
    }

    public static class ProductDto {
        @NotBlank(message = "El nombre del producto es obligatorio")
        public String name;

        @NotNull(message = "El peso es obligatorio")
        @Positive(message = "El peso debe ser mayor que cero")
        public BigDecimal weightKg;
    }
}