package org.ikigaidigital.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.instancio.Select.root;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.model.WithdrawalView;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GetTimeDepositsService} with a mocked
 * {@link TimeDepositRepository} (Mockito) and Instancio-generated read models.
 *
 * <p>The read use case returns the repository's read models as stored: no
 * balances are recalculated and no plan types are interpreted, so every
 * record must come back exactly as supplied, and the read path must never
 * call a write port method.</p>
 */
@ExtendWith({MockitoExtension.class, InstancioExtension.class})
public class GetTimeDepositsServiceTest {

    /** Known plan types plus arbitrary unknown ones; the read service interprets none of them. */
    private static final String[] PLAN_TYPES = {"basic", "student", "premium", "business", "pension"};

    @Mock
    private TimeDepositRepository repository;

    @Test
    public void getTimeDeposits_EmptyRepository_ShouldReturnEmptyList() {
        when(repository.findAll()).thenReturn(List.of());
        GetTimeDepositsService service = new GetTimeDepositsService(repository);

        assertThat(service.getTimeDeposits()).isEmpty();

        // The read path is load-only: findAll() exactly once, no write method ever.
        verify(repository, times(1)).findAll();
        verify(repository, never()).findAllForUpdate();
        verify(repository, never()).updateBalances(anyList());
        verifyNoMoreInteractions(repository);
    }

    /**
     * Deposits with and without withdrawals must come back exactly as
     * supplied by the repository.
     *
     * <p>The read fixtures are generated with Instancio (the seed is
     * reported on failure by InstancioExtension) and are deliberately
     * random, because the read service must not interpret any of them:
     * deposit ids derive from one random base id with distinct offsets, so
     * they are random but unique; plan types are picked from a mix of known
     * and unknown values; days are non-boundary; balances and withdrawal
     * amounts are whole cents as BigDecimal scale 2; withdrawal dates are
     * non-null past dates; every withdrawal's timeDepositId matches its
     * owning deposit's id; the deposit without withdrawals gets an empty
     * list, never null.</p>
     */
    @Test
    public void getTimeDeposits_WithAndWithoutWithdrawals_ShouldReturnReadModelsExactlyAsSupplied() {
        int baseId = Instancio.of(Integer.class)
                .generate(root(), gen -> gen.ints().range(1, 1_000_000))
                .create();
        TimeDepositView withTwoWithdrawals = randomReadModel(baseId + 1, 2);
        TimeDepositView withOneWithdrawal = randomReadModel(baseId + 2, 1);
        TimeDepositView withoutWithdrawals = randomReadModel(baseId + 3, 0);
        List<TimeDepositView> supplied = List.of(
                withTwoWithdrawals, withOneWithdrawal, withoutWithdrawals);

        when(repository.findAll()).thenReturn(supplied);
        GetTimeDepositsService service = new GetTimeDepositsService(repository);

        List<TimeDepositView> returned = service.getTimeDeposits();

        // The whole list and each returned record equal the supplied ones
        // (record value equality; no field is re-asserted individually).
        assertThat(returned).isEqualTo(supplied);

        // Fixture guarantees: unique deposit ids, consistent withdrawal
        // foreign keys, and an empty (never null) withdrawals list for the
        // deposit without withdrawals.
        assertThat(supplied).extracting(TimeDepositView::id).doesNotHaveDuplicates();
        assertThat(withTwoWithdrawals.withdrawals())
                .extracting(WithdrawalView::timeDepositId).containsOnly(baseId + 1);
        assertThat(withOneWithdrawal.withdrawals())
                .extracting(WithdrawalView::timeDepositId).containsOnly(baseId + 2);
        assertThat(withoutWithdrawals.withdrawals()).isNotNull().isEmpty();

        // The read path is load-only: findAll() exactly once, no write method ever.
        verify(repository, times(1)).findAll();
        verify(repository, never()).findAllForUpdate();
        verify(repository, never()).updateBalances(anyList());
        verifyNoMoreInteractions(repository);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * One Instancio-generated read model with the given id and withdrawal
     * count. All values may be random because the read service must not
     * interpret them: plan type from known and unknown values, non-boundary
     * days, a whole-cent balance as BigDecimal scale 2, non-null past
     * withdrawal dates, and withdrawal foreign keys tied to the owning id.
     */
    private static TimeDepositView randomReadModel(int id, int withdrawalCount) {
        TimeDepositView skeleton = Instancio.of(TimeDepositView.class)
                .set(field(TimeDepositView::id), id)
                .set(field(TimeDepositView::withdrawals), List.of())
                .generate(field(TimeDepositView::planType), gen -> gen.oneOf(PLAN_TYPES))
                .generate(field(TimeDepositView::days), gen -> gen.ints().range(1, 5_000))
                .supply(field(TimeDepositView::balance),
                        random -> BigDecimal.valueOf(random.longRange(1, 100_000_000), 2))
                .create();

        List<WithdrawalView> withdrawals = new ArrayList<>(withdrawalCount);
        for (int i = 0; i < withdrawalCount; i++) {
            withdrawals.add(Instancio.of(WithdrawalView.class)
                    // Withdrawal ids derive from the deposit id: unique as well.
                    .set(field(WithdrawalView::id), id * 1_000 + i)
                    // Consistent foreign key: the withdrawal belongs to this deposit.
                    .set(field(WithdrawalView::timeDepositId), id)
                    .supply(field(WithdrawalView::amount),
                            random -> BigDecimal.valueOf(random.longRange(1, 10_000_000), 2))
                    .generate(field(WithdrawalView::date), gen -> gen.temporal().localDate().past())
                    .create());
        }
        return new TimeDepositView(
                id, skeleton.planType(), skeleton.balance(), skeleton.days(), withdrawals);
    }
}
