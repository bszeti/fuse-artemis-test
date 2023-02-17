package bszeti.camelspringboot.jmstest;

import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.connection.JmsTransactionManager;

import javax.jms.ConnectionFactory;

@SpringBootApplication
public class Application {
	

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	 @Bean(name="amq")
	 public JmsComponent  jmsComponent(@Autowired ConnectionFactory pooledConnectionFactory) {
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

	@Bean 
	public String messageWithSetLenght (@Value("${send.message.length}") Integer sendMessageLength){
		if (sendMessageLength>0) {
            return String.format("%1$"+sendMessageLength+ "s", "").replace(" ","M");
        }
		return "";
	}
}
