package co.andesexpress.orders.domain.model;

import java.util.Locale;

/**
 * Tipo de servicio del envío (requisito 1 del enunciado: "tipo de envío").
 * Es informativo: la tarifa solo depende de la zona y del peso.
 */
public enum ShippingType {
    STANDARD,
    EXPRESS;

    /** Interpreta el texto que llega por la API; vacío o nulo equivale a STANDARD. */
    public static ShippingType fromText(String text) {
        if (text == null || text.isBlank()) {
            return STANDARD;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de envío inválido: " + text + " (use STANDARD o EXPRESS)");
        }
    }
}
