package it.ln.cs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.MessageProperties;

public class CriticalSection implements AutoCloseable {
    private static final int CONFIRMATION_TIMEOUT = 5_000;
    private static final String TOKEN = "xxx";
    private static final byte[] TOKEN_B = TOKEN.getBytes(StandardCharsets.UTF_8);
    private final Connection connection;
    private final Channel channel;
    private Long currentDeliveryTag;
    private final String queue;

    public CriticalSection(String host, String queue) throws IOException, TimeoutException {
        this(host, ConnectionFactory.DEFAULT_AMQP_PORT, queue);
    }

    public CriticalSection(String host, int port, String queue) throws IOException, TimeoutException {
        this.queue = queue;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);

        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
        channel.basicQos(1);

        channel.queueDeclare(queue, true, false, false, null);
    }


    public CriticalHandler waitToken() throws IOException {
        // Avoid double lock with a runtime error
        if (currentDeliveryTag != null) {
            throw new IllegalStateException("Token already acquired by this CriticalSection");
        }

        // For blocking against rabbit mq response
        final BlockingQueue<Delivery> deliveries = new ArrayBlockingQueue<>(1);

        final String consumerTag = channel.basicConsume(
                queue,
                false, // DO not auto ack, we will manual ack at the end of the critical section
                (x, delivery) -> deliveries.offer(delivery), // when the message is, eventually, recieved put it into
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
                // Tell RabbitMQ to not give more messages 
                channel.basicCancel(consumerTag);

                // Check if another token was already arrived before the interupt
                final Delivery lateDelivery = deliveries.poll();
                if (lateDelivery != null) {
                    //  Put it back in the queue
                    channel.basicNack(lateDelivery.getEnvelope().getDeliveryTag(),false,true);
                }
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
            try {
                channel.basicNack(deliveryTag, false, true);
            } finally{
                currentDeliveryTag = null;
            }
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


    /**
     * Initialise the queue with the unique token. It must be called only one time duting the bootrsap of the queue
     */
    public static void initialize(String host, String queue) throws IOException, TimeoutException, InterruptedException {
        initialize(host, ConnectionFactory.DEFAULT_AMQP_PORT, queue);
    }

    public static void initialize(String host, int port, String queue) throws IOException, TimeoutException, InterruptedException {
        final ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {

            final DeclareOk declareOk = channel.queueDeclare(queue, true, false, false, null);

            // Blocca se c'è già un token READY in coda o se ci sono consumer attivi
            if (declareOk.getMessageCount() > 0 || declareOk.getConsumerCount() > 0) {
                throw new IllegalStateException("Queue '" + queue + "' already contains a token or has active consumers");
            }

            // Garantisce che il broker confermi la ricezione del token
            channel.confirmSelect();

            channel.basicPublish("",queue,MessageProperties.PERSISTENT_TEXT_PLAIN,TOKEN_B);

            channel.waitForConfirmsOrDie(CONFIRMATION_TIMEOUT);
            System.out.println("Released a token in queue: " + queue);
        }
    }

}