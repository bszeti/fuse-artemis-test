package bszeti.camelspringboot.jmstest;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Routes extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(Routes.class);

    private AtomicInteger counter = new AtomicInteger();
    private int previous = 0;

    @Override
    public void configure() throws Exception {

        log.error("Test",new Exception("log"));

        from("amqp:{{receive.endpoint}}")
            .routeId("amqp.receive")
            .log(LoggingLevel.DEBUG, log, "Message received: ${exchangeId} - ${body}")

            .choice()
                .when(simple("${body} contains 'error' "))
                .throwException(new Exception("error"))
            .end()

            .process(e->counter.incrementAndGet())
            .delay(constant("{{receive.delay}}"))
            .log(LoggingLevel.DEBUG, log, "Message processed: ${exchangeId}")
        ;

        from("timer:printCounter?period=1000")
            .setBody(b->{
                int current = counter.get();
                int diff = current - previous;
                previous=current;
                return "current: " + current + " - " +diff +"/s";
            })
            .log(LoggingLevel.INFO, log, "${body}")
        ;

    }

}
