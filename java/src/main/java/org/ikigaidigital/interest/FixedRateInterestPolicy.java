package org.ikigaidigital.interest;

import java.util.function.IntPredicate;

import org.ikigaidigital.TimeDeposit;

public record FixedRateInterestPolicy(
        double annualRate,
        IntPredicate eligibleDays
) implements InterestPolicy {

    @Override
    public double calculateInterest(TimeDeposit deposit) {
        if (!eligibleDays.test(deposit.getDays())) {
            return 0;
        }

        return deposit.getBalance() * annualRate / 12;
    }
}
