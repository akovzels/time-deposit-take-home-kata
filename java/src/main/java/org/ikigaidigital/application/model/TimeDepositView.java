package org.ikigaidigital.application.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Application read model of a time deposit, including its withdrawals.
 *
 * <p>Owned by the application core; carries no serialization or ORM
 * annotations. The HTTP adapter reuses it as the response representation
 * only because the OpenAPI contract is currently isomorphic to this
 * model — if the contract diverges, the web adapter must introduce its
 * own DTO rather than reshaping this model.</p>
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
