package org.ikigaidigital.adapter.transaction;

import org.ikigaidigital.application.port.in.UpdateBalancesUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Keeps row locks until loading, calculation, and persistence complete. */
public class TransactionalUpdateBalancesUseCase implements UpdateBalancesUseCase {

    private final UpdateBalancesUseCase delegate;
    private final TransactionTemplate transaction;

    public TransactionalUpdateBalancesUseCase(
            UpdateBalancesUseCase delegate, PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void updateBalances() {
        transaction.executeWithoutResult(status -> delegate.updateBalances());
    }
}
