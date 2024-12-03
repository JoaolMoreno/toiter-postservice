package com.toiter.postservice;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ToiterPostserviceApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        // Verifica a variável de ambiente FEATURE_FLAG_EXIT
        String featureFlagExit = dotenv.get("FEATURE_FLAG_EXIT");
        if ("true".equalsIgnoreCase(featureFlagExit)) {
            System.err.println("Feature flag exit is enabled. Exiting application.");
            System.exit(1);
        }
        SpringApplication.run(ToiterPostserviceApplication.class, args);
    }

}
