package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class MdcKafkaConsumerInterceptorTest {

    private final MdcKafkaConsumerInterceptor interceptor = new MdcKafkaConsumerInterceptor();

    @AfterEach
    void clear() {
        MDC.remove(MdcKafkaConsumerInterceptor.MDC_KEY);
    }

    private static ConsumerRecord<Object, Object> recordWithHeader(String header) {
        ConsumerRecord<Object, Object> rec = new ConsumerRecord<>("t", 0, 0L, "k", "v");
        if (header != null) {
            rec.headers().add(new RecordHeader(MdcKafkaConsumerInterceptor.HEADER,
                    header.getBytes(StandardCharsets.UTF_8)));
        }
        return rec;
    }

    @Test
    void onConsume_recordWithHeader_putsMdc() {
        TopicPartition tp = new TopicPartition("t", 0);
        ConsumerRecords<Object, Object> records = new ConsumerRecords<>(
                Map.of(tp, List.of(recordWithHeader("xyz-7"))));
        interceptor.onConsume(records);
        assertEquals("xyz-7", MDC.get(MdcKafkaConsumerInterceptor.MDC_KEY));
    }

    @Test
    void onConsume_recordsWithoutHeader_doesNotPutMdc() {
        TopicPartition tp = new TopicPartition("t", 0);
        ConsumerRecords<Object, Object> records = new ConsumerRecords<>(
                Map.of(tp, List.of(recordWithHeader(null))));
        interceptor.onConsume(records);
        assertNull(MDC.get(MdcKafkaConsumerInterceptor.MDC_KEY));
    }

    @Test
    void onConsume_emptyBatch_noOp() {
        ConsumerRecords<Object, Object> records = ConsumerRecords.empty();
        interceptor.onConsume(records);
        assertNull(MDC.get(MdcKafkaConsumerInterceptor.MDC_KEY));
    }

    @Test
    void onConsume_headerWithNullValue_doesNotPutMdc() {
        // h != null but h.value() == null — the second arm of the guard.
        ConsumerRecord<Object, Object> rec = new ConsumerRecord<>("t", 0, 0L, "k", "v");
        rec.headers().add(new RecordHeader(MdcKafkaConsumerInterceptor.HEADER, (byte[]) null));
        TopicPartition tp = new TopicPartition("t", 0);
        ConsumerRecords<Object, Object> records = new ConsumerRecords<>(Map.of(tp, List.of(rec)));
        interceptor.onConsume(records);
        assertNull(MDC.get(MdcKafkaConsumerInterceptor.MDC_KEY));
    }

    @Test
    void noOpHooks_doNotThrow() {
        assertDoesNotThrow(() -> {
            interceptor.onCommit(Map.of());
            interceptor.close();
            interceptor.configure(Map.of());
        });
    }
}
