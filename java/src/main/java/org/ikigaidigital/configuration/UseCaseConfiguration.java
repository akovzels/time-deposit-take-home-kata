package org.ikigaidigital.configuration;

import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.service.GetTimeDepositsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires application use cases to their adapters. 
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    public GetTimeDepositsUseCase getTimeDepositsUseCase(TimeDepositRepository repository) {
        return new GetTimeDepositsService(repository);
    }
}
