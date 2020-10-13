package bszeti.camelspringboot.jmstest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Routes extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(Routes.class);

    private AtomicInteger receiveCounter = new AtomicInteger();
    private int receiveCounterLast = 0;
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

//        getContext().
//
//        ExecutorService senderExecutor = new ThreadPoolBuilder()
//            .maxQueueSize(sendThreads)
//            .poolSize(sendThreads)
//            .maxPoolSize(sendThreads)
//            .rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns) //Causes +1 sender thread.
//        .build();

        from("amqp:{{receive.endpoint}}")
            .routeId("amqp.receive").autoStartup("{{receive.enabled}}")
            .log(LoggingLevel.DEBUG, log, "Message received: ${exchangeId} - ${body}")

            .choice()
                .when(simple("${body} contains 'error' "))
                .throwException(new Exception("error"))
            .end()

            .process(e-> receiveCounter.incrementAndGet())
            .delay(constant("{{receive.delay}}"))
            .log(LoggingLevel.DEBUG, log, "Message processed: ${exchangeId}")
        ;


//        RouteDefinition sender =
//            from("timer:sender?period={{send.delay}}&repeatCount={{send.count}}")
//            .routeId("amqp.send").autoStartup("{{send.enabled}}");
//            if (sendThreads>1) {
//                sender.threads().poolSize(sendThreads-1).maxPoolSize(sendThreads-1).maxQueueSize(sendThreads-1).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns);
//            }
//            sender
//                .log(LoggingLevel.DEBUG, log, "Send msg: ${exchangeId}")
//                .setBody(simple(sendMessage))
//                .to("amqp:{{send.endpoint}}")
//                .process(e-> sendCounter.incrementAndGet())
//                .log(LoggingLevel.DEBUG, log, "Sent msg: ${exchangeId} - ${body}")
//        ;

//        from("timer:sender?period=0")
//            .routeId("amqp.send").autoStartup("{{send.enabled}}")
//            .log(LoggingLevel.DEBUG, log, "Sending {{send.count}}")
//            .loop(constant("{{send.count}}")).copy()
//                .threads().poolSize(sendThreads).maxPoolSize(sendThreads).maxQueueSize(sendThreads).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)
//                .log(LoggingLevel.DEBUG, log, "Send msg: ${exchangeId}-${header.CamelLoopIndex}")
//                .setBody(simple(sendMessage))
//                .to("amqp:{{send.endpoint}}")
//                .process(e-> sendCounter.incrementAndGet())
//                .delay(constant("{{send.delay}}"))
//                .log(LoggingLevel.DEBUG, log, "Sent msg: ${exchangeId} - ${body}")
//            ;

            from("timer:sender?period=1&repeatCount={{send.threads}}")
                .routeId("amqp.send").autoStartup("{{send.enabled}}")
                    .threads().poolSize(sendThreads).maxPoolSize(sendThreads).maxQueueSize(sendThreads).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)
                        .log(LoggingLevel.INFO, log, "Sending {{send.count}}")
                        .loop(constant("{{send.count}}"))
                            .log(LoggingLevel.DEBUG, log, "Send msg: ${exchangeId}-${header.CamelLoopIndex}")
                            .setBody(simple(sendMessage))
                            .to("amqp:{{send.endpoint}}")
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
                    int current = receiveCounter.get();
                    int diff = current - receiveCounterLast;
                    receiveCounterLast = current;
                    return "receive: " + current + " - " + diff + "/s";
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
