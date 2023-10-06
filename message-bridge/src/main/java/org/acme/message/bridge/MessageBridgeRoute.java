package org.acme.message.bridge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.transaction.jta.JtaTransactionManager;

@ApplicationScoped
public class MessageBridgeRoute extends RouteBuilder {
    @Inject
    JtaTransactionManager transactionManager;

    @Override
    public void configure() throws Exception {
        rest()
                .post("/message")
                .id("rest")
                .to("direct:publish");

        from("direct:publish")
                .id("ibmmq")
                .transacted()
                .log("Sending message to IBMMQ: ${body}")
                .to("ibmmq:queue:{{ibm.mq.queue}}?disableReplyTo=true");

        from("ibmmq:queue:{{ibm.mq.queue}}")
                .id("ibmmq-amq")
                .transacted()
                .process(ex -> {
                    DummyXAResource xaResource = new DummyXAResource("crash".equals(ex.getIn().getBody(String.class)));
                    transactionManager.getTransactionManager().getTransaction().enlistResource(xaResource);
                })
                .choice()
                .when(simple("${header.JMSRedelivered}"))
                .log("Redelivering message after rollback to ActiveMQ: ${body}")
                .otherwise()
                .log("Sending message from IBMMQ to ActiveMQ: ${body}")
                .end()
                .to("amq:queue:{{amq.queue}}")
                .process(ex -> {
                    if (ex.getIn().getBody(String.class).toLowerCase().contains("rollback")) {
                        // To simulate the rollback just once, we examine the value of the JMSRedelivered flag in the message
                        // if the value is "false", we initiate the rollback
                        // if the value is "true", it indicates that the rollback has already occurred,
                        // so we allow the message to proceed through the route successfully
                        if (!ex.getIn().getHeader("JMSRedelivered", Boolean.class)) {
                            // Simulate rollback
                            throw new RuntimeException("Simulated rollback");
                        }
                    }
                });

        from("amq:queue:{{amq.queue}}")
                .id("amq")
                .log("ActiveMQ received: ${body}");
    }
}
