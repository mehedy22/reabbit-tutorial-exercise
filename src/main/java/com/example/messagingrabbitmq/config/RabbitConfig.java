// STEP 1 (tutorial-one): Spring topology + listener disabled while we follow the plain Java client tutorial.
// Uncomment the whole file to restore it.

// package com.example.messagingrabbitmq.config;
//
// import com.example.messagingrabbitmq.constants.RabbitConstants;
// import com.example.messagingrabbitmq.consumer.MessageReceiver;
//
// import org.springframework.amqp.core.AcknowledgeMode;
// import org.springframework.amqp.core.Binding;
// import org.springframework.amqp.core.BindingBuilder;
// import org.springframework.amqp.core.DirectExchange;
// import org.springframework.amqp.core.Queue;
// import org.springframework.amqp.core.QueueBuilder;
// import org.springframework.amqp.core.TopicExchange;
// import org.springframework.amqp.rabbit.connection.ConnectionFactory;
// import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
// import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.context.annotation.Profile;
//
// /**
//  * Broker topology (exchange, queue, binding) and the listener that feeds the consumer.
//  */
// @Configuration
// public class RabbitConfig {
//
// 	@Bean
// 	Queue queue() {
// 		// Durable: RabbitMQ 4.x rejects transient non-exclusive queues (the guide's original `false`).
// 		// Rejected messages are re-routed to the DLX instead of being dropped.
// 		return QueueBuilder.durable(RabbitConstants.QUEUE)
// 				.deadLetterExchange(RabbitConstants.DLX)
// 				.deadLetterRoutingKey(RabbitConstants.DLQ_ROUTING_KEY)
// 				.build();
// 	}
//
// 	@Bean
// 	TopicExchange exchange() {
// 		return new TopicExchange(RabbitConstants.EXCHANGE);
// 	}
//
// 	@Bean
// 	Binding binding(Queue queue, TopicExchange exchange) {
// 		return BindingBuilder.bind(queue).to(exchange).with(RabbitConstants.BINDING_KEY);
// 	}
//
// 	@Bean
// 	DirectExchange deadLetterExchange() {
// 		return new DirectExchange(RabbitConstants.DLX);
// 	}
//
// 	@Bean
// 	Queue deadLetterQueue() {
// 		return QueueBuilder.durable(RabbitConstants.DLQ).build();
// 	}
//
// 	@Bean
// 	Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
// 		return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(RabbitConstants.DLQ_ROUTING_KEY);
// 	}
//
// 	// The producer profile only sends, so it gets no listener.
// 	@Bean
// 	@Profile("!producer")
// 	SimpleMessageListenerContainer container(ConnectionFactory connectionFactory,
// 			MessageListenerAdapter listenerAdapter) {
// 		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
// 		container.setQueueNames(RabbitConstants.QUEUE);
// 		container.setMessageListener(listenerAdapter);
// 		// Hand-built containers ignore spring.rabbitmq.listener.simple.*, so the policy is set here.
// 		container.setAcknowledgeMode(AcknowledgeMode.AUTO); // ack when the handler returns, reject when it throws
// 		container.setDefaultRequeueRejected(false); // a rejected message goes to the DLX, not back on the queue
// 		return container;
// 	}
//
// 	@Bean
// 	@Profile("!producer")
// 	MessageListenerAdapter listenerAdapter(MessageReceiver receiver) {
// 		return new MessageListenerAdapter(receiver, "receiveMessage");
// 	}
//
// }
