package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the X-Request-ID propagation across the producer→record→consumer
 * round trip wired by {@link MdcKafkaProducerInterceptor} and
 * {@link MdcKafkaConsumerInterceptor} (Étape 24.7.5, C7 — pr-test-analyzer
 * I-4).
 *
 * <p>The smallrye-in-memory connector bypasses Kafka client interceptors
 * (cf. note in {@code event-service/src/main/resources/application.properties}),
 * so this test simulates the in-memory hand-off by feeding the producer's
 * decorated record straight into the consumer interceptor — an end-to-end
 * round trip without a broker. The test goes red the moment either
 * interceptor stops carrying the header.
 */
class MdcKafkaInMemoryTest {

    private static final String TOPIC = "test-trace";
    private static final String EXPECTED_REQUEST_ID = "req-abc-123";

    private final MdcKafkaProducerInterceptor producer = new MdcKafkaProducerInterceptor();
    private final MdcKafkaConsumerInterceptor consumer = new MdcKafkaConsumerInterceptor();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.remove(MdcKafkaProducerInterceptor.MDC_KEY);
    }

    @Test
    @DisplayName("X-Request-ID propagates: producer stamps header from MDC, consumer hydrates MDC from header")
    void requestId_propagatesAcrossKafkaRoundTrip() {
        MDC.put(MdcKafkaProducerInterceptor.MDC_KEY, EXPECTED_REQUEST_ID);

        // Producer side: MDC-stamped record.
        ProducerRecord<Object, Object> sent = producer.onSend(
                new ProducerRecord<>(TOPIC, "k", "payload"));
        Header headerOut = sent.headers().lastHeader(MdcKafkaProducerInterceptor.HEADER);
        assertNotNull(headerOut, "producer interceptor must add X-Request-ID header");

        // Carry the headers across the broker hand-off (in-memory: same JVM).
        ConsumerRecord<Object, Object> received = new ConsumerRecord<>(
                TOPIC, 0, 0L, "k", "payload");
        sent.headers().forEach(h -> received.headers().add(h));
        ConsumerRecords<Object, Object> batch = new ConsumerRecords<>(Map.of(
                new TopicPartition(TOPIC, 0), List.of(received)));

        // Consumer thread starts with a clean MDC (different worker thread
        // in real Kafka — simulated here by clearing before onConsume).
        MDC.remove(MdcKafkaProducerInterceptor.MDC_KEY);
        assertNull(MDC.get(MdcKafkaConsumerInterceptor.MDC_KEY),
                "MDC must start empty on the consumer side");

        consumer.onConsume(batch);

        assertEquals(EXPECTED_REQUEST_ID, MDC.get(MdcKafkaConsumerInterceptor.MDC_KEY),
                "consumer interceptor must hydrate MDC from the X-Request-ID header");
    }

    @Test
    @DisplayName("Empty MDC on producer side leaves the record (and the consumer MDC) header-less")
    void noRequestIdInMdc_doesNotPollute_consumerMdc() {
        // Pre-condition: MDC is clean (verified by @BeforeEach).
        ProducerRecord<Object, Object> sent = producer.onSend(
                new ProducerRecord<>(TOPIC, "k", "payload"));
        assertNull(sent.headers().lastHeader(MdcKafkaProducerInterceptor.HEADER),
                "producer must NOT stamp a header when MDC is empty");

        ConsumerRecord<Object, Object> received = new ConsumerRecord<>(
                TOPIC, 0, 0L, "k", "payload");
        ConsumerRecords<Object, Object> batch = new ConsumerRecords<>(Map.of(
                new TopicPartition(TOPIC, 0), List.of(received)));

        consumer.onConsume(batch);

        assertNull(MDC.get(MdcKafkaConsumerInterceptor.MDC_KEY),
                "consumer must NOT hydrate MDC when no header is present");
    }
}
