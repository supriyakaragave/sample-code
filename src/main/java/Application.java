import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class Application {


    public static void main(String[] args) {


        String input ="ABSCD";
        char[]  ipArray =   input.toCharArray();
        int j = 0;
        int k = ipArray.length;
        char[]  revArray = new char[]{} ;
        for (char i : ipArray){

            revArray[k-1] = ipArray[j];
            j++;
            k--;
            if (j > ipArray.length || k <0){
                return;
            }
        }

       if( revArray.toString().equals(input)){

       }
    }


}
