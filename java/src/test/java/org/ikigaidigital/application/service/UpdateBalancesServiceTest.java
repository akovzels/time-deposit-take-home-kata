package org.ikigaidigital.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.instancio.Instancio;
import org.instancio.Select;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for {@link UpdateBalancesService} with a mocked
 * {@link TimeDepositRepository} (Mockito) and the REAL
 * {@link TimeDepositCalculator} — interest calculations are never mocked
 * or stubbed.
 *
 * <p>Every test snapshots the deposits submitted to
 * {@code repository.updateBalances()} with a Mockito {@code doAnswer} that
 * copies id, planType, days, and balance into immutable
 * {@code SubmittedDeposit} records at the exact moment of the save, and
 * asserts them as VALUES SUBMITTED FOR PERSISTENCE (raw Double results, no
 * rounding or normalization): a repository mock does not prove what a
 * database actually persists.</p>
 *
 * <p>All monetary expectations are hardcoded decimal strings compared with
 * {@code isEqualByComparingTo} — no tolerance, and no mirrored formula from
 * {@code TimeDepositCalculatorTest} is reused here.</p>
 */
@ExtendWith({MockitoExtension.class, InstancioExtension.class})
public class UpdateBalancesServiceTest {

    @Mock
    private TimeDepositRepository repository;

    private UpdateBalancesService service;

    @BeforeEach
    public void setUp() {
        // Real calculator: interest calculations are never mocked or stubbed.
        service = new UpdateBalancesService(repository, new TimeDepositCalculator());
    }

    /**
     * One updateBalances() invocation over a MIXED collection of eligible,
     * ineligible, and unknown plan types (one stubbed load list).
     *
     * <p>Ids are generated with Instancio: they never participate in the
     * interest calculation, so randomizing them keeps the monetary
     * expectations the only deterministic part of this test.</p>
     *
     * <p>Interest calculations (current Double-based behavior, HALF_UP to 2 dp):</p>
     * <ul>
     *   <li>basic 1000.00 @ 31d: 1000.00 * 0.01 / 12 = 0.8333... -&gt; 0.83 -&gt; 1000.83</li>
     *   <li>student 1000.00 @ 365d: 1000.00 * 0.03 / 12 = 2.50 -&gt; 1002.50</li>
     *   <li>premium 1000.00 @ 46d: 1000.00 * 0.05 / 12 = 4.1666... -&gt; 4.17 -&gt; 1004.17</li>
     *   <li>basic 1000.00 @ 30d: not eligible (days &lt;= 30) -&gt; 1000.00</li>
     *   <li>student 1000.00 @ 366d: not eligible (days &gt;= 366) -&gt; 1000.00</li>
     *   <li>premium 1000.00 @ 45d: not eligible (days &lt;= 45) -&gt; 1000.00</li>
     *   <li>business 1000.00 @ 1000d: unknown plan type, no interest -&gt; 1000.00</li>
     * </ul>
     */
    @Test
    public void updateBalances_MixedEligibleIneligibleAndUnknownPlans_ShouldSubmitCalculatedBalances() {
        int baseId = randomBaseId();
        List<Integer> ids = List.of(
                baseId + 1, baseId + 2, baseId + 3, baseId + 4, baseId + 5, baseId + 6, baseId + 7);
        when(repository.findAllForUpdate()).thenReturn(List.of(
                new TimeDeposit(ids.get(0), "basic", 1000.00, 31),
                new TimeDeposit(ids.get(1), "student", 1000.00, 365),
                new TimeDeposit(ids.get(2), "premium", 1000.00, 46),
                new TimeDeposit(ids.get(3), "basic", 1000.00, 30),
                new TimeDeposit(ids.get(4), "student", 1000.00, 366),
                new TimeDeposit(ids.get(5), "premium", 1000.00, 45),
                new TimeDeposit(ids.get(6), "business", 1000.00, 1000)));

        // Snapshot on save (stubbing is local to this test, strict stubs):
        // the deposits submitted for persistence are copied into immutable
        // SubmittedDeposit records at the exact moment updateBalances() runs.
        List<List<SubmittedDeposit>> submissions = new ArrayList<>();
        doAnswer(snapshotOnSave(submissions)).when(repository).updateBalances(anyList());

        service.updateBalances();

        // Loading precedes saving, each exactly once per service invocation.
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository, times(1)).findAllForUpdate();
        inOrder.verify(repository, times(1)).updateBalances(anyList());

