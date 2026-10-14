package com.forcitis.one;

import com.forcitis.one.kernel.UtcJvm;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OneApplication {

    public static void main(String[] args) {
        UtcJvm.installAsDefault();
        SpringApplication.run(OneApplication.class, args);
    }
}
