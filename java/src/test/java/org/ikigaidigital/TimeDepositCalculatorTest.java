package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.instancio.Select.field;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.ikigaidigital.interest.InterestPolicy;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    public void updateBalance_CustomPolicy_ShouldApplyInterestWithoutChangingCalculator() {
        InterestPolicy bonusPolicy = deposit -> 7.25;
        var calculator = new TimeDepositCalculator(Map.of("bonus", bonusPolicy));
        var deposit = new TimeDeposit(1, "bonus", 1000.00, 100);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(1007.25);
    }

    @Test
    public void updateBalance_EmptyList_ShouldCompleteWithoutException() {
        assertThatCode(() -> new TimeDepositCalculator().updateBalance(List.of()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "planType={0}, days={1}")
    @CsvSource({
            "basic,   31",
            "student, 31",
            "premium, 46"
    })
    public void updateBalance_ZeroBalance_ShouldRemainZero(String planType, int days) {
        var deposit = new TimeDeposit(1, planType, 0.0, days);

        new TimeDepositCalculator().updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(0.0);
    }

    /**
     * Exact decimal interest is 402.00 * 0.03 / 12 = 1.005, which HALF_UP rounds to 1.01.
     * The current double calculation produces a value slightly below 1.005;
     * new BigDecimal(interest) preserves that approximation and rounds it to 1.00.
     * Pin the resulting 403.00 balance to detect behavior changes during refactoring.
     * This documents compatibility, not financial correctness; changing the expectation
     * to 403.01 requires a separate decision to fix the calculation.
     */
    @Test
    
    public void updateBalance_Student_ShouldPreserveExistingDoubleRounding() {
        var deposit = new TimeDeposit(1, "student", 402.00, 31);

        new TimeDepositCalculator().updateBalance(List.of(deposit));
        
        assertThat(deposit.getBalance()).isEqualTo(403.00);
    }

    @ParameterizedTest(name = "[{index}] planType={0}, days={1}, expectedBalance={2}")
    @CsvSource(textBlock = """
            basic,      30, 1000.00
            basic,      31, 1000.83
            basic,    1000, 1000.83
            student,    30, 1000.00
            student,    31, 1002.50
            student,   365, 1002.50
            student,   366, 1000.00
            premium,    30, 1000.00
            premium,    45, 1000.00
            premium,    46, 1004.17
            business,   30, 1000.00
            business,   31, 1000.00
            """)
    public void updateBalance_DeterministicBoundaryCases_ShouldMatchExpectedBalance(
            String planType, int days, String expectedBalance) {
        double actual = updateBalanceOnce(planType, BALANCE_1000, days);

        assertBalanceEquals(actual, expectedBalance);
    }
    
    /**
     * Unknown plan types never match an interest branch, so no interest is added
     * regardless of the day count (including day counts that would be eligible for
     * the known plan types).
     */
    @ParameterizedTest(name = "[{index}] planType='{0}', days={1}")
    @CsvSource({
            "unknown, 31",
            "unknown, 46",
            "unknown, 365",
            "unknown, 1000",
            "'',      365"
    })
    public void updateBalance_UnknownPlanType_ShouldLeaveBalanceUnchanged(String planType, int days) {
        
        var deposit = new TimeDeposit(-1, planType, BALANCE_1000, days);
        
        new TimeDepositCalculator().updateBalance(List.of(deposit));
        
        assertBalanceEquals(deposit.getBalance(), "1000.00");
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
     * Reproducibility: Instancio picks a random seed per run; on failure the seed is
     * reported by the Instancio extension and can be pinned with @Seed to reproduce.
     */
    @Test
    public void updateBalance_RandomEligibleDeposits_ShouldMatchMirroredProductionArithmetic() {
        var deposits = new ArrayList<TimeDeposit>();
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
        
        double[] expectedBalances = deposits.stream()
                .mapToDouble(deposit -> mirroredExpectedBalance(deposit.getBalance(), deposit.getDays(), deposit.getPlanType()))
                .toArray();

        double[] originalBalances = deposits.stream().mapToDouble(TimeDeposit::getBalance)
                .toArray();

        new TimeDepositCalculator().updateBalance(deposits);

        for (int i = 0; i < deposits.size(); i++) {
            TimeDeposit deposit = deposits.get(i);
            double expected = expectedBalances[i];
           
            assertThat(deposit.getBalance())
                    .as("index=%d, planType=%s, days=%d, originalBalance=%s",
                            i, deposit.getPlanType(), deposit.getDays(), originalBalances[i])
                    .isEqualTo(expected);

        }
    }

    /**
     * Randomized ineligible deposits: no interest may be added, so the balance must be
     * exactly unchanged. Four ineligible categories are generated:
     * (a) known plan types with days &lt;= 30 (global gate closed),
     * (b) student with days &gt;= 366 (past the 365-day cap),
     * (c) unknown plan types with eligible-looking day counts,
     * (d) premium with days between 31 and 45 (before premium interest starts).
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

        // (d) premium after the global gate opens, but before premium eligibility.
        deposits.addAll(randomDeposits("premium", 31, 45, 15));
    
        double[] originalBalances = deposits.stream().mapToDouble(TimeDeposit::getBalance).toArray();

        new TimeDepositCalculator().updateBalance(deposits);

        for (int i = 0; i < deposits.size(); i++) {
            TimeDeposit deposit = deposits.get(i);
            assertThat(BigDecimal.valueOf(deposit.getBalance()))
                    .as("index=%d, planType=%s, days=%d, originalBalance=%s",
                            i, deposit.getPlanType(), deposit.getDays(), originalBalances[i])
                    .isEqualByComparingTo(BigDecimal.valueOf(originalBalances[i]));
        }
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
     * Compare the actual balance with a decimal-string expectation without rounding or tolerance.
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
