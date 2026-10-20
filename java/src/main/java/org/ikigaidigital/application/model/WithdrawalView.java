package org.ikigaidigital.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Application read model of a withdrawal.
 *
 * <p>Owned by the application core; carries no serialization or ORM
 * annotations. It is reused in the HTTP response (inside
 * {@link TimeDepositView}) only because the OpenAPI contract is currently
 * isomorphic to this model — if the contract diverges, the web adapter must
 * introduce its own DTO rather than reshaping this model.</p>
 */
public record WithdrawalView(
        int id,
        int timeDepositId,
        BigDecimal amount,
        LocalDate date) {
}
