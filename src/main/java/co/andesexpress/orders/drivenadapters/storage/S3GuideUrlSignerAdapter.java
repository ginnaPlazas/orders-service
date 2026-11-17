package co.andesexpress.orders.drivenadapters.storage;

import co.andesexpress.orders.domain.port.out.GuideUrlSignerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * Genera la URL GET prefirmada de la guía PDF en S3. Firmar es un cálculo local (no llama a AWS),
 * pero necesita credenciales: si faltan o el bucket no está configurado se registra el error y se
 * devuelve {@code null}, para que consultar el pedido siga funcionando.
 */
@Component
public class S3GuideUrlSignerAdapter implements GuideUrlSignerPort {

    private static final Logger log = LoggerFactory.getLogger(S3GuideUrlSignerAdapter.class);

    private final S3Presigner presigner;
    private final String bucket;
    private final Duration ttl;

    public S3GuideUrlSignerAdapter(S3Presigner presigner,
                                   @Value("${aws.s3.guides-bucket:}") String bucket,
                                   @Value("${aws.s3.guide-url-ttl:15m}") Duration ttl) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.ttl = ttl;
        if (bucket == null || bucket.isBlank()) {
            log.warn("aws.s3.guides-bucket (GUIDES_BUCKET) no está configurado: guideUrl será null");
        }
    }

    @Override
    public String presignedGetUrl(String guideKey) {
        if (bucket == null || bucket.isBlank()) {
            log.error("No se puede firmar la guía {}: bucket S3 no configurado (GUIDES_BUCKET)", guideKey);
            return null;
        }
        try {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(guideKey).build())
                    .build();
            return presigner.presignGetObject(request).url().toString();
        } catch (SdkException e) {
            log.error("No se pudo firmar la URL de la guía {} en el bucket {}: {}", guideKey, bucket, e.getMessage());
            return null;
        }
    }
}
