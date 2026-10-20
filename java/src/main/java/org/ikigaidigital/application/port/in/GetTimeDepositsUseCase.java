package org.ikigaidigital.application.port.in;

import java.util.List;

import org.ikigaidigital.application.model.TimeDepositView;

/**
 * Inbound port: retrieving all time deposits.
 *
 * <p>Returns the read models as stored; the use case does not recalculate
 * balances and does not interpret plan types.</p>
 */
public interface GetTimeDepositsUseCase {

    /**
     * Returns all time deposits, including their withdrawals.
     * An empty repository yields an empty list.
     */
    List<TimeDepositView> getTimeDeposits();
}
