package co.andesexpress.orders.entrypoints.web.dto;

import java.math.BigDecimal;
import java.util.List;

public class CreateOrderRequest {
    public String idempotencyKey;
    public AddressDto origin;
    public AddressDto destination;
    public List<ProductDto> products;

    public static class AddressDto {
        public int departmentId;
        public int cityId;
    }

    public static class ProductDto {
        public String name;
        public BigDecimal weightKg;
    }
}