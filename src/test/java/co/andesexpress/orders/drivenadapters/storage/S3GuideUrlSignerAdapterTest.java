package co.andesexpress.orders.drivenadapters.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class S3GuideUrlSignerAdapterTest {

    // Firmar es un cálculo local: no hay red ni credenciales reales involucradas.
    final S3Presigner presigner = S3Presigner.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("AKIAEXAMPLEEXAMPLE00", "secret/example/key")))
            .build();

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    void generaUrlGetPrefirmadaDelBucketYLaClaveConElTtlConfigurado() {
        S3GuideUrlSignerAdapter adapter = new S3GuideUrlSignerAdapter(presigner, "andes-guias", Duration.ofMinutes(15));

        String url = adapter.presignedGetUrl("guias/order-1.pdf");

        URI uri = URI.create(url);
        assertEquals("https", uri.getScheme());
        assertTrue(uri.getHost().startsWith("andes-guias."), uri.getHost());
        assertEquals("/guias/order-1.pdf", uri.getPath());
        assertTrue(uri.getQuery().contains("X-Amz-Expires=900"), uri.getQuery());
        assertTrue(uri.getQuery().contains("X-Amz-Signature="), uri.getQuery());
        assertTrue(uri.getQuery().contains("X-Amz-Credential=AKIAEXAMPLEEXAMPLE00"), uri.getQuery());
    }

    @Test
    void sinBucketConfigurado_devuelveNull() {
        S3GuideUrlSignerAdapter adapter = new S3GuideUrlSignerAdapter(presigner, "", Duration.ofMinutes(15));

        assertNull(adapter.presignedGetUrl("guias/order-1.pdf"));
    }
}
