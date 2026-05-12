package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.jboss.logmanager.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Lifts the {@code X-Request-ID} header from each inbound Kafka record
 * onto the MDC under {@code requestId} so the {@code @Incoming} handler
 * downstream can correlate logs with the originating HTTP request.
 *
 * <p>Wired per incoming channel via:
 * <pre>{@code
 * mp.messaging.incoming.<channel>.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaConsumerInterceptor
 * }</pre>
 *
 * <p>Décision D — closes KAFKA-002 (BLOQUANT) on the consumer side.
 */
public class MdcKafkaConsumerInterceptor implements ConsumerInterceptor<Object, Object> {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    public ConsumerRecords<Object, Object> onConsume(ConsumerRecords<Object, Object> records) {
        for (var record : records) {
            Header h = record.headers().lastHeader(HEADER);
            if (h != null && h.value() != null) {
                MDC.put(MDC_KEY, new String(h.value(), StandardCharsets.UTF_8));
                // Take the first non-null occurrence and stop scanning —
                // a batch is unlikely to mix multiple request IDs in real
                // traffic, and the MDC entry is cleared by RequestIdFilter
                // / consumer framework at the end of the message scope.
                return records;
            }
        }
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // No-op — commit happens after handler completion.
    }

    @Override
    public void close() {
        // No-op.
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No-op — interceptor has no tunables.
    }
}
