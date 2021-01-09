package bszeti.camelspringboot.jmstest;

import javax.jms.ConnectionFactory;

import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.jms.JmsComponent;
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

//	@Bean(name="amqp")
//	public AMQPComponent getAMQPComponent(@Autowired ConnectionFactory pooledConnectionFactory) {
//		AMQPComponent amqpComponent = new AMQPComponent(pooledConnectionFactory);
//		return amqpComponent;
//	}


	 @Bean(name="amqp")
	 public JmsComponent  amqpComponent(@Autowired ConnectionFactory pooledConnectionFactory) {
	 	JmsComponent component = JmsComponent.jmsComponent(pooledConnectionFactory);
	 	return component;
	 }


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
