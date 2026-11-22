package co.andesexpress.orders.drivenadapters.eventspublisher;

import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica el evento directamente en una cola SQS. Es el modo por defecto (desarrollo local:
 * Orders -> una cola -> Notifications). En AWS se usa {@link SnsEventPublisherAdapter}.
 */
@Component
@ConditionalOnProperty(name = "events.publisher", havingValue = "sqs", matchIfMissing = true)
public class SqsEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SqsEventPublisherAdapter.class);

    private final SqsClient sqsClient;
    private final JsonMapper jsonMapper;
    private final String queueUrl;

    public SqsEventPublisherAdapter(SqsClient sqsClient,
                                    JsonMapper jsonMapper,
                                    @Value("${aws.sqs.order-confirmed-queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.jsonMapper = jsonMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publishOrderConfirmed(Order order) {
        String messageBody = jsonMapper.writeValueAsString(OrderConfirmedEvent.from(order));

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build());
        log.info("Evento PedidoConfirmado publicado en SQS para orderId={}", order.getId());
    }
}
