package bszeti.camelspringboot.jmstest;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.LoggingLevel;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Routes extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(Routes.class);

    private AtomicInteger receiveCounter = new AtomicInteger();
    private int receiveCounterLast = 0;
    private AtomicInteger receiveForwardedCounter = new AtomicInteger();
    private AtomicInteger sendCounter = new AtomicInteger();
    private int sendCounterLast = 0;

    @Value("${receive.enabled}")
    Boolean receiveEnabled;

    @Value("${send.enabled}")
    Boolean sendEnabled;

    @Value("${send.threads}")
    int sendThreads;

    @Value("${send.message}")
    String sendMessage;

    @Override
    public void configure() throws Exception {

        onException(Exception.class)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR,"Camel onException: ${exception}")
        ;

        from("amqp:{{receive.endpoint}}")
            .routeId("amqp.receive").autoStartup("{{receive.enabled}}")
            .log(LoggingLevel.DEBUG, log, "Message received: ${exchangeId} - ${body}")

            .choice()
                .when(simple("${body} contains 'error' "))
                .throwException(new Exception("error"))
            .end()

            .setHeader("receiveCounter").exchange(e->receiveCounter.incrementAndGet())

            .choice()
                .when(constant("{{receive.forward.enabled}}"))
                .delay(constant("{{receive.forward.delay}}"))
                .log(LoggingLevel.DEBUG, log, "Message forward: ${exchangeId} - ${header.counter}")
                .to("amqp:{{receive.forward.endpoint}}")
                .process(e-> receiveForwardedCounter.incrementAndGet())
            .end()
            
            .delay(constant("{{receive.delay}}"))
            .log(LoggingLevel.DEBUG, log, "Message processed: ${exchangeId}")
        ;

        from("timer:sender?period=1&repeatCount={{send.threads}}")
            .routeId("amqp.send").autoStartup("{{send.enabled}}")

                .threads().poolSize(sendThreads).maxPoolSize(sendThreads).maxQueueSize(sendThreads).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)

                    .log(LoggingLevel.INFO, log, "Sending {{send.count}}")
                    .loop(constant("{{send.count}}"))
                        .log(LoggingLevel.DEBUG, log, "Send msg: ${exchangeId}-${header.CamelLoopIndex}")

                        .setBody(simple(sendMessage))
                        .setHeader("{{send.headeruuid}}").exchange(e->java.util.UUID.randomUUID().toString())
                        .to("amqp:{{send.endpoint}}?transacted=false")
                        .process(e-> sendCounter.incrementAndGet())
                        .delay(constant("{{send.delay}}"))
                        .log(LoggingLevel.DEBUG, log, "Sent msg: ${exchangeId}-${header.CamelLoopIndex} - ${body}")
                    .end()
                .end()
            .log(LoggingLevel.INFO, log, "Done - {{send.count}}")
        ;

        from("timer:printCounter?period=1000")
            .setBody(b->{
                if (receiveEnabled) {
                    int forwarder = receiveForwardedCounter.get();
                    int current = receiveCounter.get();
                    int diff = current - receiveCounterLast;
                    receiveCounterLast = current;
                    return "receive: " + current + " (" + forwarder + ") "+ " - " + diff + "/s";
                }
                if (sendEnabled) {
                    int current = sendCounter.get();
                    int diff = current - sendCounterLast;
                    sendCounterLast = current;
                    return "send   : " + current + " - " + diff + "/s";
                }
                return null;
            })
            .log(LoggingLevel.INFO, log, "${body}")
        ;

    }

}
