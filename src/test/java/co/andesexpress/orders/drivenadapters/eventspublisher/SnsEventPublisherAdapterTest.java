package co.andesexpress.orders.drivenadapters.eventspublisher;

import co.andesexpress.orders.domain.model.EventType;
import co.andesexpress.orders.domain.model.OutboxEvent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnsEventPublisherAdapterTest {

    static final String TOPIC = "arn:aws:sns:us-east-1:000000000000:order-events";

    /** SnsClient falso: guarda las peticiones y opcionalmente falla. */
    static class CapturingSnsClient implements SnsClient {
        final List<PublishRequest> requests = new ArrayList<>();
        RuntimeException failure;

        @Override
        public PublishResponse publish(PublishRequest request) {
            if (failure != null) {
                throw failure;
            }
            requests.add(request);
            return PublishResponse.builder().messageId("msg-1").build();
        }

        @Override
        public String serviceName() {
            return "sns";
        }

        @Override
        public void close() {
        }
    }

    final JsonMapper jsonMapper = JsonMapper.builder().build();
    final CapturingSnsClient sns = new CapturingSnsClient();

    static OutboxEvent event(EventType type, String email) {
        return OutboxEvent.reconstruct("e1", type, "order-1", email, Instant.parse("2026-11-17T09:00:00Z"),
                null, 0, null);
    }

    @Test
    void pedidoConfirmado_publicaAlTopicoConCuerpoYAtributoEventType() {
        new SnsEventPublisherAdapter(sns, jsonMapper, TOPIC)
                .publish(event(EventType.ORDER_CONFIRMED, "cliente@andesexpress.com"));

        assertEquals(1, sns.requests.size());
        PublishRequest request = sns.requests.get(0);
        assertEquals(TOPIC, request.topicArn());
        assertEquals("{\"eventType\":\"PedidoConfirmado\",\"orderId\":\"order-1\",\"customerEmail\":\"cliente@andesexpress.com\"}",
                request.message());
        assertEquals(Map.of("eventType", MessageAttributeValue.builder().dataType("String").stringValue("PedidoConfirmado").build()),
                request.messageAttributes());
    }

    @Test
    void guiaLista_escapaElCorreoConJackson() {
        String trickyEmail = "o\"brien\\x\n@andes.com";

        new SnsEventPublisherAdapter(sns, jsonMapper, TOPIC).publish(event(EventType.GUIDE_READY, trickyEmail));

        PublishRequest request = sns.requests.get(0);
        assertEquals("{\"eventType\":\"GuiaLista\",\"orderId\":\"order-1\",\"customerEmail\":\"o\\\"brien\\\\x\\n@andes.com\"}",
                request.message());
        JsonNode body = jsonMapper.readTree(request.message());
        assertEquals("GuiaLista", body.get("eventType").asString());
        assertEquals("order-1", body.get("orderId").asString());
        assertEquals(trickyEmail, body.get("customerEmail").asString());
        assertEquals(3, body.size());
        assertEquals("GuiaLista", request.messageAttributes().get("eventType").stringValue());
        assertEquals("String", request.messageAttributes().get("eventType").dataType());
    }

    @Test
    void siSnsFalla_laExcepcionLlegaAlRelay() {
        sns.failure = SdkClientException.create("Unable to load credentials");
        SnsEventPublisherAdapter adapter = new SnsEventPublisherAdapter(sns, jsonMapper, TOPIC);

        assertThrows(SdkClientException.class,
                () -> adapter.publish(event(EventType.ORDER_CONFIRMED, "cliente@andesexpress.com")));
    }

    @Test
    void sinTopicoConfigurado_fallaSinLlamarASns() {
        SnsEventPublisherAdapter adapter = new SnsEventPublisherAdapter(sns, jsonMapper, " ");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> adapter.publish(event(EventType.ORDER_CONFIRMED, "cliente@andesexpress.com")));
        assertTrue(e.getMessage().contains("ORDER_EVENTS_TOPIC_ARN"), e.getMessage());
        assertTrue(sns.requests.isEmpty());
    }
}
