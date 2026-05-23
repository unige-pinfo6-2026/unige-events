package ch.unige.events.event.attachment.service;

import ch.unige.events.event.attachment.entity.EventAttachment;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.service.EventService;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import ch.unige.events.shared.storage.FileStorageService;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the {@code callerUuid == null} defensive guards in
 * {@link EventAttachmentService#upload} and {@link EventAttachmentService#delete}.
 *
 * <p>These guards are unreachable through the wired runtime: the real
 * {@link CallerIdentity#requireUuid()} <em>throws</em> rather than returning
 * null. So the service is built by hand with a mocked {@link CallerIdentity}
 * whose {@code requireUuid()} returns null, and the Panache statics are stubbed
 * via {@link PanacheMock}. Annotated {@code @QuarkusTest} so the executed
 * service bytecode is captured by quarkus-jacoco.
 */
@QuarkusTest
class EventAttachmentServiceExceptionPathsTest {

    private EventAttachmentService service;

    private final Long eventId = 7L;
    private final Long attachmentId = 99L;

    @BeforeEach
    void setUp() {
        EventService eventService = mock(EventService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        CallerIdentity callerIdentity = mock(CallerIdentity.class);
        when(callerIdentity.requireUuid()).thenReturn(null);
        service = new EventAttachmentService(eventService, fileStorageService, callerIdentity);
    }

    @Test
    void upload_nullCallerUuid_throwsNotFound() {
        PanacheMock.mock(Event.class);
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(new Event()));

        assertThrows(NotFoundException.class,
                () -> service.upload(eventId, mock(FileUpload.class), false));
    }

    @Test
    void delete_nullCallerUuid_throwsNotFound() {
        PanacheMock.mock(EventAttachment.class);
        EventAttachment attachment = new EventAttachment();
        attachment.eventId = eventId; // path matches → past the anti-oracle guard
        when(EventAttachment.findByIdOptional(attachmentId)).thenReturn(Optional.of(attachment));

        assertThrows(NotFoundException.class,
                () -> service.delete(eventId, attachmentId, false));
    }

    @Test
    void delete_pathMismatch_throwsNotFound() {
        PanacheMock.mock(EventAttachment.class);
        EventAttachment attachment = new EventAttachment();
        attachment.eventId = eventId + 1; // belongs to another event → 404 anti-oracle
        when(EventAttachment.findByIdOptional(any())).thenReturn(Optional.of(attachment));

        assertThrows(NotFoundException.class,
                () -> service.delete(eventId, attachmentId, false));
    }
}
