package bszeti.camelspringboot.jmstest;

import javax.jms.ConnectionFactory;

import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.JmsTransactionManager;

@SpringBootApplication
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Value("${jmscomponent.transacted}")
	Boolean jmsComponentTransacted;
	
	@Bean(name="amqp")
	public AMQPComponent getAMQPComponent(@Autowired ConnectionFactory pooledConnectionFactory) {
		AMQPComponent amqpComponent = new AMQPComponent(pooledConnectionFactory);
		amqpComponent.setTransacted(jmsComponentTransacted);
		return amqpComponent;
	}

	//This is a non-pooled ConnectionFactory
//	@Bean
//	public ConnectionFactory amqConnectionFactory(@Value("${amqp.url}") String url, @Value("${amqp.username}")  String username, @Value("${amqp.password}") String password){
//		return new JmsConnectionFactory(username,password,url);
//	}


	@Bean
	@Primary
	public ConnectionFactory pooledConnectionFactory(@Value("${amqp.url}") String url, @Value("${amqp.username}")  String username, @Value("${amqp.password}") String password,
													 @Value("${useCachingConnectionFactory}") Boolean useCachingConnectionFactory, @Value("${sessionCacheSize}") Integer sessionCacheSize, @Value("${jmspool.maxConnections}") Integer maxConnections) {

		if (useCachingConnectionFactory) {

			// Spring CachingConnectionFactory
			CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
			cachingConnectionFactory.setTargetConnectionFactory(new JmsConnectionFactory(username,password,url));
			cachingConnectionFactory.setSessionCacheSize(sessionCacheSize);
			return cachingConnectionFactory;
		} else {
			// MessagingHub JmsPoolConnectionFactory
			JmsPoolConnectionFactory jmsPoolConnectionFactory = new JmsPoolConnectionFactory();
			jmsPoolConnectionFactory.setConnectionFactory(new JmsConnectionFactory(username,password,url));
			jmsPoolConnectionFactory.setMaxConnections(maxConnections);
			return jmsPoolConnectionFactory;
		}

	}

	// @Bean(name="amqp")
	// public Component  amqpComponent(@Autowired ConnectionFactory pooledConnectionFactory) {
	// 	JmsComponent component = JmsComponent.jmsComponent(pooledConnectionFactory);
	// 	return component;
	// }


	@Bean
	public JmsTransactionManager myTransactionManager(@Autowired ConnectionFactory pooledConnectionFactory){
		return new JmsTransactionManager(pooledConnectionFactory);
	}

	@Bean
	public SpringTransactionPolicy jmsSendTransaction(@Autowired JmsTransactionManager jmsTransactionManager, @Value("${receive.forward.propagation}") String transactionPropagation){
		SpringTransactionPolicy transactionPolicy = new SpringTransactionPolicy(jmsTransactionManager);
		transactionPolicy.setPropagationBehaviorName(transactionPropagation);
		return transactionPolicy;
	}
}
