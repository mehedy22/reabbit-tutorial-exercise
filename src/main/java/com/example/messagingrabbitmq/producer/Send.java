package com.example.messagingrabbitmq.producer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Send {

    private static final String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5673); // our broker's host port (see compose.yaml); the tutorial uses the default 5672

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            Map<String, Object> args = Map.of("x-queue-type", "quorum");
            channel.queueDeclare(QUEUE_NAME, true, false, false, args);
            String message = "Hello World!,Guyes";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }

}