package ch.unige.events.service;

import ch.unige.events.MockEventFactory;
import ch.unige.events.dto.event.ShareResponse;
import ch.unige.events.entity.Event;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class ShareServiceMock extends ShareService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    private final Map<String, Long> codeToEventId = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public void reset() {
        eventsById.clear();
        codeToEventId.clear();
        idSequence.set(1);
    }

    public Event seedEvent(String title) {
        Event event = MockEventFactory.build(title, idSequence);
        eventsById.put(event.id, event);
        return event;
    }

    @Override
    public ShareResponse getShareInfo(Long eventId) {
        Event event = eventsById.get(eventId);
        if (event == null) throw new NotFoundException();

        if (event.shareCode == null) {
            event.shareCode = "testCode" + eventId;
            codeToEventId.put(event.shareCode, eventId);
        }

        String shareUrl = "https://10.25.10.136.nip.io/events/" + eventId;
        return new ShareResponse(shareUrl, event.shareCode);
    }

    @Override
    public Event resolveByShortCode(String shortCode) {
        Long eventId = codeToEventId.get(shortCode);
        if (eventId == null) throw new NotFoundException();
        return eventsById.get(eventId);
    }
}
