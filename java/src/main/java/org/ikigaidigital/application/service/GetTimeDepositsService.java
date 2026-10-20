package org.ikigaidigital.application.service;

import java.util.List;

import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;

/**
 * Implements {@link GetTimeDepositsUseCase}.
 *
 * <p>Returns the repository's read models as stored: no balances are
 * recalculated and no plan types are interpreted.</p>
 */
public class GetTimeDepositsService implements GetTimeDepositsUseCase {

    private final TimeDepositRepository repository;

    public GetTimeDepositsService(TimeDepositRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TimeDepositView> getTimeDeposits() {
        return repository.findAll();
    }
}
