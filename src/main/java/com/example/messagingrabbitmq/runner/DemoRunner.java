package com.example.messagingrabbitmq.runner;

import java.util.concurrent.TimeUnit;

import com.example.messagingrabbitmq.consumer.MessageReceiver;
import com.example.messagingrabbitmq.producer.MessageProducer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The Spring guide's one-shot demo: send one message and wait for it to be received.
 * Runs only when neither the "producer" nor the "consumer" profile is active.
 */
@Component
@Profile("!producer & !consumer")
public class DemoRunner implements CommandLineRunner {

	private final MessageProducer producer;
	private final MessageReceiver receiver;

	public DemoRunner(MessageProducer producer, MessageReceiver receiver) {
		this.producer = producer;
		this.receiver = receiver;
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Sending message...");
		producer.send("Hello from RabbitMQ!");
		receiver.getLatch().await(10000, TimeUnit.MILLISECONDS);
	}

}
