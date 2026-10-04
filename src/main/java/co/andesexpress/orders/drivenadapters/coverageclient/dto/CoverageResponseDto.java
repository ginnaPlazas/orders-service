package co.andesexpress.orders.drivenadapters.coverageclient.dto;

import java.math.BigDecimal;

public class CoverageResponseDto {
    public boolean valid;
    public String zone;
    public Fare fare;
    public String reason;
    public String message;

    public static class Fare {
        public BigDecimal amount;
        public String currency;
    }
}