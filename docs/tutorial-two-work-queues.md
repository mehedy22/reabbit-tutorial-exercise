# Tutorial Two: Work Queues with the Plain Java Client

This guide walks through the official RabbitMQ tutorial
[Work Queues (Java client)](https://www.rabbitmq.com/tutorials/tutorial-two-java), adapted to
this project. It builds on [tutorial one](tutorial-one-hello-world.md): same broker, same plain
Java client, no Spring.

A **work queue** spreads time-consuming **tasks** across several **workers**. The producer
doesn't wait for a task to be done; it just queues it, and whichever worker is free handles it.

## How it works

```
NewTask (producer)              RabbitMQ (Docker)                          Workers (consumers)
------------------              -----------------                          -------------------
"Task one....."  ──publish──▶  default exchange ""
 (persistent)                     │ routing key "task_queue"
                                  ▼
                               queue "task_queue" (quorum, durable) ──▶  Worker 1  ─┐ ack when
                                                                    └──▶  Worker 2  ─┘ done
```

The work is simulated: **each `.` in a message means one second of work**, so
`Task three...` keeps a worker busy for 3 seconds.

## The files

```
src/main/java/com/example/messagingrabbitmq/
  producer/NewTask.java   declares "task_queue", publishes one persistent task from the program arguments, exits
  consumer/Worker.java    takes one task at a time, sleeps 1 s per '.', acks when done
```

The queue is new (`task_queue`, not `hello`). An existing queue can't be redeclared with
different settings, so the tutorial starts from a fresh one.

## Running in IntelliJ

**Passing the task text to `NewTask`:**

1. Open the run-configuration dropdown, then **NewTask** → **⋮** → **Edit...**.
2. Type the task into **Program arguments**, the field under the main class name. For
   example: `Third message...`
3. Click **▷ RUN** at the bottom of the dialog. It saves and runs in one click.

With no arguments, `NewTask` sends `Hello World!`, which has no dots and so means no work.

**Running two Workers at once:**

1. Open the run-configuration dropdown, then **Worker** → **⋮** → **Edit...**.
2. Click **Modify options ▾** (Alt+M) and tick **Allow multiple instances**. Click **OK**.
3. Click ▶ twice. The Run window shows two `Worker` tabs.

If you see "Worker is already running. Stop and rerun?", the option wasn't saved.

Alternatively, copy the configuration (the **Copy** icon in the Run/Debug Configurations
dialog) as `Worker 2` and run both.

After changing `Worker.java`, **stop and restart every Worker tab**. A running worker keeps
the old code.

---

## Step 1: The producer (`NewTask`)

```java
package com.example.messagingrabbitmq.producer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class NewTask {

    private static final String TASK_QUEUE_NAME = "task_queue";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5673); // our broker's host port (see compose.yaml)

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            Map<String, Object> args = Map.of("x-queue-type", "quorum");
            channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, args);

            String message = argv.length > 0 ? String.join(" ", argv) : "Hello World!";

            channel.basicPublish("", TASK_QUEUE_NAME,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }

}
```

Two things are new compared with `Send`:

| Change | Why |
|---|---|
| `String.join(" ", argv)` | You choose the task, and its length in dots, each time you run. |
| `MessageProperties.PERSISTENT_TEXT_PLAIN` instead of `null` | Sets `deliveryMode = 2` (**persistent**: written to disk) and `contentType = text/plain`. |

### Message durability

For a task to survive a broker restart, you need **both** of these:

1. a **durable queue** (`queueDeclare(..., durable = true, ...)`), so the queue itself survives;
2. **persistent messages** (`PERSISTENT_TEXT_PLAIN`), so the messages in it survive.

A durable queue on its own can come back empty. Even with both, there is a short window
before a message reaches disk. Publisher confirms (a later tutorial) close that gap.

**Run it** with no arguments. Expected output:

```
 [x] Sent 'Hello World!'
```

**Check the broker:** a new `task_queue` (quorum) appears with **Ready: 1**.

---

## Step 2: A first worker, and round-robin dispatching

The first version of `Worker` still uses `autoAck = true`, like `Recv`:

```java
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
            doWork(message);
            System.out.println(" [x] Done");
        };
        boolean autoAck = true;
        channel.basicConsume(TASK_QUEUE_NAME, autoAck, deliverCallback, consumerTag -> { });
```

and the fake work:

```java
    /** Fake work: sleep one second for every '.' in the task. */
    private static void doWork(String task) {
        for (char ch : task.toCharArray()) {
            if (ch == '.') {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // restore the flag instead of swallowing it
                }
            }
        }
    }
```

**Test it.** Start two Workers, then send:

```
First message.
Second message..
Third message...
Fourth message....
Fifth message.....
```

**Result:**

```
Worker 1: First, Third, Fifth
Worker 2: Second, Fourth
```

This is **round-robin dispatching**: RabbitMQ gives each consumer the next message in turn,
no matter how long the tasks take or how busy the consumer is. Adding more workers is how
a work queue scales.

**The problem with `autoAck = true`:** the broker deletes a message the moment it is
**delivered**. If a worker dies in the middle of a task, that task is lost for good.

---

## Step 3: Manual acknowledgements

Replace the callback and turn `autoAck` off:

```java
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
            try {
                doWork(message);
            } finally {
                System.out.println(" [x] Done");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        boolean autoAck = false; // we ack manually after the work is done
        channel.basicConsume(TASK_QUEUE_NAME, autoAck, deliverCallback, consumerTag -> { });
```

| Piece | Meaning |
|---|---|
| `basicAck(deliveryTag, multiple)` | Tells the broker "this message is handled, delete it". |
| `deliveryTag` | The channel's number for this delivery (1, 2, 3, …), which identifies *which* message is being acked. |
| `multiple = false` | Ack only this one message. `true` would ack every message up to this tag. |
| `finally` | The ack is sent even if `doWork` throws, so a failing task can't stay unacked forever. |
| Same channel | An ack must be sent on the channel that received the delivery. |

**What happens to an unacked message:** it shows as **Unacked** in the UI. If the worker's
channel or connection closes, the broker **requeues** it and delivers it to another worker.
Deliveries that stay unacked for more than 30 minutes (the default timeout) also cause the
broker to close the channel.

**Forgetting to ack** is a common bug. The messages are never deleted, they pile up as
Unacked, and memory use grows.

**Test it: kill a worker mid-task.**

1. Restart both Workers.
2. Send `Long task..........` (10 seconds of work).
3. In the Worker tab that receives it, press ■ **before** it prints `Done`.
4. The **other** Worker prints `Received 'Long task..........'` and finishes it.

**Result:** the task was **not lost**. The broker saw the connection drop with the message
unacked and redelivered it.

---

## Step 4: Fair dispatch with `basicQos(1)`

Even with manual acks, the broker pushes messages to a worker **without waiting for it to
finish**. By default the **prefetch** is unlimited.

**See the problem (before the fix):**

1. Stop both Workers.
2. Queue four tasks: `Task one.....`, `Task two.....`, `Task three.....`, `Task four.....`.
3. Start Worker 1, wait about 2 seconds, then start Worker 2.

**Result:** Worker 1 receives **all four** at once (the UI shows **Unacked: 4**), while
Worker 2 sits idle. The tasks take about 20 seconds in total.

**The fix:** add this line before the `DeliverCallback`:

```java
        channel.basicQos(1); // accept only one unacked message at a time
```

| Piece | Meaning |
|---|---|
| `basicQos(prefetchCount)` | The maximum number of unacked messages the broker will send this consumer at one time. |
| `1` | "Don't send me a new task until I've acked the current one." Each new task then goes to whichever worker is **free**. |

Prefetch only works together with manual acks, because the broker needs the ack to know a
worker has finished.

**Repeat the test.**

**Result:** Worker 1 gets tasks one and three, Worker 2 gets tasks two and four. **Unacked**
never goes above 2, and the work finishes in about 10 seconds instead of 20.

If every worker is busy, tasks wait in the queue. Watch the queue length and add workers
when it keeps growing.

---

## Final `Worker.java`

```java
package com.example.messagingrabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Worker {

    private static final String TASK_QUEUE_NAME = "task_queue";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5673); // our broker's host port (see compose.yaml)

        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();

        Map<String, Object> args = Map.of("x-queue-type", "quorum");
        channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, args);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        channel.basicQos(1); // accept only one unacked message at a time

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
            try {
                doWork(message);
            } finally {
                System.out.println(" [x] Done");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        boolean autoAck = false; // we ack manually after the work is done
        channel.basicConsume(TASK_QUEUE_NAME, autoAck, deliverCallback, consumerTag -> { });
    }

    /** Fake work: sleep one second for every '.' in the task. */
    private static void doWork(String task) {
        for (char ch : task.toCharArray()) {
            if (ch == '.') {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

}
```

## Putting it all together

| Feature | Code | What it gives you |
|---|---|---|
| Several workers | run `Worker` more than once | Parallel processing; scale by adding workers. |
| Round-robin | the default | Tasks are dealt out in turn. |
| Durable queue | `queueDeclare(..., true, ...)` | The queue survives a broker restart. |
| Persistent messages | `MessageProperties.PERSISTENT_TEXT_PLAIN` | The tasks in it survive too. |
| Manual acks | `autoAck = false` + `basicAck` | A worker's death doesn't lose its task; it is redelivered. |
| Fair dispatch | `basicQos(1)` | New tasks go to idle workers, not busy ones. |

## What we observed

| Experiment | Result |
|---|---|
| Five tasks, two workers, round-robin | Worker 1: 1st, 3rd, 5th. Worker 2: 2nd, 4th. |
| Kill a worker mid-task, manual acks | The other worker received and finished the task. |
| Four queued tasks, no prefetch limit | Worker 1 took all four; Worker 2 stayed idle. |
| Four queued tasks, `basicQos(1)` | The workers split them two and two. |

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "Worker is already running. Stop and rerun?" | **Allow multiple instances** isn't enabled. Edit the configuration → **Modify options** → tick it. |
| A worker behaves like the old code | Running workers don't pick up code changes. Stop every Worker tab and start them again. |
| Messages stay **Unacked** forever | `basicAck` is missing, or it is sent on a different channel. |
| One worker gets everything | `basicQos(1)` is missing, or the worker hasn't been restarted since you added it. |
| `PRECONDITION_FAILED` on `task_queue` | `NewTask` and `Worker` declare the queue differently. Make both declarations identical. |
| `NewTask` always sends `Hello World!` | **Program arguments** is empty. Set it in the NewTask run configuration. |

## Next

Tutorial three, [Publish/Subscribe](https://www.rabbitmq.com/tutorials/tutorial-three-java),
delivers the **same** message to **every** consumer, using a `fanout` exchange instead of the
default exchange.
