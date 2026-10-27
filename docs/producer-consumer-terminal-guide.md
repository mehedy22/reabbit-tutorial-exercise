# Producer and Consumer in the IntelliJ Terminal

This guide runs the RabbitMQ producer and consumer as two separate apps, each in its own
IntelliJ terminal. You type a message in the producer terminal and see it arrive in the
consumer terminal.

## How it works

```
Producer terminal                 RabbitMQ (Docker)                         Consumer terminal
-----------------                 -----------------                         -----------------
you type "hello"  ──publish──▶  exchange "spring-boot-exchange"
                                   │ routing key "foo.bar.baz"
                                   │ matches binding "foo.bar.#"
                                   ▼
                                queue "spring-boot"  ──deliver──▶  Received <hello>
```

The same jar runs in three modes, chosen with a Spring profile:

| Profile    | What it does                                                                 |
|------------|------------------------------------------------------------------------------|
| `consumer` | Listens on the `spring-boot` queue and prints every message it receives.     |
| `producer` | Reads each line you type and publishes it. It does not listen.               |
| *(none)*   | The original Spring guide demo: sends one message, receives it, keeps running. |

The code is split into layers under `com.example.messagingrabbitmq`:

```
MessagingRabbitmqApplication.java    entry point only
config/
  RabbitConstants.java               exchange, queue, binding key and routing key names
  RabbitConfig.java                  declares queue, exchange, binding and the listener
                                     (listener skipped in the producer profile)
producer/
  MessageProducer.java               publishes a message; the only class using RabbitTemplate
consumer/
  MessageReceiver.java               handles each message delivered from the queue
runner/
  ConsoleProducerRunner.java         reads typed lines and sends them (producer profile)
  DemoRunner.java                    the guide's one-shot demo (no profile)
```

## Prerequisites

- **Java 25.** Check with `java -version`.
- **Docker** running.
- **Ports 5673 and 15673 free.** This project's broker uses them, because 5672 and 15672 may
  already be taken by another project's RabbitMQ.

## Steps

The IntelliJ terminal already opens in the project folder (`initial`), so you don't need to `cd`.

### 1. Open the terminal

Press **Alt+F12**, or use **View → Tool Windows → Terminal**, or click the **Terminal** tab at the
bottom of the window.

### 2. Open a second terminal next to it

- Click the **+** next to the terminal tab name to open a second tab, or
- right-click the terminal tab and choose **Split Right** to see both at once.

Use the first one as the **consumer** and the second as the **producer**.

### 3. Stop IntelliJ's own runs first

Click the red **Stop** button on every Run/Debug tab of this app. Any running copy of the app is
also a consumer on the same queue and will take some of your messages (see
[Troubleshooting](#troubleshooting)).

### 4. First terminal: consumer

```bash
docker compose up -d --wait
./gradlew bootJar
java -jar build/libs/messaging-rabbitmq-initial-0.0.1-SNAPSHOT.jar --spring.profiles.active=consumer
```

- `docker compose up -d --wait` starts the broker and waits until it is healthy.
- `./gradlew bootJar` builds the runnable jar. Re-run it after any code change.

Wait for `Started MessagingRabbitmqApplication`. The consumer is now listening.

### 5. Second terminal: producer

```bash
java -jar build/libs/messaging-rabbitmq-initial-0.0.1-SNAPSHOT.jar --spring.profiles.active=producer
```

When it prints `Producer ready.`, type a message and press Enter:

```
hello      → second terminal: Sent <hello>
           → first terminal:  Received <hello>
exit       → quits the producer
```

### 6. When you're done

Press **Ctrl+C** in the first terminal, then run:

```bash
docker compose down
```

## Checking the queue

From any terminal:

```bash
curl -s -u guest:guest http://localhost:15673/api/queues/%2F/spring-boot \
  | grep -oE '"(messages|consumers)":[0-9]+'
```

- **`consumers`** should be `1` (the consumer terminal). A higher number means another copy of the
  app is still connected.
- **`messages`** is how many messages are waiting. Stop the consumer with Ctrl+C, send a few
  messages from the producer, and this number goes up. Start the consumer again and it receives
  all of them.

The management UI is at http://localhost:15673 (login `guest` / `guest`).

## Publishing without the producer app

You can also publish straight to the broker's HTTP API:

```bash
curl -s -u guest:guest -H 'content-type: application/json' \
  -X POST http://localhost:15673/api/exchanges/%2F/spring-boot-exchange/publish \
  -d '{"properties":{"content_type":"text/plain"},"routing_key":"foo.bar.test","payload":"Hello from curl","payload_encoding":"string"}'
```

- `{"routed":true}` means the message reached the queue.
- `{"routed":false}` means no binding matched. The routing key must start with `foo.bar.`.
- Keep `"content_type":"text/plain"`. Without it the consumer gets raw bytes and fails with
  `NoSuchMethodException: receiveMessage([B)`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Consumer only shows some of the messages, or none | Another copy of the app (often an IntelliJ Run/Debug session) is also consuming the queue. RabbitMQ hands messages to consumers in turn. Stop the others, or run `pkill -f MessagingRabbitmqApplication`. Check with the `consumers` count above. |
| `java: command not found`, or an older Java version | The terminal isn't using JDK 25. Check **Settings → Tools → Terminal**, or use the full path: `~/.jdks/ms-25.0.5.0.2/bin/java -jar ...`. |
| `Bind for 0.0.0.0:5673 failed: port is already allocated` | Something else is using port 5673. Stop it, or change the host port in `compose.yaml` and `spring.rabbitmq.port` in `application.yml`. |
| `Connection refused` on startup | The broker isn't running. Run `docker compose up -d --wait`. |
| Code changes don't show up | The jar is stale. Run `./gradlew bootJar` again. |

## Configuration notes

- **Port:** `application.yml` defaults to `localhost:5673` to match `compose.yaml`. Override with the
  `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` and `RABBITMQ_VHOST`
  environment variables.
- **Durable queue:** the `spring-boot` queue is durable. RabbitMQ 4.x rejects the non-durable queue
  the original guide used.
- **Docker Compose with `./gradlew bootRun`:** Spring Boot starts the broker automatically and leaves
  it running on exit (`lifecycle-management: start-only`), so one side quitting doesn't stop the
  broker for the other. A jar run with `java -jar` doesn't do this, which is why step 4 starts the
  broker by hand.
