package co.andesexpress.orders.entrypoints.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo que envía la Lambda al terminar el PDF: la clave del objeto en S3, nunca una URL. */
public class GuideReadyRequest {
    @NotBlank(message = "La clave de la guía en S3 (guideKey) es obligatoria")
    public String guideKey;
}
