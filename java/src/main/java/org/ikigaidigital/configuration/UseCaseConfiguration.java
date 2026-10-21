package org.ikigaidigital.configuration;

import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.adapter.transaction.TransactionalUpdateBalancesUseCase;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateBalancesUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.service.GetTimeDepositsService;
import org.ikigaidigital.application.service.UpdateBalancesService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires application use cases to their adapters. 
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    public GetTimeDepositsUseCase getTimeDepositsUseCase(TimeDepositRepository repository) {
        return new GetTimeDepositsService(repository);
    }

    @Bean
    public UpdateBalancesUseCase updateBalancesUseCase(
            TimeDepositRepository repository, PlatformTransactionManager transactionManager) {
        return new TransactionalUpdateBalancesUseCase(
                new UpdateBalancesService(repository, new TimeDepositCalculator()), transactionManager);
    }
}
