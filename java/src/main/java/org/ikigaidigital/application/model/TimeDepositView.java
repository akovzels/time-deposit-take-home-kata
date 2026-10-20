package org.ikigaidigital.application.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Application read model of a time deposit, including its withdrawals.
 *
 * <p>This is an application-layer read model, not an HTTP DTO and not a
 * persistence entity; it carries no serialization or ORM annotations.</p>
 */
public record TimeDepositView(
        int id,
        String planType,
        BigDecimal balance,
        int days,
        List<WithdrawalView> withdrawals) {

    public TimeDepositView {
        // Defensive copy: the record must not share collection state with the
        // caller. A null withdrawals list is normalized to an empty list so
        // that "no withdrawals" is always represented by an empty list,
        // never null.
        withdrawals = withdrawals == null ? List.of() : List.copyOf(withdrawals);
    }
}
