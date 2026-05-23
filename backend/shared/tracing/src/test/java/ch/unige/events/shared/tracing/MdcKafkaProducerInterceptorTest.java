package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdcKafkaProducerInterceptorTest {

    private final MdcKafkaProducerInterceptor interceptor = new MdcKafkaProducerInterceptor();

    @AfterEach
    void clear() {
        MDC.remove(MdcKafkaProducerInterceptor.MDC_KEY);
    }

    @Test
    void onSend_mdcSet_addsHeader() {
        MDC.put(MdcKafkaProducerInterceptor.MDC_KEY, "abc-123");

        ProducerRecord<Object, Object> record = new ProducerRecord<>("t", "k", "v");
        ProducerRecord<Object, Object> out = interceptor.onSend(record);

        var header = out.headers().lastHeader(MdcKafkaProducerInterceptor.HEADER);
        assertEquals("abc-123",
                new String(header.value(), StandardCharsets.UTF_8));
    }

    @Test
    void onSend_mdcEmpty_noHeader() {
        ProducerRecord<Object, Object> record = new ProducerRecord<>("t", "k", "v");
        ProducerRecord<Object, Object> out = interceptor.onSend(record);
        assertNull(out.headers().lastHeader(MdcKafkaProducerInterceptor.HEADER));
    }

    @Test
    void onSend_mdcBlank_noHeader() {
        MDC.put(MdcKafkaProducerInterceptor.MDC_KEY, "   ");
        ProducerRecord<Object, Object> record = new ProducerRecord<>("t", "k", "v");
        ProducerRecord<Object, Object> out = interceptor.onSend(record);
        assertNull(out.headers().lastHeader(MdcKafkaProducerInterceptor.HEADER));
    }

    @Test
    void onSend_existingHeader_preserved() {
        MDC.put(MdcKafkaProducerInterceptor.MDC_KEY, "new");
        ProducerRecord<Object, Object> record = new ProducerRecord<>("t", "k", "v");
        record.headers().add(new RecordHeader(MdcKafkaProducerInterceptor.HEADER,
                "preset".getBytes(StandardCharsets.UTF_8)));
        ProducerRecord<Object, Object> out = interceptor.onSend(record);
        var header = out.headers().lastHeader(MdcKafkaProducerInterceptor.HEADER);
        assertEquals("preset", new String(header.value(), StandardCharsets.UTF_8));
    }

    @Test
    void noOpHooks_doNotThrow() {
        interceptor.onAcknowledgement(null, null);
        interceptor.close();
        interceptor.configure(java.util.Map.of());
    }

    @Test
    void onAcknowledgement_nullMetadata_withException_logsUnknownWithoutThrowing() {
        // metadata == null → the two ternaries fall to "(unknown)" / -1.
        assertDoesNotThrow(() ->
                interceptor.onAcknowledgement(null, new IOException("no metadata")));
    }

    @Test
    void onAcknowledgement_withException_logsWarn() {
        Logger jul = Logger.getLogger(MdcKafkaProducerInterceptor.class.getName());
        Level originalLevel = jul.getLevel();
        List<LogRecord> captured = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        jul.addHandler(handler);
        jul.setLevel(Level.ALL);
        try {
            RecordMetadata meta = new RecordMetadata(
                    new TopicPartition("events.lifecycle", 3),
                    0L, 0, 0L, 0, 0);
            IOException boom = new IOException("broker unavailable");

            interceptor.onAcknowledgement(meta, boom);

            assertTrue(
                captured.stream().anyMatch(r -> {
                    String msg = r.getMessage();
                    return msg != null && msg.contains("[KAFKA_PRODUCE_FAIL]")
                            && r.getLevel().intValue() >= Level.WARNING.intValue();
                }),
                "expected a WARN log record containing [KAFKA_PRODUCE_FAIL] but captured=" + captured);
        } finally {
            jul.removeHandler(handler);
            jul.setLevel(originalLevel);
        }
    }
}
