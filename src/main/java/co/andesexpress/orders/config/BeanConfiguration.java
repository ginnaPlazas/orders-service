package co.andesexpress.orders.config;

import co.andesexpress.orders.domain.port.in.ConfirmOrderInputPort;
import co.andesexpress.orders.domain.port.in.CreateOrderInputPort;
import co.andesexpress.orders.domain.port.in.GetOrderStatusInputPort;
import co.andesexpress.orders.domain.port.in.MarkGuideReadyInputPort;
import co.andesexpress.orders.domain.port.in.RelayOutboxEventsInputPort;
import co.andesexpress.orders.domain.port.out.CoverageClientPort;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import co.andesexpress.orders.domain.port.out.GuideUrlSignerPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;
import co.andesexpress.orders.domain.port.out.TransactionPort;
import co.andesexpress.orders.usecase.ConfirmOrderUseCase;
import co.andesexpress.orders.usecase.CreateOrderUseCase;
import co.andesexpress.orders.usecase.GetOrderStatusUseCase;
import co.andesexpress.orders.usecase.MarkGuideReadyUseCase;
import co.andesexpress.orders.usecase.RelayOutboxEventsUseCase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BeanConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CreateOrderInputPort createOrderInputPort(OrderRepositoryPort orderRepository,
                                                       CoverageClientPort coverageClient) {
        return new CreateOrderUseCase(orderRepository, coverageClient);
    }

    @Bean
    public ConfirmOrderInputPort confirmOrderInputPort(OrderRepositoryPort orderRepository,
                                                         OutboxRepositoryPort outbox,
                                                         TransactionPort transaction,
                                                         Clock clock) {
        return new ConfirmOrderUseCase(orderRepository, outbox, transaction, clock);
    }

    @Bean
    public GetOrderStatusInputPort getOrderStatusInputPort(OrderRepositoryPort orderRepository,
                                                           GuideUrlSignerPort guideUrlSigner) {
        return new GetOrderStatusUseCase(orderRepository, guideUrlSigner);
    }

    @Bean
    public MarkGuideReadyInputPort markGuideReadyInputPort(OrderRepositoryPort orderRepository,
                                                           OutboxRepositoryPort outbox,
                                                           TransactionPort transaction,
                                                           Clock clock) {
        return new MarkGuideReadyUseCase(orderRepository, outbox, transaction, clock);
    }

    @Bean
    public RelayOutboxEventsInputPort relayOutboxEventsInputPort(OutboxRepositoryPort outbox,
                                                                 EventPublisherPort eventPublisher,
                                                                 Clock clock,
                                                                 @Value("${orders.outbox.relay.batch-size:50}") int batchSize) {
        return new RelayOutboxEventsUseCase(outbox, eventPublisher, clock, batchSize);
    }
}
