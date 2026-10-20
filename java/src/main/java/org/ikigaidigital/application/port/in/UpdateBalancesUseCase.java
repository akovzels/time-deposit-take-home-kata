package org.ikigaidigital.application.port.in;

/**
 * Inbound port: updating the balances of all time deposits.
 *
 * <p>Each successful invocation applies exactly one calculation (one
 * monthly interest amount per eligible deposit) as determined by the
 * existing {@code TimeDepositCalculator}. The use case is intentionally
 * not idempotent: two invocations apply two calculations. There are no
 * calendar periods, scheduling, or jobs in the application core.</p>
 */
public interface UpdateBalancesUseCase {

    /**
     * Recalculates and persists the balances of all time deposits.
     * An empty repository completes successfully.
     */
    void updateBalances();
}
