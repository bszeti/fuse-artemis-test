package bszeti.camelspringboot.jmstest;

import org.apache.camel.Headers;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class Routes extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(Routes.class);

    @Autowired
    ApplicationContext applicationContext;

    private AtomicInteger receiveCounter = new AtomicInteger();
    private int receiveCounterLast = 0;
    private AtomicInteger receiveForwardedCounter = new AtomicInteger();
    private AtomicInteger sendCounter = new AtomicInteger();
    private int sendCounterLast = 0;

    @Value("${receive.enabled}")
    Boolean receiveEnabled;

    @Value("${receive.shutdownMessageCount}")
    Integer receiveShutdownMessageCount;

    @Value("${receive.shutdownIdleSec}")
    Integer receiveShutdownIdleSec;

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

    @Value("#{${send.headers}}")
    Map<String,String> sendHeaders;

    @Value("${send.shutdownEnabled}")
    Boolean sendShutdownEnabled;

    @Value("${shutdownSec}")
    Integer shutdownSec;


    // Incomplete send thread counter
    CountDownLatch sendActive;

    // Messages received in last few seconds
    List<Integer> receiveCounterHistory;

    Map<Object,Object> extraHeaders = new HashMap<>();

    @PostConstruct
    private void postConstruct(){
        log.info("send.message: {}",sendMessage);
        // Overwrite send message body with a given length string
        if (sendMessageLength>0) {
            sendMessage = "${ref:messageWithSetLenght}";
        }

        // Create Map with extra headers
        if (sendHeadersCount>0){
            for(int i=0; i<sendHeadersCount; i++) {
                String key="extra"+i;
                String value=String.format("%1$"+sendHeadersLength+ "s", "").replace(" ","H");
                extraHeaders.put(key,value);
            }
        }

        // Prepare send countdown latch, so we can stop once threads are done
        sendActive = new CountDownLatch(sendThreads);

        //Prepare receiveCounterHistory
        receiveCounterHistory = new ArrayList<>(receiveShutdownIdleSec);
    }

    public void addExtraHeaders(@Headers Map<Object,Object> headers){
        // Add additional headers with given name and value
        if (sendHeaders!=null) headers.putAll(sendHeaders);
        // Generated headers with given length
        headers.putAll(extraHeaders);
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
                .onCompletion()
                    .log(LoggingLevel.INFO, log, "Done - {{send.count}}")
                    .process(e -> sendActive.countDown())
                .end()

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
        ;

        // Print counters in log
        from("timer:printCounter?period=1000").routeId("status")
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
            // Check shutdown conditions
            .process(e->{
                // Shutdown after given time
                if (shutdownSec>0 && (Instant.now().isAfter(
                        Instant.ofEpochMilli(applicationContext.getStartupDate()).plus(shutdownSec, ChronoUnit.SECONDS))))
                    shutdownApp();

                // Send shutdown check
                if (sendShutdownEnabled && sendActive.getCount()==0)
                    shutdownApp();

                // Receive shutdown check
                if (receiveEnabled) {
                    // Did we reach min expected message count
                    if (receiveShutdownMessageCount>0 && receiveCounter.get()>=receiveShutdownMessageCount)
                        shutdownApp();
                    // Did we receive no message lately
                    if (receiveShutdownIdleSec>0) {
                        int current = receiveCounter.get();
                        if (receiveCounterHistory.size()>=receiveShutdownIdleSec && receiveCounterHistory.remove(0) == current)
                            shutdownApp();
                        receiveCounterHistory.add(current);
                    }
                }
            })
        ;

    }

    public void shutdownApp(){
        log.info("Shutting down...");
        new Thread(()->SpringApplication.exit(applicationContext)).start();
    }

}
