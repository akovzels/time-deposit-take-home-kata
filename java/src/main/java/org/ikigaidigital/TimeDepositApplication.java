package org.ikigaidigital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap class for the Spring Boot application.
 *
 * <p>Only framework bootstrap lives here: no HTTP endpoints, persistence, or
 * application-service beans are registered yet. The application core (ports,
 * services, read models, {@link TimeDepositCalculator}, and the interest
 * policies) stays framework-independent and unchanged; its production
 * repository adapter does not exist yet, so no application beans are wired.</p>
 */
@SpringBootApplication
public class TimeDepositApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeDepositApplication.class, args);
    }
}
