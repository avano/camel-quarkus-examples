package org.acme.message.bridge.resource;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class IBMMQTestResource implements QuarkusTestResourceLifecycleManager {
    private static final String IMAGE_NAME = "icr.io/ibm-messaging/mq:9.3.2.1-r1";
    private static final int PORT = 1414;
    private static final String QUEUE_MANAGER_NAME = "QM1";
    private static final String USER = "app";
    private static final String PASSWORD = "passw0rd";
    private static final String MESSAGING_CHANNEL = "DEV.APP.SVRCONN";
    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(DockerImageName.parse(IMAGE_NAME))
                .withExposedPorts(PORT)
                .withEnv(Map.of(
                        "LICENSE", System.getProperty("ibm.mq.container.license"),
                        "MQ_QMGR_NAME", QUEUE_MANAGER_NAME,
                        "MQ_APP_PASSWORD", PASSWORD))
                // AMQ5806I is a message code for queue manager start
                .waitingFor(Wait.forLogMessage(".*AMQ5806I.*", 1));
        container.start();

        return Map.of(
                "ibm.mq.host", container.getHost(),
                "ibm.mq.port", container.getMappedPort(PORT).toString(),
                "ibm.mq.user", USER,
                "ibm.mq.password", PASSWORD,
                "ibm.mq.queueManagerName", QUEUE_MANAGER_NAME,
                "ibm.mq.channel", MESSAGING_CHANNEL);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
