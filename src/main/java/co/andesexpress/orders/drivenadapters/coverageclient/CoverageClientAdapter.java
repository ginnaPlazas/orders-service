package co.andesexpress.orders.drivenadapters.coverageclient;

import co.andesexpress.orders.domain.exception.CoverageServiceUnavailableException;
import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.Fare;
import co.andesexpress.orders.domain.model.Zone;
import co.andesexpress.orders.domain.port.out.CoverageClientPort;
import co.andesexpress.orders.drivenadapters.coverageclient.dto.CoverageRequestDto;
import co.andesexpress.orders.drivenadapters.coverageclient.dto.CoverageResponseDto;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class CoverageClientAdapter implements CoverageClientPort {

    private final WebClient webClient;

    public CoverageClientAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8081") // URL de Coverage, ajustar en application.properties luego
                .build();
    }

    @Override
    @Retry(name = "coverageService")
    public CoverageValidationResult validate(Address origin, Address destination, BigDecimal totalWeightKg) {
        CoverageRequestDto request = new CoverageRequestDto();
        request.origin = new CoverageRequestDto.OriginDestination(origin.getDepartmentId(), origin.getCityId());
        request.destination = new CoverageRequestDto.OriginDestination(destination.getDepartmentId(), destination.getCityId());
        request.totalWeightKg = totalWeightKg;

        try {
            CoverageResponseDto response = webClient.post()
                    .uri("/coverage/validate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CoverageResponseDto.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            if (response == null) {
                throw new CoverageServiceUnavailableException("Respuesta vacía de Coverage", null);
            }

            if (!response.valid) {
                return new CoverageValidationResult(false, null, null, response.message);
            }

            Zone zone = Zone.valueOf(response.zone);
            Fare fare = new Fare(response.fare.amount, response.fare.currency);
            return new CoverageValidationResult(true, zone, fare, null);

        } catch (WebClientResponseException.NotFound e) {
            // Coverage devolvió 404 en el body esperado, no debería pasar con el contrato actual,
            // pero por seguridad lo tratamos como destino inválido
            return new CoverageValidationResult(false, null, null, "Destino no encontrado");

        } catch (WebClientResponseException.ServiceUnavailable e) {
            throw new CoverageServiceUnavailableException("Coverage no disponible (503)", e);

        } catch (Exception e) {
            // timeout, conexión rechazada, etc. — HU-07
            throw new CoverageServiceUnavailableException("No se pudo validar cobertura: " + e.getMessage(), e);
        }
    }
}