package org.acme.message.bridge;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import io.quarkus.artemis.test.ArtemisTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.acme.message.bridge.resource.IBMMQTestResource;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
@QuarkusTestResource(ArtemisTestResource.class)
@QuarkusTestResource(IBMMQTestResource.class)
@EnabledIfSystemProperty(named = "ibm.mq.container.license", matches = "accept")
public class MessageBridgeITCase {
    private static final File LOG_FILE = new File("target/quarkus.log");

    @Test
    public void shouldSendMessageToActiveMQTest() {
        final String message = RandomStringUtils.randomAlphabetic(8);
        RestAssured
                .given()
                .body(message)
                .post("/message");

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(Files.readString(LOG_FILE.toPath())).contains("ActiveMQ received: " + message));
    }

    @Test
    public void shouldRollbackMessageTest() {
        final String message = RandomStringUtils.randomAlphabetic(8) + " rollback";
        RestAssured
                .given()
                .body(message)
                .post("/message");

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(Files.readString(LOG_FILE.toPath()))
                        .containsSubsequence(
                                "Sending message from IBMMQ to ActiveMQ: " + message,
                                "Simulated rollback",
                                "Redelivering message after rollback to ActiveMQ: " + message,
                                "ActiveMQ received: " + message));

    }
}
