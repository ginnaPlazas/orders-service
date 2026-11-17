package co.andesexpress.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Clientes AWS. Las credenciales se resuelven de forma perezosa (cadena por defecto del SDK),
 * así que construirlos no falla aunque AWS no esté disponible: el error aparece al usarlos
 * y lo manejan el relay del outbox (reintenta) y el firmador de guías (devuelve null).
 */
@Configuration
public class AwsConfig {

    @Bean(destroyMethod = "close")
    public SnsClient snsClient(@Value("${aws.region}") String region) {
        return SnsClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(@Value("${aws.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
