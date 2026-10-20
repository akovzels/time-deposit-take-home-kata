package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.ikigaidigital.interest.FixedRateInterestPolicy;
import org.ikigaidigital.interest.InterestPolicy;

public class TimeDepositCalculator {
    private static final InterestPolicy NO_INTEREST = deposit -> 0;

    private final Map<String, InterestPolicy> policies;

    public TimeDepositCalculator() {
        this(Map.of(
                PlanTypes.BASIC, new FixedRateInterestPolicy(0.01, days -> days > 30),
                PlanTypes.STUDENT, new FixedRateInterestPolicy(0.03, days -> days > 30 && days < 366),
                PlanTypes.PREMIUM, new FixedRateInterestPolicy(0.05, days -> days > 45)
        ));
    }

    public TimeDepositCalculator(Map<String, InterestPolicy> policies) {
        this.policies = Map.copyOf(policies);
    }

    public void updateBalance(List<TimeDeposit> deposits) {
        for (TimeDeposit deposit : deposits) {
            InterestPolicy policy = policies.getOrDefault(deposit.getPlanType(), NO_INTEREST);
            double interest = policy.calculateInterest(deposit);

            // Preserve the original double-to-decimal rounding behavior during refactoring.
            double roundedInterest = new BigDecimal(interest)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            deposit.setBalance(deposit.getBalance() + roundedInterest);
        }
    }
}
