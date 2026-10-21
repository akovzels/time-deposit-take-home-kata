package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ikigaidigital.adapter.jdbc.JdbcTimeDepositRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UpdateBalancesControllerIT {

    private static final String UPDATE_PATH = "/api/v1/time-deposits/balance-updates";

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
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private JdbcTimeDepositRepository repository;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM withdrawals");
        jdbcTemplate.update("DELETE FROM \"timeDeposits\"");
    }

    @Test
    void updateBalances_emptyDatabase_returnsNoContent() {
        ResponseEntity<String> response = postUpdate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        assertThat(storedBalances()).isEmpty();
    }

    @Test
    void updateBalances_updatesEligiblePlansAndPreservesOtherFields() {
        insertDeposit(1, "basic", 31, "1200.00");
        insertDeposit(2, "student", 31, "402.00");
        insertDeposit(3, "premium", 46, "1200.00");
        insertDeposit(4, "basic", 30, "1200.00");
        insertDeposit(5, "student", 366, "1200.00");
        insertDeposit(6, "premium", 45, "1200.00");
        insertDeposit(7, "business", 1000, "1200.00");
        jdbcTemplate.update("""
                INSERT INTO withdrawals (id, "timeDepositId", amount, date)
                VALUES (1, 1, 50.25, DATE '2026-06-15')
                """);
        List<Map<String, Object>> fieldsBefore = storedNonBalanceFields();
        List<Map<String, Object>> withdrawalsBefore = storedWithdrawals();

        ResponseEntity<String> response = postUpdate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        assertBalances("1201.00", "403.00", "1205.00", "1200.00", "1200.00", "1200.00", "1200.00");
        assertThat(storedNonBalanceFields()).isEqualTo(fieldsBefore);
        assertThat(storedWithdrawals()).isEqualTo(withdrawalsBefore);
    }

    @Test
    void updateBalances_repeatedRequests_applyInterestTwice() {
        insertDeposit(1, "basic", 31, "1200.00");

        assertThat(postUpdate().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertBalances("1201.00");
        assertThat(postUpdate().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertBalances("1202.00");
    }

    @Test
    void updateBalances_failureAfterBatchUpdate_rollsBackEveryBalance() throws Exception {
        insertDeposit(1, "basic", 31, "1200.00");
        insertDeposit(2, "basic", 31, "2400.00");
        AtomicBoolean updatedInsideTransaction = new AtomicBoolean();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertBalances("1201.00", "2402.00");
            updatedInsideTransaction.set(true);
            throw new ForcedRollbackException();
        }).when(repository).updateBalances(anyList());

        ResponseEntity<String> response = postUpdate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = new ObjectMapper().readTree(response.getBody());
        assertThat(problem.get("type").asText()).isEqualTo("about:blank");
        assertThat(problem.get("title").asText()).isEqualTo("Internal Server Error");
        assertThat(problem.get("status").asInt()).isEqualTo(500);
        assertThat(problem.get("detail").asText()).isEqualTo("An unexpected error occurred.");
        assertThat(problem.get("instance").asText()).isEqualTo(UPDATE_PATH);
        assertThat(updatedInsideTransaction).isTrue();
        assertBalances("1200.00", "2400.00");
    }

    @Test
    void updateBalances_concurrentRequests_waitForLocksAndUseCommittedBalances() throws Exception {
        insertDeposit(1, "basic", 31, "1200.00");
        insertDeposit(2, "basic", 31, "2400.00");
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger secondBackendPid = new AtomicInteger();

        doAnswer(invocation -> {
            if (loads.incrementAndGet() == 2) {
                secondBackendPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                secondStarted.countDown();
            }
            return invocation.callRealMethod();
        }).when(repository).findAllForUpdate();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (saves.incrementAndGet() == 1) {
                firstSaved.countDown();
                if (!releaseFirst.await(45, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release the first transaction");
                }
            }
            return null;
        }).when(repository).updateBalances(anyList());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> first = executor.submit(this::postUpdate);
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            assertBalances("1200.00", "2400.00");
            Future<ResponseEntity<String>> second = executor.submit(this::postUpdate);
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // Observe a database lock wait instead of relying on thread scheduling or sleeps.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(jdbcTemplate.queryForObject(
                            "SELECT cardinality(pg_blocking_pids(?))", Integer.class, secondBackendPid.get()))
                            .isGreaterThan(0));
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(second.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertBalances("1202.00", "2404.00");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResponseEntity<String> postUpdate() {
        return restTemplate.postForEntity(UPDATE_PATH, null, String.class);
    }

    private void insertDeposit(int id, String planType, int days, String balance) {
        jdbcTemplate.update(
                "INSERT INTO \"timeDeposits\" (id, \"planType\", days, balance) VALUES (?, ?, ?, ?)",
                id, planType, days, new BigDecimal(balance));
    }

    private List<BigDecimal> storedBalances() {
        return jdbcTemplate.queryForList("SELECT balance FROM \"timeDeposits\" ORDER BY id", BigDecimal.class);
    }

    private void assertBalances(String... expected) {
        List<BigDecimal> balances = storedBalances();
        assertThat(balances).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(balances.get(i)).isEqualByComparingTo(expected[i]);
        }
    }

    private List<Map<String, Object>> storedNonBalanceFields() {
        return jdbcTemplate.queryForList("SELECT id, \"planType\", days FROM \"timeDeposits\" ORDER BY id");
    }

    private List<Map<String, Object>> storedWithdrawals() {
        return jdbcTemplate.queryForList("SELECT * FROM withdrawals ORDER BY id");
    }

    private static final class ForcedRollbackException extends RuntimeException {
    }
}
