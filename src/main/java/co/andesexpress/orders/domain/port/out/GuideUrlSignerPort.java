package co.andesexpress.orders.domain.port.out;

public interface GuideUrlSignerPort {
    /** URL GET prefirmada y temporal para la guía guardada bajo {@code guideKey}, o {@code null} si no se pudo firmar. */
    String presignedGetUrl(String guideKey);
}
