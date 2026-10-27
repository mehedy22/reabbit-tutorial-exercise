package com.example.messagingrabbitmq.producer;

import com.example.messagingrabbitmq.constants.RabbitConstants;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes messages to the exchange. The only class that talks to {@link RabbitTemplate}.
 */
@Service
public class MessageProducer {

	private final RabbitTemplate rabbitTemplate;

	public MessageProducer(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void send(String message) {
		rabbitTemplate.convertAndSend(RabbitConstants.EXCHANGE, RabbitConstants.ROUTING_KEY, message);
	}

}
