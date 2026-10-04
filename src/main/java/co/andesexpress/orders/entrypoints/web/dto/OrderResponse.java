package co.andesexpress.orders.entrypoints.web.dto;

import java.math.BigDecimal;

public class OrderResponse {
    public String id;
    public String status;
    public AddressDto origin;
    public AddressDto destination;
    public String zone;
    public FareDto fare;
    public String guideUrl;

    public static class AddressDto {
        public int departmentId;
        public int cityId;
    }

    public static class FareDto {
        public BigDecimal amount;
        public String currency;
    }
}