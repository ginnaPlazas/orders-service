package co.andesexpress.orders.drivenadapters.eventspublisher;

import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Publica los eventos del outbox en el tópico SNS de eventos de pedidos (fan-out a la cola de la
 * Lambda de guías, filtrada por {@code eventType = PedidoConfirmado}, y a la de Notifications).
 * El atributo {@code eventType} permite las políticas de filtro de las suscripciones.
 */
@Component
public class SnsEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SnsEventPublisherAdapter.class);
    static final String EVENT_TYPE_ATTRIBUTE = "eventType";

    private final SnsClient snsClient;
    private final JsonMapper jsonMapper;
    private final String topicArn;

    public SnsEventPublisherAdapter(SnsClient snsClient, JsonMapper jsonMapper,
                                    @Value("${aws.sns.order-events-topic-arn:}") String topicArn) {
        this.snsClient = snsClient;
        this.jsonMapper = jsonMapper;
        this.topicArn = topicArn;
        if (topicArn == null || topicArn.isBlank()) {
            log.warn("aws.sns.order-events-topic-arn (ORDER_EVENTS_TOPIC_ARN) no está configurado: "
                    + "los eventos quedarán pendientes en el outbox hasta configurarlo");
        }
    }

    @Override
    public void publish(OutboxEvent event) {
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException("Tópico SNS no configurado (ORDER_EVENTS_TOPIC_ARN)");
        }
        String eventType = event.getEventType().wireName();

        // Cuerpo construido con Jackson: el escape del correo lo hace la librería, no a mano.
        ObjectNode body = jsonMapper.createObjectNode()
                .put("eventType", eventType)
                .put("orderId", event.getOrderId())
                .put("customerEmail", event.getCustomerEmail());

        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(jsonMapper.writeValueAsString(body))
                .messageAttributes(Map.of(EVENT_TYPE_ATTRIBUTE, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(eventType)
                        .build()))
                .build();

        snsClient.publish(request);
        log.info("Evento {} enviado a SNS para orderId={}", eventType, event.getOrderId());
    }
}
