package org.ikigaidigital.application.port.in;

import java.util.List;

import org.ikigaidigital.application.model.TimeDepositView;

/**
 * Inbound port: retrieving all time deposits.
 */
public interface GetTimeDepositsUseCase {

    /**
     * Returns all time deposits, including their withdrawals.
     * An empty repository yields an empty list.
     */
    List<TimeDepositView> getTimeDeposits();
}
