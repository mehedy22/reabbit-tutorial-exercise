package com.example.messagingrabbitmq.runner;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.example.messagingrabbitmq.producer.MessageProducer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Interactive producer: every line typed in the terminal is published to the exchange.
 * Run with the "producer" profile; type "exit" (or Ctrl+D) to quit.
 */
@Component
@Profile("producer")
public class ConsoleProducerRunner implements CommandLineRunner {

	private final MessageProducer producer;
	private final ConfigurableApplicationContext context;

	public ConsoleProducerRunner(MessageProducer producer, ConfigurableApplicationContext context) {
		this.producer = producer;
		this.context = context;
	}

	@Override
	public void run(String... args) throws Exception {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Producer ready. Type a message and press Enter (\"exit\" to quit).");
		String line;
		while ((line = in.readLine()) != null && !line.equals("exit")) {
			if (line.isBlank()) {
				continue;
			}
			producer.send(line);
			System.out.println("Sent <" + line + ">");
		}
		context.close();
	}

}
