package org.ikigaidigital.adapter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.model.WithdrawalView;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link JdbcTimeDepositRepository} against a real
 * PostgreSQL instance (postgres:17.5 Testcontainer). The real application
 * context boots, Flyway applies the real migrations on the container, and the
 * real {@code @Repository} adapter is wired — nothing is mocked and no
 * production datasource or migration configuration is disabled.
 *
 * <p>Monetary expectations are explicit decimal strings compared with
 * {@code isEqualByComparingTo} (no tolerance).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public class JdbcTimeDepositRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TimeDepositRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanTables() {
        // Withdrawals first: they reference "timeDeposits" rows.
        jdbcTemplate.update("DELETE FROM withdrawals");
        jdbcTemplate.update("DELETE FROM \"timeDeposits\"");
    }

    private void insertDeposit(int id, String planType, int days, String balance) {
        jdbcTemplate.update(
                "INSERT INTO \"timeDeposits\" (id, \"planType\", days, balance) VALUES (?, ?, ?, ?)",
                id, planType, days, new BigDecimal(balance));
    }

    private void insertWithdrawal(int id, int timeDepositId, String amount, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO withdrawals (id, \"timeDepositId\", amount, date) VALUES (?, ?, ?, ?)",
                id, timeDepositId, new BigDecimal(amount), date);
    }

    /**
     * The moved smoke coverage: the real application context loads against the
     * container-provided datasource, and the registered port implementation is
     * the production JDBC adapter.
     */
    @Test
    public void contextLoads_withContainerDatasource_repositoryIsJdbcAdapter() {
        assertThat(repository).isInstanceOf(JdbcTimeDepositRepository.class);
    }

    @Test
    public void findAll_emptyDatabase_returnsEmptyList() {
        assertThat(repository.findAll()).isEmpty();
    }

    /**
     * Deposits with zero, one, and multiple withdrawals map to exactly one view
     * each (no LEFT JOIN duplicates); plan, days, decimals, and dates are
     * preserved; unknown plan types map unchanged; deposits and withdrawals
     * are ordered by id.
     */
    @Test
    public void findAll_zeroOneAndMultipleWithdrawals_mapsWithoutDuplicates() {
        insertDeposit(1, "basic", 30, "50.25");
        insertDeposit(2, "business", 45, "1000.00");
        insertDeposit(3, "premium", 60, "0.01");
        insertWithdrawal(10, 2, "250.50", LocalDate.of(2025, 1, 15));
        // Withdrawal 30 inserted before 11 and 12 to prove id ordering.
        insertWithdrawal(30, 3, "0.99", LocalDate.of(2025, 3, 1));
        insertWithdrawal(11, 3, "100.00", LocalDate.of(2025, 2, 1));
        insertWithdrawal(12, 3, "1234.56", LocalDate.of(2025, 2, 10));

        List<TimeDepositView> views = repository.findAll();

        assertThat(views).hasSize(3);
        assertThat(views.stream().map(TimeDepositView::id)).containsExactly(1, 2, 3);

        TimeDepositView bare = views.get(0);
        assertThat(bare.planType()).isEqualTo("basic");
        assertThat(bare.days()).isEqualTo(30);
        assertThat(bare.balance()).isEqualByComparingTo("50.25");
        assertThat(bare.withdrawals()).isEmpty();

        TimeDepositView unknownPlan = views.get(1);
        assertThat(unknownPlan.planType()).isEqualTo("business");
        assertThat(unknownPlan.days()).isEqualTo(45);
        assertThat(unknownPlan.balance()).isEqualByComparingTo("1000.00");
        assertThat(unknownPlan.withdrawals()).hasSize(1);
        WithdrawalView single = unknownPlan.withdrawals().get(0);
        assertThat(single.id()).isEqualTo(10);
        assertThat(single.timeDepositId()).isEqualTo(2);
        assertThat(single.amount()).isEqualByComparingTo("250.50");
        assertThat(single.date()).isEqualTo(LocalDate.of(2025, 1, 15));

        TimeDepositView premium = views.get(2);
        assertThat(premium.planType()).isEqualTo("premium");
        assertThat(premium.days()).isEqualTo(60);
        assertThat(premium.balance()).isEqualByComparingTo("0.01");
        assertThat(premium.withdrawals())
                .extracting(WithdrawalView::id)
                .containsExactly(11, 12, 30);
        assertThat(premium.withdrawals().get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(premium.withdrawals().get(1).amount()).isEqualByComparingTo("1234.56");
        assertThat(premium.withdrawals().get(2).amount()).isEqualByComparingTo("0.99");
    }

    @Test
    public void findAllForUpdate_insideTransactionTemplate_returnsDomainValues() {
        insertDeposit(1, "basic", 30, "100.00");
        insertDeposit(2, "premium", 60, "2000.00");
        insertWithdrawal(10, 1, "10.00", LocalDate.of(2025, 1, 2));

        List<TimeDeposit> deposits = new TransactionTemplate(transactionManager)
                .execute(status -> repository.findAllForUpdate());

        assertThat(deposits).hasSize(2);
        assertThat(deposits.get(0).getId()).isEqualTo(1);
        assertThat(deposits.get(0).getPlanType()).isEqualTo("basic");
        assertThat(deposits.get(0).getDays()).isEqualTo(30);
        assertThat(deposits.get(0).getBalance()).isEqualTo(100.00);
        assertThat(deposits.get(1).getId()).isEqualTo(2);
        assertThat(deposits.get(1).getPlanType()).isEqualTo("premium");
        assertThat(deposits.get(1).getDays()).isEqualTo(60);
        assertThat(deposits.get(1).getBalance()).isEqualTo(2000.00);
    }

    @Test
    public void updateBalances_persistsCalculatedBalances_preservingOtherColumnsAndWithdrawals() {
        insertDeposit(1, "basic", 31, "1000.00");
        insertDeposit(2, "premium", 46, "1000.00");
        insertDeposit(3, "student", 366, "1000.00");
        insertWithdrawal(10, 1, "100.00", LocalDate.of(2025, 1, 10));
        insertWithdrawal(11, 2, "200.00", LocalDate.of(2025, 2, 20));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<TimeDeposit> deposits = repository.findAllForUpdate();
            new TimeDepositCalculator().updateBalance(deposits);
            repository.updateBalances(deposits);
        });

        List<TimeDepositView> views = repository.findAll();
        assertThat(views).hasSize(3);
        assertThat(views.get(0).balance()).isEqualByComparingTo("1000.83");
        assertThat(views.get(1).balance()).isEqualByComparingTo("1004.17");
        assertThat(views.get(2).balance()).isEqualByComparingTo("1000.00");
        assertThat(views.stream().map(TimeDepositView::planType))
                .containsExactly("basic", "premium", "student");
        assertThat(views.stream().map(TimeDepositView::days)).containsExactly(31, 46, 366);
        assertThat(views.get(0).withdrawals())
                .extracting(WithdrawalView::id, WithdrawalView::amount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10, new BigDecimal("100.00")));
        assertThat(views.get(1).withdrawals())
                .extracting(WithdrawalView::id, WithdrawalView::amount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11, new BigDecimal("200.00")));
        assertThat(views.get(2).withdrawals()).isEmpty();
    }

    @Test
    public void updateBalances_emptyList_completesWithoutChanges() {
        insertDeposit(1, "basic", 30, "500.00");

        assertThatCode(() -> repository.updateBalances(List.of()))
                .doesNotThrowAnyException();

        List<TimeDepositView> views = repository.findAll();
        assertThat(views).hasSize(1);
        assertThat(views.get(0).balance()).isEqualByComparingTo("500.00");
    }

    /**
     * Student 402.00 for 31 days: the double computation of 402.00 * 0.03 / 12
     * lands just below 1.005, so HALF_UP yields 1.00 and the persisted balance
     * is exactly 403.00.
     */
    @Test
    public void updateBalances_student402For31Days_persistsExactly403() {
        insertDeposit(1, "student", 31, "402.00");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<TimeDeposit> deposits = repository.findAllForUpdate();
            new TimeDepositCalculator().updateBalance(deposits);
            repository.updateBalances(deposits);
        });

        List<TimeDepositView> views = repository.findAll();
        assertThat(views).hasSize(1);
        assertThat(views.get(0).balance()).isEqualByComparingTo("403.00");
    }

    /**
     * Dedicated exception thrown inside the transaction to trigger the
     * rollback. Using a dedicated type (not a generic {@code RuntimeException})
     * ensures the test can only pass via the intended rollback path.
     */
    private static final class RollbackTriggerException extends RuntimeException {
        RollbackTriggerException() {
            super("deliberate failure after save to trigger rollback");
        }
    }

    /**
     * Load, calculate, save, then fail: the rollback must restore the original
     * balance, proving the adapter participates in the caller's transaction
     * and never commits independently. The dedicated exception and the
     * in-transaction balance assertion ensure the test can only pass via the
     * intended rollback path, not via an unrelated failure (e.g. an exception
     * thrown by loading or updating before the trigger).
     */
    @Test
    public void updateBalances_rollback_restoresOriginalBalances() {
        insertDeposit(1, "basic", 31, "1000.00");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<TimeDeposit> deposits = repository.findAllForUpdate();
            new TimeDepositCalculator().updateBalance(deposits);
            repository.updateBalances(deposits);

            // Prove the update was applied inside this transaction before the rollback.
            BigDecimal inTx = jdbcTemplate.queryForObject(
                    "SELECT balance FROM \"timeDeposits\" WHERE id = ?", BigDecimal.class, 1);
            assertThat(inTx).isEqualByComparingTo("1000.83");

            // Trigger the rollback with the dedicated exception type.
            throw new RollbackTriggerException();
        })).isInstanceOf(RollbackTriggerException.class);

        // After the rollback the persisted balance must be restored to the original value.
        BigDecimal afterRollback = jdbcTemplate.queryForObject(
                "SELECT balance FROM \"timeDeposits\" WHERE id = ?", BigDecimal.class, 1);
        assertThat(afterRollback).isEqualByComparingTo("1000.00");
    }
}
