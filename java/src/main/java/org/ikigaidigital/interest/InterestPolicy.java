package org.ikigaidigital.interest;

import org.ikigaidigital.TimeDeposit;

@FunctionalInterface
public interface InterestPolicy {
    /** Returns unrounded monthly interest, or zero when the deposit is ineligible. */
    double calculateInterest(TimeDeposit deposit);
}
