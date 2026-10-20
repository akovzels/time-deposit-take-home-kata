package org.ikigaidigital.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Application read model of a withdrawal.
 *
 * <p>This is an application-layer read model, not an HTTP DTO and not a
 * persistence entity; it carries no serialization or ORM annotations.</p>
 */
public record WithdrawalView(
        int id,
        int timeDepositId,
        BigDecimal amount,
        LocalDate date) {
}
