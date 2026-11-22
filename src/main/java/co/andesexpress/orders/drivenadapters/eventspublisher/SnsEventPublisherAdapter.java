package co.andesexpress.orders.drivenadapters.eventspublisher;

import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica el evento en un tópico SNS. El tópico reparte (fan-out) el mismo mensaje a varias colas SQS
 * suscritas: una para Notifications y otra para la Lambda que genera el PDF de la guía.
 * Se activa con events.publisher=sns (perfil aws). Las suscripciones deben tener
 * "raw message delivery" activado para que las colas reciban el JSON tal cual, sin el sobre de SNS.
 */
@Component
@ConditionalOnProperty(name = "events.publisher", havingValue = "sns")
public class SnsEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SnsEventPublisherAdapter.class);

    private final SnsClient snsClient;
    private final JsonMapper jsonMapper;
    private final String topicArn;

    public SnsEventPublisherAdapter(SnsClient snsClient,
                                    JsonMapper jsonMapper,
                                    @Value("${aws.sns.order-confirmed-topic-arn}") String topicArn) {
        this.snsClient = snsClient;
        this.jsonMapper = jsonMapper;
        this.topicArn = topicArn;
    }

    @Override
    public void publishOrderConfirmed(Order order) {
        String message = jsonMapper.writeValueAsString(OrderConfirmedEvent.from(order));

        snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(message)
                .build());
        log.info("Evento PedidoConfirmado publicado en SNS para orderId={}", order.getId());
    }
}
