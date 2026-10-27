package com.example.messagingrabbitmq.constants;

/**
 * Broker names shared by the producer and the consumer, kept in one place.
 */
public final class RabbitConstants {

	public static final String EXCHANGE = "spring-boot-exchange";

	public static final String QUEUE = "spring-boot";

	/** Pattern the queue is bound with: any routing key starting with "foo.bar.". */
	public static final String BINDING_KEY = "foo.bar.#";

	/** Routing key the producer publishes with; matches {@link #BINDING_KEY}. */
	public static final String ROUTING_KEY = "foo.bar.baz";

	/** Dead-letter exchange: receives messages the consumer rejected. */
	public static final String DLX = "spring-boot-dlx";

	/** Dead-letter queue: parks rejected messages for inspection or replay. */
	public static final String DLQ = "spring-boot.dlq";

	/** Routing key rejected messages are re-published with to the DLX. */
	public static final String DLQ_ROUTING_KEY = "spring-boot.dead";

	private RabbitConstants() {
	}

}
