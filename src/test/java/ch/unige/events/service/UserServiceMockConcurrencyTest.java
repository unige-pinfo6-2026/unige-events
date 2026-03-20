package ch.unige.events.service;

import io.quarkus.oidc.UserInfo;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceMockConcurrencyTest {

    @Test
    void concurrentGetOrCreateReturnsSingleProfileIdentity() throws InterruptedException, ExecutionException {
        UserServiceMock userServiceMock = new UserServiceMock();
        int calls = 12;
        ExecutorService executorService = Executors.newFixedThreadPool(6);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int index = 0; index < calls; index++) {
            tasks.add(() -> userServiceMock.getOrCreateUser("auth0|race", new UserInfo("{\"email\": \"race@example.com\"}")).getId().toString());
        }

        List<Future<String>> results = executorService.invokeAll(tasks);
        executorService.shutdown();

        Set<String> uniqueIds = new HashSet<>();
        for (Future<String> result : results) {
            uniqueIds.add(result.get());
        }

        assertEquals(1, uniqueIds.size());
    }

    @Test
    void missingEmailClaimForProvisioningThrowsUnauthorized() {
        UserServiceMock userServiceMock = new UserServiceMock();

        assertThrows(NotAuthorizedException.class,
            () -> userServiceMock.getOrCreateUser("auth0|missing-email", null));
    }
}
