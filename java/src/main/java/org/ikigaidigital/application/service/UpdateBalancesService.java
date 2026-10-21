package org.ikigaidigital.application.service;

import java.util.List;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.UpdateBalancesUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;

/**
 * Implements {@link UpdateBalancesUseCase}.
 *
 * <p>Sequence per invocation: load all deposits through
 * {@link TimeDepositRepository#findAllForUpdate()}, call the injected
 * {@link TimeDepositCalculator#updateBalance(List)} exactly once with the
 * complete list, then persist the balances through
 * {@link TimeDepositRepository#updateBalances(List)}.</p>
 *
 * <p>Transaction boundary (adapter responsibility, not implemented here):
 * the production adapter must wrap loading, calculation, and
 * persistence in a single transaction, with write locks acquired in
 * {@code findAllForUpdate()}. The application core manages neither
 * transactions nor locks.</p>
 */
public class UpdateBalancesService implements UpdateBalancesUseCase {

    private final TimeDepositRepository repository;
    private final TimeDepositCalculator calculator;

    public UpdateBalancesService(TimeDepositRepository repository, TimeDepositCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    public void updateBalances() {
        List<TimeDeposit> deposits = repository.findAllForUpdate();
        calculator.updateBalance(deposits);
        repository.updateBalances(deposits);
    }
}