        assertThat(submissions).hasSize(1);
        List<SubmittedDeposit> submitted = submissions.get(0);
        assertThat(submitted).hasSize(7);
        assertThat(submitted).extracting(SubmittedDeposit::id)
                .containsExactlyInAnyOrderElementsOf(ids);

        Map<Integer, Double> submittedBalanceById = new HashMap<>();
        for (SubmittedDeposit deposit : submitted) {
            submittedBalanceById.put(deposit.id(), deposit.balance());
        }

        // VALUES SUBMITTED FOR PERSISTENCE (raw Double results, no rounding
        // or normalization; a repository mock does not prove persisted
        // database values):
        Map<Integer, String> expectedSubmittedBalanceById = Map.of(
                ids.get(0), "1000.83",
                ids.get(1), "1002.50",
                ids.get(2), "1004.17",
                ids.get(3), "1000.00",
                ids.get(4), "1000.00",
                ids.get(5), "1000.00",
                ids.get(6), "1000.00");
        for (Map.Entry<Integer, String> expected : expectedSubmittedBalanceById.entrySet()) {
            assertThat(BigDecimal.valueOf(submittedBalanceById.get(expected.getKey())))
                    .as("value submitted for persistence by deposit %d", expected.getKey())
                    .isEqualByComparingTo(expected.getValue());
        }
    }

    /**
     * The objects submitted for persistence keep their ids, plan types, and
     * days; only the balances demonstrably change (premium 1000.00 @ 46d
     * -&gt; 1004.17 and basic 2000.00 @ 40d -&gt; 2001.67).
     *
     * <p>Non-balance fields are snapshotted BEFORE the service runs: the
     * real calculator mutates the loaded deposits in place, so expectations
     * must not be derived from the objects after mutation.</p>
     *
     * <p>Withdrawals do not exist on the mutable TimeDeposit domain model;
     * withdrawal preservation is covered by the read-model/read-service
     * tests, and actual persistence preservation belongs to future
     * repository integration tests — the repository mock does not prove it.</p>
     */
    @Test
    public void updateBalances_EligibleDeposits_ShouldPreserveIdsPlanTypesAndDaysInSubmittedObjects() {
        TimeDeposit premium = new TimeDeposit(1, "premium", 1000.00, 46);
        TimeDeposit basic = new TimeDeposit(2, "basic", 2000.00, 40);
        List<TimeDeposit> loaded = List.of(premium, basic);

        // Snapshot the original id/planType/days BEFORE executing the service.
        Map<Integer, NonBalanceFields> originalFieldsById = new HashMap<>();
        for (TimeDeposit deposit : loaded) {
            originalFieldsById.put(deposit.getId(), new NonBalanceFields(
                    deposit.getId(), deposit.getPlanType(), deposit.getDays()));
        }

        // Snapshot on save (stubbing is local to this test, strict stubs):
        // the submitted values are copied at the exact moment
        // updateBalances() runs, so the assertions below observe the state
        // at the instant of the save call, immune to any later mutation.
        List<List<SubmittedDeposit>> submissions = new ArrayList<>();
        doAnswer(snapshotOnSave(submissions)).when(repository).updateBalances(anyList());

        when(repository.findAllForUpdate()).thenReturn(loaded);

        service.updateBalances();

        // Loading precedes saving, each exactly once per service invocation.
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository, times(1)).findAllForUpdate();
        inOrder.verify(repository, times(1)).updateBalances(anyList());

        assertThat(submissions).hasSize(1);
        List<SubmittedDeposit> submitted = submissions.get(0);
        assertThat(submitted).extracting(SubmittedDeposit::id).containsExactlyInAnyOrder(1, 2);

        Map<Integer, String> expectedSubmittedBalanceById = Map.of(1, "1004.17", 2, "2001.67");
        for (SubmittedDeposit deposit : submitted) {
            NonBalanceFields original = originalFieldsById.get(deposit.id());
            assertThat(deposit.planType())
                    .as("plan type of deposit %d in the submitted snapshot", deposit.id())
                    .isEqualTo(original.planType);
            assertThat(deposit.days())
                    .as("days of deposit %d in the submitted snapshot", deposit.id())
                    .isEqualTo(original.days);
            // VALUES SUBMITTED FOR PERSISTENCE: the balances demonstrably changed.
            assertThat(BigDecimal.valueOf(deposit.balance()))
                    .as("value submitted for persistence by deposit %d", deposit.id())
                    .isEqualByComparingTo(expectedSubmittedBalanceById.get(deposit.id()));
        }
    }

    /**
     * Empty input: the update completes without exception; loading and saving
     * each occur exactly once (load before save), and the list submitted for
     * persistence is empty.
     */
    @Test
    public void updateBalances_EmptyInput_ShouldCompleteWithoutExceptionAndSaveEmptyListOnce() {
        // Snapshot on save (stubbing is local to this test, strict stubs).
        List<List<SubmittedDeposit>> submissions = new ArrayList<>();
        doAnswer(snapshotOnSave(submissions)).when(repository).updateBalances(anyList());

        when(repository.findAllForUpdate()).thenReturn(List.of());

        assertThatCode(service::updateBalances).doesNotThrowAnyException();

        // Loading precedes saving, each exactly once per service invocation.
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository, times(1)).findAllForUpdate();
        inOrder.verify(repository, times(1)).updateBalances(anyList());

        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0)).isEmpty();
    }

    /**
     * Two successive invocations apply two calculations, on TWO INDEPENDENT
     * input lists (thenReturn(firstLoad, secondLoad) with fresh TimeDeposit
     * objects each, representing the first and second loaded states) — never
     * the same mutable objects for both loads, and no stateful repository
     * inside Mockito answers.
     *
     * <ul>
     *   <li>First load: basic 1200.00 @ 31d -&gt; submitted 1201.00
     *       (1200.00 * 0.01 / 12 = 1.00 exactly).</li>
     *   <li>Second load: basic 1201.00 @ 31d -&gt; submitted 1202.00
     *       (1201.00 * 0.01 / 12 = 1.0008... -&gt; 1.00).</li>
     * </ul>
     */
    @Test
    public void updateBalances_TwoSuccessiveInvocations_ShouldApplyTwoCalculations() {
        List<TimeDeposit> firstLoad = List.of(new TimeDeposit(1, "basic", 1200.00, 31));
        List<TimeDeposit> secondLoad = List.of(new TimeDeposit(1, "basic", 1201.00, 31));
        // Snapshot on save (stubbing is local to this test, strict stubs):
        // one immutable snapshot list per updateBalances() invocation.
        List<List<SubmittedDeposit>> submissions = new ArrayList<>();
        doAnswer(snapshotOnSave(submissions)).when(repository).updateBalances(anyList());

        when(repository.findAllForUpdate()).thenReturn(firstLoad, secondLoad);

        service.updateBalances();
        service.updateBalances();

        // Loading and saving each occur exactly twice.
        verify(repository, times(2)).findAllForUpdate();
        verify(repository, times(2)).updateBalances(anyList());

        assertThat(submissions).hasSize(2);
        // VALUES SUBMITTED FOR PERSISTENCE (raw Double results, no rounding
        // or normalization):
        assertThat(BigDecimal.valueOf(submissions.get(0).get(0).balance()))
                .as("first invocation - value submitted for persistence")
                .isEqualByComparingTo("1201.00");
        assertThat(BigDecimal.valueOf(submissions.get(1).get(0).balance()))
                .as("second invocation - value submitted for persistence")
                .isEqualByComparingTo("1202.00");

        // Loading precedes saving each time: load, save, load, save.
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).findAllForUpdate();
        inOrder.verify(repository).updateBalances(anyList());
        inOrder.verify(repository).findAllForUpdate();
        inOrder.verify(repository).updateBalances(anyList());
    }

    /**
     * The existing student rounding example: student 402.00 @ 31 days
     * produces 403.00 as the value submitted for persistence under the
     * CURRENT Double-based behavior.
     *
     * <p>The interest is computed as a double: 402.00 * 0.03 / 12 yields a
     * double slightly BELOW the exact 1.005, which HALF_UP rounds down to
     * 1.00, giving 403.00. The exact-decimal expectation 403.01
     * (402.00 * 0.03 / 12 = 1.005 -&gt; 1.01) is deliberately documented by
     * the {@code @Disabled} test in {@code TimeDepositCalculatorTest} and
     * remains disabled — it is not touched here.</p>
     */
    @Test
    public void updateBalances_StudentRoundingExample_ShouldMatchCurrentDoubleBehavior() {
        // Snapshot on save (stubbing is local to this test, strict stubs).
        List<List<SubmittedDeposit>> submissions = new ArrayList<>();
        doAnswer(snapshotOnSave(submissions)).when(repository).updateBalances(anyList());

        when(repository.findAllForUpdate())
                .thenReturn(List.of(new TimeDeposit(1, "student", 402.00, 31)));

        service.updateBalances();

        // Loading precedes saving, each exactly once per service invocation.
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository, times(1)).findAllForUpdate();
        inOrder.verify(repository, times(1)).updateBalances(anyList());

        assertThat(submissions).hasSize(1);
        List<SubmittedDeposit> submitted = submissions.get(0);
        assertThat(submitted).hasSize(1);
        // VALUE SUBMITTED FOR PERSISTENCE (current Double-based behavior).
        assertThat(BigDecimal.valueOf(submitted.get(0).balance()))
                .isEqualByComparingTo("403.00");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Random base id generated with Instancio (ids never participate in
     * interest calculations); distinct offsets keep the derived ids unique.
     */
    private static int randomBaseId() {
        return Instancio.of(Integer.class)
                .generate(Select.root(), gen -> gen.ints().range(1, 1_000_000))
                .create();
    }

    /**
     * Returns a Mockito answer that snapshots the deposits submitted to
     * {@code repository.updateBalances()} the moment the save is invoked:
     * the invocation's list argument is copied into immutable
     * {@link SubmittedDeposit} records (never references to the mutable
     * {@link TimeDeposit} objects), and each invocation's snapshot list is
     * collected into {@code submissions}.
     *
     * <p>WHY: an ArgumentCaptor would retain REFERENCES to the mutable
     * TimeDeposit domain objects, which the real calculator mutates in
     * place, so assertions made after the service ran would inspect the
     * final object state and could pass even if saving had happened BEFORE
     * the calculation. The doAnswer snapshots capture the submitted values
     * at the instant of the call, making the assertions immune to any later
     * mutation of the domain objects.</p>
     */
    private static Answer<Void> snapshotOnSave(List<List<SubmittedDeposit>> submissions) {
        return invocation -> {
            List<TimeDeposit> submittedAtCallTime = invocation.getArgument(0);
            List<SubmittedDeposit> snapshot = new ArrayList<>(submittedAtCallTime.size());
            for (TimeDeposit deposit : submittedAtCallTime) {
                snapshot.add(new SubmittedDeposit(deposit.getId(), deposit.getPlanType(),
                        deposit.getDays(), deposit.getBalance()));
            }
            submissions.add(List.copyOf(snapshot));
            return null;
        };
    }

    /** Immutable snapshot of one deposit exactly as submitted for persistence, copied at the moment of the save call. */
    private record SubmittedDeposit(int id, String planType, int days, double balance) {
    }

    /** Immutable snapshot of the non-balance fields of one input deposit. */
    private record NonBalanceFields(int id, String planType, int days) {
    }
}
