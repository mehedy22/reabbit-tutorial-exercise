# Tutorial One: "Hello World" with the Plain Java Client

This guide walks through the official RabbitMQ tutorial
[“Hello World” (Java client)](https://www.rabbitmq.com/tutorials/tutorial-one-java), adapted to
this project. It builds two small programs, a sender and a receiver, one piece at a time, and
runs each piece against the broker so you can see what it does.

Spring is **not** involved here. `Send` and `Recv` are plain Java classes with their own `main`
method. They use the RabbitMQ Java client (`amqp-client`) directly, which is already on the
classpath through `spring-boot-starter-amqp`. Spring never picks these classes up.

## How it works

```
Send (producer)                 RabbitMQ (Docker)                     Recv (consumer)
---------------                 -----------------                     ---------------
basicPublish("", "hello") ──▶  default exchange ""
                                  │ routes by queue name:
                                  │ routing key "hello" → queue "hello"
                                  ▼
                               queue "hello" (quorum) ──push──▶  DeliverCallback prints
                                                                  " [x] Received '...'"
```

- The **producer** never writes into a queue directly. It publishes to an **exchange**, and the
  exchange routes the message to a queue.
- The **default exchange** (its name is the empty string `""`) delivers a message to the queue
  whose name equals the routing key.
- The **broker** stores the message in the queue until a consumer takes it.
- The **consumer** doesn't poll. RabbitMQ **pushes** each message to it, and the client library
  calls your callback.

## The files

```
src/main/java/com/example/messagingrabbitmq/
  producer/Send.java    connects, declares "hello", publishes one message, exits
  consumer/Recv.java    connects, declares "hello", keeps running and prints every message
```

## Prerequisites

- The broker from `compose.yaml` is running. Check with `docker ps`; you should see
  `initial-rabbitmq-1`.
- The broker listens on **host port 5673** for AMQP and **15673** for the management UI, because
  another project's broker holds 5672 and 15672. The official tutorial uses the default 5672, so
  both classes call `factory.setPort(5673)`.
- Management UI: http://localhost:15673 (user `guest`, password `guest`).

## How to run a class

These are not Spring apps, so don't start `MessagingRabbitmqApplication`. That runs the Spring
guide's demo instead. You can tell you started the wrong one if you see the Spring banner or
`Sending message...` in the output.

To run `Send` or `Recv` in IntelliJ:

1. Open the class.
2. Click the green ▶ in the gutter next to `public static void main`.
3. Choose **Run '<Class>.main()'**.

A correct run of `Send` prints only its own line, with no Spring banner, and ends with
`Process finished with exit code 0`.

---

## Part 1: The sender (`Send`)

### Step 1a: Open a connection and a channel

```java
package com.example.messagingrabbitmq.producer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class Send {

    private static final String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5673); // our broker's host port (see compose.yaml); the tutorial uses the default 5672

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            System.out.println(" [x] Connected, channel " + channel.getChannelNumber());
        }
    }

}
```

| Piece | What it is |
|---|---|
| `ConnectionFactory` | Holds the broker's address: host, port and credentials (default `guest`/`guest`). |
| `newConnection()` | Opens a real TCP connection to RabbitMQ. Connections are expensive, so you open few of them. |
| `createChannel()` | Opens a **channel**, a lightweight session inside the connection. Almost every operation (declare, publish, consume) goes through a channel. |
| `try (...)` | Try-with-resources. It closes the channel and then the connection when the block ends. |

**Run it.** Expected output:

```
 [x] Connected, channel 1
```

The program exits right away because the connection is closed as soon as the block ends.

### Step 1b: Declare the queue

Replace the `println` inside the `try` block with:

```java
            Map<String, Object> args = Map.of("x-queue-type", "quorum");
            channel.queueDeclare(QUEUE_NAME, true, false, false, args);
            System.out.println(" [x] Queue '" + QUEUE_NAME + "' declared");
```

and add `import java.util.Map;`.

`queueDeclare(name, durable, exclusive, autoDelete, args)` creates the queue if it doesn't exist.
If the queue already exists with the **same** settings, the call does nothing, so declaring it
again is always safe.

| Argument | Value | Meaning |
|---|---|---|
| `name` | `"hello"` | The queue's name. |
| `durable` | `true` | The queue survives a broker restart. |
| `exclusive` | `false` | Other connections can use it too. The receiver needs this. |
| `autoDelete` | `false` | It isn't deleted when the last consumer disconnects. |
| `args` | `x-queue-type: quorum` | A **quorum queue**: replicated and durable. This is the queue type the tutorial recommends. |

**Run it.** Expected output:

```
 [x] Queue 'hello' declared
```

**Check the broker:** under **Queues and Streams** there is now a queue `hello` with type
**quorum** and **0** messages.

### Step 1c: Publish the message

Replace the `println` from Step 1b with:

```java
            String message = "Hello World!";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
```

and add `import java.nio.charset.StandardCharsets;`.

`basicPublish(exchange, routingKey, properties, body)`:

| Argument | Value | Meaning |
|---|---|---|
| `exchange` | `""` | The **default exchange**. It routes each message to the queue whose name equals the routing key. |
| `routingKey` | `"hello"` | So the message lands in the `hello` queue. |
| `properties` | `null` | No headers or metadata for now. |
| `body` | `byte[]` | RabbitMQ carries raw bytes. The string is encoded as UTF-8, and the receiver must decode it the same way. |

**Run it.** Expected output:

```
 [x] Sent 'Hello World!'
```

**Check the broker:** `hello` now shows **Ready: 1**. Nobody is consuming yet, so the message
waits in the queue. Each further run of `Send` adds one more message.

### Final `Send.java`

```java
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
            String message = "Hello World!";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }

}
```

---

## Part 2: The receiver (`Recv`)

### Step 2a: Connect and declare the queue, without closing

```java
package com.example.messagingrabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Map;

public class Recv {

    private static final String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5673); // our broker's host port (see compose.yaml)

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        Map<String, Object> args = Map.of("x-queue-type", "quorum");
        channel.queueDeclare(QUEUE_NAME, true, false, false, args);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
    }

}
```

Two differences from `Send`:

- **No `try (...)`.** The sender closes the connection as soon as it finishes. A receiver has to
  stay connected to wait for messages, so it never closes it. Even after `main` returns, the
  open connection's threads keep the JVM alive.
- **The same `queueDeclare` again.** Either program might start first, so each one declares the
  queue and whichever runs first creates it. Both declarations must use **identical** settings.
  If they don't, RabbitMQ refuses the second one with `PRECONDITION_FAILED`.

**Run it.** Expected output:

```
 [*] Waiting for messages. To exit press CTRL+C
```

The program keeps running. **Check the broker:**

- **Connections** shows one connection.
- `hello` still holds its messages, because we connected but haven't started consuming.

Stop `Recv` with the red ■ before the next step.

### Step 2b: Consume messages

Add after the "Waiting" line:

```java
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
        };
        channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
```

and add `import java.nio.charset.StandardCharsets;` and
`import com.rabbitmq.client.DeliverCallback;`.

- **`DeliverCallback`** is the code that runs for **each** message. The client library calls it
  on its own thread whenever the broker pushes a delivery. `delivery.getBody()` returns the raw
  bytes, which are decoded with the same UTF-8 encoding `Send` used.
- **`basicConsume(queue, autoAck, deliverCallback, cancelCallback)`** registers the consumer:

| Argument | Value | Meaning |
|---|---|---|
| `queue` | `"hello"` | Which queue to consume from. |
| `autoAck` | `true` | The broker counts a message as done as soon as it is **delivered**. If the app crashes while processing, that message is lost. Tutorial two switches to manual acknowledgements. |
| `deliverCallback` | the lambda | What to do with each message. |
| `cancelCallback` | `consumerTag -> { }` | Runs if the broker cancels the consumer, for example when the queue is deleted. Empty for now. |

**Run it.** Every message that was waiting is printed straight away:

```
 [*] Waiting for messages. To exit press CTRL+C
 [x] Received 'Hello World!'
 [x] Received 'Hello World!'
```

**Check the broker:** `hello` drops to **Ready: 0** and shows **Consumers: 1**.

### Final `Recv.java`

```java
package com.example.messagingrabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Recv {

    private static final String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5673); // our broker's host port (see compose.yaml)

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        Map<String, Object> args = Map.of("x-queue-type", "quorum");
        channel.queueDeclare(QUEUE_NAME, true, false, false, args);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
        };
        channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
    }

}
```

---

## Part 3: Putting it together

1. Start `Recv` and leave it running.
2. Run `Send` several times.
3. Each run's message appears in the `Recv` console within moments.

**Experiment: the queue as a buffer.**

1. Stop `Recv`.
2. Run `Send` three times. In the UI, `hello` shows **Ready: 3**.
3. Start `Recv`. It prints all three messages at once, and the queue drops back to 0.

This shows the core idea of a message queue: the producer and the consumer don't need to be
running at the same time. The broker holds the messages until someone takes them.

## What happens on each run

```
Send                                         Recv
────                                         ────
1. open TCP connection                       1. open TCP connection
2. open channel                              2. open channel
3. queueDeclare("hello", quorum)             3. queueDeclare("hello", quorum)   same settings
4. basicPublish("", "hello", body)           4. basicConsume("hello", autoAck=true, callback)
5. close channel and connection, exit        5. stay connected; the broker pushes messages
                                                → callback runs once per message
```

## Concepts introduced

| Concept | In one line |
|---|---|
| Producer | A program that sends messages (`Send`). |
| Consumer | A program that waits for and receives messages (`Recv`). |
| Queue | A buffer inside the broker that stores messages until they are consumed. |
| Connection | A TCP connection to the broker. |
| Channel | A lightweight session inside a connection; where the work happens. |
| Exchange | Receives published messages and routes them to queues. |
| Default exchange `""` | Routes a message to the queue named by its routing key. |
| Routing key | The address the producer attaches to a message. |
| Quorum queue | A durable, replicated queue type (`x-queue-type: quorum`). |
| Idempotent declare | Declaring an existing queue with the same settings is a harmless no-op. |
| Auto-ack | The message counts as handled once it is delivered. Simple, but it can lose messages. |

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| The output shows the Spring banner or `Sending message...` | You ran `MessagingRabbitmqApplication` instead of `Send` or `Recv`. Use the ▶ next to that class's own `main`. |
| `Connection refused` | The broker isn't running, or the port is wrong. Check `docker ps`, and check that both classes call `setPort(5673)`. |
| `PRECONDITION_FAILED - inequivalent arg 'x-queue-type'` | `hello` already exists with different settings, for example as a classic queue. Make the two declarations identical, or delete the queue in the UI and run again. |
| `Recv` prints nothing | Check that `Recv` is still running, and that `Send` publishes to the same queue name on the same port. |
| Messages are split between two consoles | Two `Recv` processes are running, and RabbitMQ spreads messages across consumers in turn. Stop the extra one. Tutorial two is about exactly this behavior. |

## Next

Tutorial two, [Work Queues](https://www.rabbitmq.com/tutorials/tutorial-two-java), covers several
consumers sharing a queue, manual acknowledgements in place of `autoAck=true`, and prefetch.
