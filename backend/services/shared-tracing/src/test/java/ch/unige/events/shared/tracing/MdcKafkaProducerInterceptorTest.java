package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
