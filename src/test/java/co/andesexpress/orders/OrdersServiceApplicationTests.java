package co.andesexpress.orders;

import co.andesexpress.orders.entrypoints.scheduler.OutboxRelayScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class OrdersServiceApplicationTests {

	@Autowired
	OutboxRelayScheduler outboxRelayScheduler;

	@Test
	void contextLoads() {
	}

	@Test
	void relayProgramado_estaActivoYNoFallaSinAws() {
		assertDoesNotThrow(outboxRelayScheduler::relay);
	}

}
