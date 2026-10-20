package org.ikigaidigital.application.port.out;

import java.util.List;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.model.TimeDepositView;

/**
 * Outbound port: persistence of time deposits.
 *
 * <p>The production adapter owns transactions and locking; the application
 * core never manages database locks itself.</p>
 */
public interface TimeDepositRepository {

    /**
     * Returns the read models of all time deposits, including their
     * withdrawals. An empty repository yields an empty list.
     */
    List<TimeDepositView> findAll();

    /**
     * Returns all time deposits in the domain representation, suitable for
     * the update use case.
     *
     * <p>A production adapter must acquire write locks on all returned
     * deposits (for example row locks via {@code SELECT ... FOR UPDATE})
     * within the transaction that encompasses the entire update use case:
     * loading, calculation, and persistence. Locking is exclusively the
     * adapter's responsibility.</p>
     */
    List<TimeDeposit> findAllForUpdate();

    /**
     * Persists the balances of the given deposits. Implementations persist
     * the balance (and identify the row by id) and must not change days,
     * planType, or withdrawals.
     */
    void updateBalances(List<TimeDeposit> deposits);
}
