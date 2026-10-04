package co.andesexpress.orders.entrypoints.web.dto;

import jakarta.validation.constraints.NotBlank;

public class GuideReadyRequest {
    @NotBlank(message = "La URL de la guía es obligatoria")
    public String guideUrl;
}