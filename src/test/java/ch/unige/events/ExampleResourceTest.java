package ch.unige.events;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import java.net.URL;

@QuarkusTest
class ExampleResourceTest {

    @TestHTTPResource("/hello")
    URL url;

    @Test
    void testHelloEndpoint() {
        given()
                .when().get(url)
                .then()
                .statusCode(200)
                .body(is("Hello from Quarkus REST"));
    }

}