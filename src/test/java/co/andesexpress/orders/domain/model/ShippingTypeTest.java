package co.andesexpress.orders.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShippingTypeTest {

    @Test
    void nuloOVacio_equivaleAEstandar() {
        assertEquals(ShippingType.STANDARD, ShippingType.fromText(null));
        assertEquals(ShippingType.STANDARD, ShippingType.fromText(""));
        assertEquals(ShippingType.STANDARD, ShippingType.fromText("   "));
    }

    @Test
    void ignoraMayusculasYEspacios() {
        assertEquals(ShippingType.EXPRESS, ShippingType.fromText(" express "));
        assertEquals(ShippingType.STANDARD, ShippingType.fromText("Standard"));
    }

    @Test
    void valorDesconocido_lanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ShippingType.fromText("rapido"));
        assertTrue(ex.getMessage().contains("rapido"));
    }
}
