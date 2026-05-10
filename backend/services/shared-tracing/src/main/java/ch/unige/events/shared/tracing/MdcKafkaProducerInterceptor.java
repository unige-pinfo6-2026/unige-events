package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.jboss.logmanager.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Decorates outgoing Kafka records with the {@code X-Request-ID} header
 * lifted from the current MDC. Pairs with {@link RequestIdFilter} which
 * stamps {@code requestId} on the MDC of every inbound HTTP request and
 * with {@link MdcKafkaConsumerInterceptor} on the consumer side.
 *
 * <p>Wired per outgoing channel via:
 * <pre>{@code
 * mp.messaging.outgoing.<channel>.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
 * }</pre>
 *
 * <p>Décision D — closes KAFKA-002 (BLOQUANT): the architecture doc had
 * promised this component but it was missing, breaking distributed
 * tracing for every Kafka emit.
 */
public class MdcKafkaProducerInterceptor implements ProducerInterceptor<Object, Object> {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        Object value = MDC.get(MDC_KEY);
        if (value == null) {
            return record;
        }
        String requestId = value.toString();
        if (requestId.isBlank()) {
            return record;
        }
        Headers headers = record.headers();
        if (headers.lastHeader(HEADER) != null) {
            return record;
        }
        headers.add(HEADER, requestId.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // No-op — tracing only adds a header; ack/nack does not need MDC.
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
