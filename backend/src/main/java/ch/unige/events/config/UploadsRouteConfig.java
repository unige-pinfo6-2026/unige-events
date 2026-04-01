package ch.unige.events.config;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class UploadsRouteConfig {

    private final Router router;
    private final String uploadsPath;

    @Inject
    public UploadsRouteConfig(Router router,
                               @ConfigProperty(name = "app.uploads.path",
                                               defaultValue = "/tmp/unige-events-uploads")
                               String uploadsPath) {
        this.router = router;
        this.uploadsPath = uploadsPath;
    }

    void init(@Observes StartupEvent ev) {
        router.route("/uploads/*")
              .handler(StaticHandler.create(FileSystemAccess.ROOT, uploadsPath));
    }
}
