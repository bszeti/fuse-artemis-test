package bszeti.camelspringboot.jmstest;

import javax.jms.ConnectionFactory;

import org.apache.camel.Component;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import static org.springframework.jms.listener.DefaultMessageListenerContainer.*;

@SpringBootApplication
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

//	@Bean(name="amqp")
//	public AMQPComponent getAMQPComponent(@Value("${amqp.url}") String url, @Value("${amqp.username}")  String username, @Value("${amqp.password}") String password) {
//		AMQPComponent amqpComponent = AMQPComponent.amqpComponent(url, username, password);
////		amqpComponent.setTransacted(true);
////		amqpComponent.setCacheLevel(CACHE_CONSUMER); //TODO: Move to camel from url?
//		return amqpComponent;
//	}

	@Bean
	@Primary
	public ConnectionFactory amqConnectionFactory(@Value("${amqp.url}") String url, @Value("${amqp.username}")  String username, @Value("${amqp.password}") String password){
		return new JmsConnectionFactory(username,password,url);
	}


	@Bean
	public ConnectionFactory getPoolConnectionFactory(@Value("${useCachingConnectionFactory}") Boolean useCachingConnectionFactory) {

		if (useCachingConnectionFactory) {
			// Spring CachingConnectionFactory
			CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
			cachingConnectionFactory.setTargetConnectionFactory(amqConnectionFactory(null,null,null));
			return cachingConnectionFactory;
		} else {
			// MessagingHub JmsPoolConnectionFactory
			JmsPoolConnectionFactory jmsPoolConnectionFactory = new JmsPoolConnectionFactory();
			jmsPoolConnectionFactory.setConnectionFactory(amqConnectionFactory(null, null, null));
			jmsPoolConnectionFactory.setMaxConnections(1);
			return jmsPoolConnectionFactory;
		}
	}

	@Bean(name="amqp")
	public Component  amqpComponent() {
		JmsComponent component = JmsComponent.jmsComponent(getPoolConnectionFactory(null));
		return component;
	}


}
