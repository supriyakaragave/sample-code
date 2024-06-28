import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class Application {


    public static void main(String[] args) {
       // SpringApplication.run(VehicleInformationUpdateApplication.class,args);


        final ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);

        final AtomicInteger counter = new AtomicInteger(0);

        Arrays.asList(context.getBeanDefinitionNames())
                .forEach(beanName -> {
                    System.out.println("{}) Bean Name: {} "+ counter.incrementAndGet() + "\t" + beanName);
                });

    }


}
