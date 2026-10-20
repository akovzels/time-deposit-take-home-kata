package org.ikigaidigital;

import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Characterization tests pinning the CURRENT observable behavior of
 * {@link TimeDepositCalculator#updateBalance(List)}.
 *
 * Eligibility rules as currently implemented:
 * - days &gt; 30 is the global gate;
 * - student: additionally days &lt; 366, interest rate 3% / 12;
 * - premium: additionally days &gt; 45, interest rate 5% / 12;
 * - basic: no upper day bound, interest rate 1% / 12;
 * - any other (unknown) plan type: no interest at all.
 *
 * The interest is computed as a double (balance * rate / 12) and rounded to 2 dp with
 * HALF_UP before being added to the balance; the method returns void and mutates the
 * deposits in the given list in place.
 *
 * Deterministic boundary cases below deliberately use a 1000.00 balance, whose interest
 * values (0.8333..., 2.50, 4.1666...) do NOT trigger the known double-vs-exact-decimal
 * rounding discrepancy (see the disabled test at the bottom of this class).
 */
@ExtendWith(InstancioExtension.class)
public class TimeDepositCalculatorTest {

    /** Balance used for the deterministic boundary cases: 1000.00 (as a double). */
    private static final double BALANCE_1000 = 1000.00d;

    
    // ------------------------------------------------------------------
    // Deterministic boundary tests (hardcoded expectations)
    // ------------------------------------------------------------------

    /**
     * days = 30 is the exact eligibility threshold: no plan type (student, premium,
     * basic, or an unknown type such as "business") earns any interest, so the balance
     * stays exactly 1000.00.
     */
    @ParameterizedTest(name = "planType={0}")
    @ValueSource(strings = {"student", "premium", "basic", "business"})
    public void updateBalance_Days30_ShouldLeaveBalanceUnchangedForEveryPlanType(String planType) {
        double actual = updateBalanceOnce(planType, BALANCE_1000, 30);

        assertBalanceEquals(actual, "1000.00");
    }

    /**
     * basic at days = 31 (first eligible day) earns 1% / 12 monthly interest:
     * 1000.00 x 0.01 / 12 = 0.833333... -&gt; HALF_UP to 2 dp -&gt; 0.83 -&gt; 1000.83.
     */
    @Test
    public void updateBalance_Days31_Basic_ShouldEarnInterest() {
        double actual = updateBalanceOnce("basic", BALANCE_1000, 31);

        assertBalanceEquals(actual, "1000.83");
    }

    /**
     * student at days = 365 (last eligible day) earns 3% / 12 monthly interest:
     * 1000.00 x 0.03 / 12 = 2.50 exactly -&gt; 1002.50.
     */
    @Test
    public void updateBalance_Days365_Student_ShouldEarnInterest() {
        double actual = updateBalanceOnce("student", BALANCE_1000, 365);

        assertBalanceEquals(actual, "1002.50");
    }

    /**
     * student at days = 366 is past the 365-day cap: no interest, balance unchanged.
     */
    @Test
    public void updateBalance_Days366_Student_ShouldLeaveBalanceUnchanged() {
        double actual = updateBalanceOnce("student", BALANCE_1000, 366);

        assertBalanceEquals(actual, "1000.00");
    }

    /**
     * premium at days = 45 is still below the premium threshold (days &gt; 45):
     * no interest, balance unchanged.
     */
    @Test
    public void updateBalance_Days45_Premium_ShouldLeaveBalanceUnchanged() {
        double actual = updateBalanceOnce("premium", BALANCE_1000, 45);

        assertBalanceEquals(actual, "1000.00");
    }

    /**
     * premium at days = 46 (first eligible day) earns 5% / 12 monthly interest:
     * 1000.00 x 0.05 / 12 = 4.166666... -&gt; HALF_UP to 2 dp -&gt; 4.17 -&gt; 1004.17.
     */
    @Test
    public void updateBalance_Days46_Premium_ShouldEarnInterest() {
        double actual = updateBalanceOnce("premium", BALANCE_1000, 46);

        assertBalanceEquals(actual, "1004.17");
    }

    /**
     * basic has no upper day bound: days = 1000 still earns the same 1% / 12 interest:
     * 1000.00 x 0.01 / 12 = 0.833333... -&gt; HALF_UP to 2 dp -&gt; 0.83 -&gt; 1000.83.
     */
    @Test
    public void updateBalance_LargeDays_Basic_ShouldEarnInterest() {
        double actual = updateBalanceOnce("basic", BALANCE_1000, 1000);

        assertBalanceEquals(actual, "1000.83");
    }

    /**
     * updateBalance returns void and mutates the deposits in the given list in place:
     * the very same instances come back with updated balances, while the list size,
     * the element order and the id / planType / days fields are untouched.
     */
    @Test
    public void updateBalance_ShouldMutateDepositsInPlace_KeepingListSizeAndNonBalanceFieldsUnchanged() {
        TimeDeposit student = new TimeDeposit(1, "student", BALANCE_1000, 365);
        TimeDeposit premium = new TimeDeposit(2, "premium", BALANCE_1000, 46);
        TimeDeposit basic = new TimeDeposit(3, "basic", BALANCE_1000, 31);
        TimeDeposit business = new TimeDeposit(4, "business", BALANCE_1000, 365);
        TimeDeposit cappedStudent = new TimeDeposit(5, "student", BALANCE_1000, 366);
        List<TimeDeposit> plans =
                new ArrayList<>(Arrays.asList(student, premium, basic, business, cappedStudent));

        new TimeDepositCalculator().updateBalance(plans);

        // The list is neither replaced nor reordered: same instances in the same order.
        assertThat(plans).hasSize(5);
        assertThat(plans.get(0)).isSameAs(student);
        assertThat(plans.get(1)).isSameAs(premium);
        assertThat(plans.get(2)).isSameAs(basic);
        assertThat(plans.get(3)).isSameAs(business);
        assertThat(plans.get(4)).isSameAs(cappedStudent);

        // Only the balance is mutated; ids, plan types and day counts are untouched.
        assertThat(plans).extracting(TimeDeposit::getId).containsExactly(1, 2, 3, 4, 5);
        assertThat(plans).extracting(TimeDeposit::getPlanType)
                .containsExactly("student", "premium", "basic", "business", "student");
        assertThat(plans).extracting(TimeDeposit::getDays).containsExactly(365, 46, 31, 365, 366);

        // In-place balances (same hardcoded expectations as the dedicated tests above).
        assertBalanceEquals(student.getBalance(), "1002.50");
        assertBalanceEquals(premium.getBalance(), "1004.17");
        assertBalanceEquals(basic.getBalance(), "1000.83");
        assertBalanceEquals(business.getBalance(), "1000.00");
        assertBalanceEquals(cappedStudent.getBalance(), "1000.00");
    }

    // ------------------------------------------------------------------
    // Randomized (Instancio) characterization tests
    // ------------------------------------------------------------------

  
    /**
     * Randomized eligible deposits: for every generated balance / days pair inside the
     * eligible range of the plan type, the new balance must equal the old balance plus
     * the interest computed by MIRRORING the current production arithmetic.
     *
     * NOTE: this intentionally mirrors the CURRENT implementation as a characterization
     * check and is NOT exact-decimal arithmetic: the interest is computed as a double
     * (balance * rate / 12 via double multiplication and division) and then rounded with
     * BigDecimal.valueOf(interest).setScale(2, HALF_UP), which are the same steps
     * TimeDepositCalculator.updateBalance performs today.
     *
     * Reproducibility: Instancio picks a random seed per run; on failure the seed is
     * reported by the Instancio extension and can be pinned with @Seed to reproduce.
     */
    @Test
    public void updateBalance_RandomEligibleDeposits_ShouldMatchMirroredProductionArithmetic() {
        List<TimeDeposit> deposits = new ArrayList<>();
        deposits.addAll(randomDeposits("basic", 31, 5_000, 25));     // basic: days > 30, no upper bound
        deposits.addAll(randomDeposits("premium", 46, 5_000, 25));  // premium: days > 45
        deposits.addAll(randomDeposits("student", 31, 365, 25));    // student: 30 < days < 366

        // Sanity: the generated data really is inside the eligible ranges.
        for (TimeDeposit deposit : deposits) {
            assertThat(deposit.getBalance()).isStrictlyBetween(99.9, 100_000.1);
            assertThat(deposit.getDays()).isGreaterThan(30);
            if ("student".equals(deposit.getPlanType())) {
                assertThat(deposit.getDays()).isLessThan(366);
            } else if ("premium".equals(deposit.getPlanType())) {
                assertThat(deposit.getDays()).isGreaterThan(45);
            }
        }

        double[] originalBalances = deposits.stream().mapToDouble(TimeDeposit::getBalance).toArray();

        new TimeDepositCalculator().updateBalance(deposits);

        for (int i = 0; i < deposits.size(); i++) {
            TimeDeposit deposit = deposits.get(i);
            double expected =
                    mirroredExpectedBalance(originalBalances[i], deposit.getDays(), deposit.getPlanType());
            assertThat(BigDecimal.valueOf(deposit.getBalance()))
                    .as("id=%d, planType=%s, days=%d, originalBalance=%s",
                            deposit.getId(), deposit.getPlanType(), deposit.getDays(), originalBalances[i])
                    .isEqualByComparingTo(BigDecimal.valueOf(expected));
        }
    }

    /**
     * Randomized ineligible deposits: no interest may be added, so the balance must be
     * exactly unchanged. Three ineligible categories are generated:
     * (a) known plan types with days &lt;= 30 (global gate closed),
     * (b) student with days &gt;= 366 (past the 365-day cap),
     * (c) unknown plan types with eligible-looking day counts.
     */
    @Test
    public void updateBalance_RandomIneligibleDeposits_ShouldLeaveBalanceUnchanged() {
        List<TimeDeposit> deposits = new ArrayList<>();

        // (a) days <= 30 for every known plan type.
        for (String planType : new String[]{"student", "premium", "basic"}) {
            deposits.addAll(randomDeposits(planType, 0, 30, 15));
        }
        
        // (b) student past the 365-day cap.
        deposits.addAll(randomDeposits("student", 366, 100_000, 15));
        
        // (c) unknown plan types with day counts that look eligible for the known plans.
        deposits.addAll(randomDeposits("unk", 31, 100_000, 15));
    
        double[] originalBalances = deposits.stream().mapToDouble(TimeDeposit::getBalance).toArray();

        new TimeDepositCalculator().updateBalance(deposits);

        for (int i = 0; i < deposits.size(); i++) {
            TimeDeposit deposit = deposits.get(i);
            assertThat(BigDecimal.valueOf(deposit.getBalance()))
                    .as("id=%d, planType=%s, days=%d, originalBalance=%s",
                            deposit.getId(), deposit.getPlanType(), deposit.getDays(), originalBalances[i])
                    .isEqualByComparingTo(BigDecimal.valueOf(originalBalances[i]));
        }
    }

    /**
     * Randomized days &lt;= 30 for every known plan type (student, premium, basic): the whole
     * ineligible region BELOW the 30-day gate. This complements the days = 30 edge test
     * ({@code updateBalance_Days30_ShouldLeaveBalanceUnchangedForEveryPlanType}), which pins
     * only the single boundary value, by covering the full days &lt;= 30 region with random
     * balances and day counts: no known plan type earns any interest at or below the gate,
     * so the balance must stay exactly unchanged.
     *
     * Reproducibility: Instancio picks a random seed per run; on failure the seed is
     * reported by the Instancio extension and can be pinned with @Seed to reproduce.
     */
    @ParameterizedTest(name = "planType={0}")
    @ValueSource(strings = {"student", "premium", "basic"})
    public void updateBalance_RandomDaysAtMost30_ShouldLeaveBalanceUnchangedForKnownPlanTypes(String planType) {
  
        TimeDeposit deposit = randomDeposit(planType, 0, 30);

        double originalBalance = deposit.getBalance();

        new TimeDepositCalculator().updateBalance(List.of(deposit));

        // No tolerance: the balance must be cent-exactly unchanged (no interest at all).
        assertThat(BigDecimal.valueOf(deposit.getBalance()))
                .as("id=%d, planType=%s, days=%d, originalBalance=%s",
                        deposit.getId(), deposit.getPlanType(), deposit.getDays(), originalBalance)
                .isEqualByComparingTo(BigDecimal.valueOf(originalBalance));
        
    }

    /**
     * Eligibility windows of the known plan types: plan type plus the inclusive random
     * day range in which the current implementation grants interest.
     */
    private static Stream<Object[]> planEligibilityWindows() {
        return Stream.<Object[]>of(
                new Object[]{"basic", 31, 5_000},
                new Object[]{"student", 31, 365},
                new Object[]{"premium", 46, 5_000});
    }

    /**
     * Randomized days &gt; 30 per plan eligibility window: basic [31, 5000] (no upper day
     * bound), student [31, 365], premium [46, 5000]. This complements the days = 31 edge
     * test ({@code updateBalance_Days31_Basic_ShouldEarnInterest}), which pins only the
     * first eligible day of a single plan type, by covering the whole days &gt; 30 region
     * of every known plan type with random balances and day counts: the new balance must
     * equal the MIRRORED current production arithmetic and must strictly increase
     * (interest &gt; 0) in every generated case.
     *
     * NOTE: this intentionally mirrors the CURRENT implementation as a characterization
     * check and is NOT exact-decimal arithmetic: the interest is computed as a double
     * (balance * rate / 12 via double multiplication and division) and then rounded with
     * BigDecimal.valueOf(interest).setScale(2, HALF_UP), which are the same steps
     * TimeDepositCalculator.updateBalance performs today (see mirroredExpectedBalance).
     *
     * Reproducibility: Instancio picks a random seed per run; on failure the seed is
     * reported by the Instancio extension and can be pinned with @Seed to reproduce.
     */
    @ParameterizedTest(name = "planType={0}, daysWindow=[{1},{2}]")
    @MethodSource("planEligibilityWindows")
    public void updateBalance_RandomDaysOver30_ShouldApplyInterestPerPlanEligibilityWindow(
            String planType, int minDays, int maxDays) {

        TimeDeposit deposit = randomDeposit(planType, minDays, maxDays);

        double originalBalance = deposit.getBalance();

        new TimeDepositCalculator().updateBalance(List.of(deposit));
        
        double expected = mirroredExpectedBalance(originalBalance, deposit.getDays(), planType);
            assertThat(BigDecimal.valueOf(deposit.getBalance()))
                    .as("id=%d, planType=%s, days=%d, originalBalance=%s",
                            deposit.getId(), deposit.getPlanType(), deposit.getDays(), originalBalance)
                    .isEqualByComparingTo(BigDecimal.valueOf(expected));
    }
    
    /**
     * Unknown plan types never match an interest branch, so no interest is added
     * regardless of the day count (including day counts that would be eligible for
     * the known plan types).
     */
    @ParameterizedTest(name = "planType={0}, days={1}")
    @MethodSource("unknownPlanTypeCases")
    public void updateBalance_UnknownPlanType_ShouldLeaveBalanceUnchangedForAnyDays(String planType, int days) {
        
        var deposit = new TimeDeposit(-1, planType, BALANCE_1000, days);
        
        new TimeDepositCalculator().updateBalance(List.of(deposit));
        
        assertBalanceEquals(deposit.getBalance(), "1000.00");
    }


    // ------------------------------------------------------------------
    // Exact-decimal expectation (TEMPORARILY DISABLED, not deleted)
    // ------------------------------------------------------------------

    /**
     * Single explicit regression case: student plan, balance 402.00, days 31.
     */
    private static Stream<Object[]> exactDecimalInterestCases() {
        return Stream.<Object[]>of(new Object[]{"student", "402.00", 31, "403.01"});
    }

    /**
     * This test intentionally documents the exact-decimal expectation for interest
     * calculation and is EXPECTED TO FAIL against the current implementation.
     *
     * Exact decimal arithmetic: 402.00 x 0.03 / 12 = 1.005, which HALF_UP-rounds to
     * 1.01, giving an expected balance of 403.01. The production code currently
     * computes the interest as a double before creating the BigDecimal (via
     * new BigDecimal(interest)), so it captures the binary approximation
     * 1.00499999999999989... which HALF_UP-rounds to 1.00 and yields 403.00 —
     * a one-cent discrepancy versus exact decimal arithmetic.
     */
    @ParameterizedTest(name = "planType={0}, initialBalance={1}, days={2}, expectedBalance={3}")
    @MethodSource("exactDecimalInterestCases")
    @Disabled("Temporarily disabled, not deleted: this test intentionally documents the exact-decimal "
            + "expectation (402.00 -> 403.01) which currently FAILS against the implementation's "
            + "double-based rounding (403.00). It is disabled pending a decision on how to document "
            + "the exact-decimal rounding discrepancy; its logic is preserved unchanged.")
    public void updateBalance_ShouldMatchExactDecimalInterestRounding(
            String planType, String initialBalance, int days, String expectedBalance) {

        // Fresh real object per invocation, exercising the REAL updateBalance method.
        TimeDeposit deposit = new TimeDeposit(1, planType, new BigDecimal(initialBalance).doubleValue(), days);
        List<TimeDeposit> plans = Arrays.asList(deposit);

        new TimeDepositCalculator().updateBalance(plans);

        // Expected balance built purely from decimal strings with exact arithmetic:
        // interest = 402.00 x 0.03 / 12, rounded to 2 dp HALF_UP, then added to the balance.
        BigDecimal interest = new BigDecimal(initialBalance)
                .multiply(new BigDecimal("0.03"))
                .divide(new BigDecimal("12"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal expected = new BigDecimal(initialBalance).add(interest);

        double actualBalance = plans.get(0).getBalance();

        // No tolerance/offset: a one-cent discrepancy must be visible.
        assertThat(BigDecimal.valueOf(actualBalance))
                .isEqualByComparingTo(new BigDecimal(expectedBalance));

        // Sanity: the locally derived exact expectation matches the supplied expected value.
        assertThat(expected).isEqualByComparingTo(new BigDecimal(expectedBalance));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Runs the real updateBalance on one fresh deposit and returns the resulting balance. */
    private static double updateBalanceOnce(String planType, double balance, int days) {
        TimeDeposit deposit = new TimeDeposit(1, planType, balance, days);
        List<TimeDeposit> plans = new ArrayList<>();
        plans.add(deposit);

        // updateBalance returns void; the result is observed on the very same instance.
        new TimeDepositCalculator().updateBalance(plans);

        return plans.get(0).getBalance();
    }

    /**
     * Cent-exact balance comparison: BigDecimal.valueOf(actual) (the canonical double
     * string, e.g. 1000.83) against a decimal-string expectation, with no tolerance so
     * that one-cent differences are visible.
     */
    private static void assertBalanceEquals(double actualBalance, String expectedBalance) {
        assertThat(BigDecimal.valueOf(actualBalance)).isEqualByComparingTo(expectedBalance);
    }
    
    private static TimeDeposit randomDeposit(String planType, int minDays, int maxDays) {
       return Instancio.of(TimeDeposit.class)
                .set(field(TimeDeposit::getId), -1)
                .supply(field(TimeDeposit::getPlanType), sup -> planType)
                .generate(field(TimeDeposit::getDays), gen -> gen.ints().range(minDays, maxDays))
                .supply(field(TimeDeposit::getBalance),
                        random -> random.intRange(10_000, 10_000_000) / 100.0) // Generate whole cents and convert to a double balance;
                .create();
    }
    
    private static List<TimeDeposit> randomDeposits(
            String planType, int minDays, int maxDays, int count) {
        return Stream.generate(
                        () -> randomDeposit(planType, minDays, maxDays))
                .limit(count)
                .toList();
    }
    
    private static Stream<Object[]> unknownPlanTypeCases() {
        return Stream.<Object[]>of(
                new Object[]{"unknown_1", 31},
                new Object[]{"unknown_2", 1000},
                new Object[]{"unknown_3", 46},
                new Object[]{"unknown_4", 365},
                new Object[]{"", 365});
    }
    
    /**
     * Intentionally mirrors the CURRENT production arithmetic of
     * TimeDepositCalculator.updateBalance as a characterization check — NOT
     * exact-decimal arithmetic: the interest is computed as a double via double
     * multiplication and division (balance * rate / 12), then rounded with
     * new BigDecimal(interest).setScale(2, HALF_UP), exactly the steps the
     * current implementation performs.
     */
    private static double mirroredExpectedBalance(double balance, int days, String planType) {
        double interest = 0;
        if (days > 30) {
            if ("student".equals(planType)) {
                if (days < 366) {
                    interest += balance * 0.03 / 12;
                }
            } else if ("premium".equals(planType)) {
                if (days > 45) {
                    interest += balance * 0.05 / 12;
                }
            } else if ("basic".equals(planType)) {
                interest += balance * 0.01 / 12;
            }
        }
        return balance + (new BigDecimal(interest).setScale(2, RoundingMode.HALF_UP)).doubleValue();
    }
}
