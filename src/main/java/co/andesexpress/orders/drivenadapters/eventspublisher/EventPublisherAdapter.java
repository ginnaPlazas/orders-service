package co.andesexpress.orders.drivenadapters.eventspublisher;

import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(EventPublisherAdapter.class);

    private final SqsClient sqsClient;
    private final String queueUrl;

    public EventPublisherAdapter(SqsClient sqsClient,
                                  @org.springframework.beans.factory.annotation.Value("${aws.sqs.order-confirmed-queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publishOrderConfirmed(String orderId) {
        String messageBody = """
                {"eventType": "PedidoConfirmado", "orderId": "%s"}
                """.formatted(orderId);

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        sqsClient.sendMessage(request);
        log.info("Evento PedidoConfirmado publicado para orderId={}", orderId);
    }
}