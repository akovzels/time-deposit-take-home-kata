package org.ikigaidigital.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for the read-model contract of {@link TimeDepositView}:
 * defensive copies of the withdrawals and normalization of null to an
 * empty list.
 */
public class TimeDepositViewTest {

    @Test
    public void constructor_MutatingSourceWithdrawalsList_ShouldNotAffectTheView() {
        List<WithdrawalView> source = new ArrayList<>();
        source.add(new WithdrawalView(1, 1, new BigDecimal("10.00"), null));

        TimeDepositView view = new TimeDepositView(1, "basic", new BigDecimal("100.00"), 31, source);

        // Mutate the list that was passed to the constructor after construction.
        source.add(new WithdrawalView(2, 1, new BigDecimal("20.00"), null));

        assertThat(view.withdrawals()).hasSize(1);
        assertThat(view.withdrawals().get(0).id()).isEqualTo(1);
        assertThat(view.withdrawals().get(0).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    public void withdrawals_ReturnedListIsUnmodifiable() {
        WithdrawalView withdrawal = new WithdrawalView(1, 1, new BigDecimal("10.00"), null);
        TimeDepositView view = new TimeDepositView(
                1, "basic", new BigDecimal("100.00"), 31, List.of(withdrawal));

        assertThatThrownBy(() -> view.withdrawals().add(withdrawal))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void constructor_NullWithdrawals_ShouldNormalizeToEmptyList() {
        TimeDepositView view = new TimeDepositView(1, "basic", new BigDecimal("100.00"), 31, null);

        assertThat(view.withdrawals()).isNotNull().isEmpty();
    }
}
