package bszeti.camelspringboot.jmstest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import io.micrometer.core.instrument.util.StringUtils;
import org.apache.camel.Headers;
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

    @Value("${send.message.length}")
    Integer sendMessageLength;

    @Value("${send.headers.count}")
    Integer sendHeadersCount;

    @Value("${send.headers.length}")
    Integer sendHeadersLength;

    @Value("${send.header.name}")
    String sendHeaderName;

    @Value("${send.header.value}")
    String sendHeaderValue;

    Map<Object,Object> extraHeaders = new HashMap<>();

    @PostConstruct
    private void postConstruct(){
        if (sendMessageLength>0) {
            sendMessage = "${ref:messageWithSetLenght}";
        }

        if (sendHeadersCount>0){
            for(int i=0; i<sendHeadersCount; i++) {
                String key="extra"+i;
                String value=String.format("%1$"+sendHeadersLength+ "s", "").replace(" ","H");
                extraHeaders.put(key,value);
            }
        }
    }

    public void addExtraHeaders(@Headers Map<Object,Object> headers){
        // Generated headers with given length
        headers.putAll(extraHeaders);
        // Add one additional header with given name and value
        if (!StringUtils.isBlank(sendHeaderName)) {
            headers.put(sendHeaderName,sendHeaderValue);
        }
    }

    @Override
    public void configure() throws Exception {

        onException(Exception.class)
            .maximumRedeliveries("{{exception.maximumredeliveries}}")
            .log(LoggingLevel.ERROR,"***************************************\n********************** Camel onException: ${exception}\n***************************************")
        ;

        // Receive messages and optionally forward them to another queue
        // If message body contains "error" an exception is thrown (before forwarding)
        // The consumer can have transacted=true, then the rest of the route uses transaction policy receive.forward.propagation. This is to test different scenarios for the forwarding. Default is PROPAGATION_REQUIRED
        from("amqp:{{receive.endpoint}}")
            .routeId("amqp.receive").autoStartup("{{receive.enabled}}")
//            .transacted("jmsSendTransaction")
            .log(LoggingLevel.DEBUG, log, "Received:  ${exchangeId} - ${header._AMQ_DUPL_ID} - ${header.JMETER_COUNTER}")

            .choice()
                .when(simple("${body} contains 'error' "))
                .throwException(new Exception("error"))
            .end()

            .setHeader("receiveCounter").exchange(e->receiveCounter.incrementAndGet())

            .choice()
                .when(constant("{{receive.forward.enabled}}"))
                .delay(constant("{{receive.forward.delay}}"))
//                .setHeader("_AMQ_DUPL_ID",constant("0000000000000000"))
                .to("amqp:{{receive.forward.endpoint}}")
                .log(LoggingLevel.DEBUG, log, "Forwarded: ${exchangeId} - ${header._AMQ_DUPL_ID} - ${header.JMETER_COUNTER} - ${header.counter}")
                .process(e-> receiveForwardedCounter.incrementAndGet())
                .end()
            .end()

            .delay(constant("{{receive.delay}}"))
            .log(LoggingLevel.DEBUG, log, "Processed: ${exchangeId} - ${header._AMQ_DUPL_ID} - ${header.JMETER_COUNTER}")
        ;

        // Send messages -  send.threads X send.count
        // Message body is from property send.message. For examepl a simple experessions: #{'$'}{exchangeId}/#{'$'}{header.CamelLoopIndex}
        // Add a UUID header. Use send.headeruuid=_AMQ_DUPL_ID for Artemis duplicate detection.
        from("timer:sender?period=1&repeatCount={{send.threads}}")
            .routeId("amqp.send").autoStartup("{{send.enabled}}")

                .threads().poolSize(sendThreads).maxPoolSize(sendThreads).maxQueueSize(sendThreads).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)

                    .log(LoggingLevel.INFO, log, "Sending {{send.count}}")
                    .loop(constant("{{send.count}}"))
                        .log(LoggingLevel.DEBUG, log, "Send msg: ${exchangeId}-${header.CamelLoopIndex}")

                        .setBody(simple(sendMessage))
                        .setHeader("{{send.headeruuid}}").exchange(e->java.util.UUID.randomUUID().toString())
                        .bean(this,"addExtraHeaders")

                        .to("amqp:{{send.endpoint}}")
                        .process(e-> sendCounter.incrementAndGet())
                        .delay(constant("{{send.delay}}"))
                        .log(LoggingLevel.DEBUG, log, "Sent msg: ${exchangeId}-${header.CamelLoopIndex} - ${body}")
                    .end()
                .end()
            .log(LoggingLevel.INFO, log, "Done - {{send.count}}")
        ;

        // Print counters in log
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
