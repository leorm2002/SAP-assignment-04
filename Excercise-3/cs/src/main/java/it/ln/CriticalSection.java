package it.ln;

import com.rabbitmq.client.*;

import it.ln.CriticalSection.CriticalHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class CriticalSection implements AutoCloseable {

    private final Connection connection;
    private final Channel channel;
    private Long currentDeliveryTag;

    public CriticalSection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(Utils.HOST);

        this.connection = factory.newConnection();
        this.channel = connection.createChannel();

        channel.queueDeclare(Utils.QUEUE, true, false, false, null);
    }

    public void init() throws IOException {
        channel.basicPublish(
                "",
                Utils.QUEUE,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                Utils.TOKEN.getBytes(StandardCharsets.UTF_8));

        System.out.println("Released a token in the system");
    }

    public CriticalHandler waitToken() throws IOException {
        // Avoid double lock with a runtime error
        if (currentDeliveryTag != null) {
            throw new IllegalStateException("Token already acquired by this CriticalSection");
        }

        // For blocking against rabbit mq response
        final BlockingQueue<Delivery> deliveries = new ArrayBlockingQueue<>(1);

        final String consumerTag = channel.basicConsume(
                Utils.QUEUE,
                false, // DO not auto ack, we will manual ack at the end of the critical section
                (_, delivery) -> deliveries.offer(delivery), // when the message is, eventually, recieved put it into
                                                             // the queue
                tag -> {
                });

        try {
            // blocca qui finché arriva un token
            final Delivery delivery = deliveries.take();

            channel.basicCancel(consumerTag);

            currentDeliveryTag = delivery.getEnvelope().getDeliveryTag();

            return new CriticalHandler(currentDeliveryTag);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            try {
                channel.basicCancel(consumerTag);
            } catch (Exception ignored) {
            }

            throw new IOException("Interrupted while waiting for token", e);
        }
    }

    public class CriticalHandler implements AutoCloseable {

        private final long deliveryTag;
        private boolean closed = false;

        private CriticalHandler(long deliveryTag) {
            this.deliveryTag = deliveryTag;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }

            closed = true;

            // Add the token back to the system
            channel.basicPublish(
                    "",
                    Utils.QUEUE,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    Utils.TOKEN.getBytes(StandardCharsets.UTF_8));
            // In this moment the original token is taken but rabbit mq has not delete it
            // yet it's just "untakeble". we send the signale to delete it now
            channel.basicAck(deliveryTag, false);
            currentDeliveryTag = null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            channel.close();
        } catch (Exception ignored) {
        }

        connection.close();
    }
}