package it.ln.cs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.server.SystemLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class CriticalSectionTests {

    private static final String HOST = "localhost";
    private static final int PORT = 5673;
    private static SystemLauncher broker;

    private String testQueue;

    @BeforeAll
    static void startBroker() throws Exception {
        System.setProperty("qpid.amqp_port", String.valueOf(PORT));

        URL configUrl = CriticalSectionTests.class
                .getClassLoader()
                .getResource("qpid-config.json");
        if (configUrl == null) {
            throw new IllegalStateException("qpid-config.json not found in src/test/resources");
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("type", "Memory");
        attributes.put("initialConfigurationLocation", configUrl.toExternalForm());
        attributes.put("startupLoggedToSystemOut", false);

        broker = new SystemLauncher();
        broker.startup(attributes);
    }

    @AfterAll
    static void stopBroker() {
        if (broker != null) {
            broker.shutdown();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        testQueue = "test.cs." + UUID.randomUUID();
        CriticalSection.initialize(HOST, PORT, testQueue);
    }

    @AfterEach
    void tearDown() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        try (Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {
            ch.queueDelete(testQueue);
        }
    }

    @Test
    @Timeout(15)
    void testMutualExclusionUnderContention() throws Exception {
        final int numWorkers = 5;
        final int iterationsPerWorker = 10;

        final AtomicInteger activeInCs = new AtomicInteger(0);
        final AtomicInteger maxConcurrent = new AtomicInteger(0);
        final int[] sharedCounter = { 0 }; // intenzionalmente non atomico

        ExecutorService pool = Executors.newFixedThreadPool(numWorkers);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numWorkers; i++) {
            futures.add(pool.submit(() -> {
                try (CriticalSection cs = new CriticalSection(HOST, PORT, testQueue)) {
                    for (int j = 0; j < iterationsPerWorker; j++) {
                        try (CriticalSection.CriticalHandler ignored = cs.waitToken()) {
                            int current = activeInCs.incrementAndGet();
                            maxConcurrent.accumulateAndGet(current, Math::max);

                            // Simula lavoro in sezione critica per forzare la contesa
                            int val = sharedCounter[0];
                            Thread.sleep(15);
                            sharedCounter[0] = val + 1;

                            activeInCs.decrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, maxConcurrent.get(), "Violazione Safety: più di un processo in sezione critica");
        assertEquals(numWorkers * iterationsPerWorker, sharedCounter[0], "Aggiornamenti persi in sezione critica");
    }

    @Test
    @Timeout(10)
    void testCrashRecoveryRequeuesToken() throws Exception {
        // Processo A acquisisce il token e "crasha" chiudendo la connessione senza rilasciare il handler
        CriticalSection csA = new CriticalSection(HOST, PORT, testQueue);
        csA.waitToken();
        csA.close(); // Simula crash improvviso (caduta TCP)

        // Processo B deve ricevere automaticamente il token rimesso in coda dal broker
        try (CriticalSection csB = new CriticalSection(HOST, PORT, testQueue);
             CriticalSection.CriticalHandler ignored = csB.waitToken()) {
            assertTrue(true);
        }
    }

    @Test
    @Timeout(10)
    void testInitializeFailsIfTokenAlreadyPresent() {
        assertThrows(IllegalStateException.class, () -> CriticalSection.initialize(HOST, PORT, testQueue));
    }

    @Test
    @Timeout(10)
    void testDoubleAcquireThrowsIllegalStateException() throws Exception {
        try (CriticalSection cs = new CriticalSection(HOST, PORT, testQueue);
             CriticalSection.CriticalHandler ignored = cs.waitToken()) {
            assertThrows(IllegalStateException.class, cs::waitToken);
        }
    }

    @Test
    @Timeout(10)
    void testInterruptWhileWaitingDoesNotDeadlockSystem() throws Exception {
        try (CriticalSection csHolder = new CriticalSection(HOST, PORT, testQueue);
             CriticalSection csWaiter = new CriticalSection(HOST, PORT, testQueue)) {

            // Processo 1 tiene occupata la sezione critica
            CriticalSection.CriticalHandler holderHandler = csHolder.waitToken();

            CountDownLatch waiterStarted = new CountDownLatch(1);
            AtomicInteger ioExceptions = new AtomicInteger(0);

            Thread waiterThread = new Thread(() -> {
                waiterStarted.countDown();
                try {
                    csWaiter.waitToken();
                } catch (IOException e) {
                    ioExceptions.incrementAndGet();
                }
            });

            waiterThread.start();
            waiterStarted.await();
            Thread.sleep(100); // Lascia che waiterThread si blocchi su deliveries.take()

            waiterThread.interrupt();
            waiterThread.join(3_000);

            assertEquals(1, ioExceptions.get(), "Il thread interrotto doveva lanciare IOException");

            // Processo 1 rilascia il token: un terzo processo deve poter entrare senza deadlock
            holderHandler.close();

            try (CriticalSection csNext = new CriticalSection(HOST, PORT, testQueue);
                 CriticalSection.CriticalHandler ignored = csNext.waitToken()) {
                assertTrue(true);
            }
        }
    }
}