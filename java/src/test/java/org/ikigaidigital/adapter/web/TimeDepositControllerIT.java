package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack HTTP integration test against a real postgres:17.5
 * Testcontainer: real controller, real use case, real Flyway migrations and
 * real JDBC adapter are exercised over HTTP - nothing is mocked. The response
 * is parsed with Jackson so dates stay strings and monetary values can be
 * compared as decimals with {@code isEqualByComparingTo} (no tolerance).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class TimeDepositControllerIT {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5");

    // By default Jackson parses non-integer JSON numbers as doubles, which silently
    // loses cents beyond double's 53-bit precision; parsing as BigDecimal keeps the
    // exact decimal so assertions can prove the cents survived the HTTP response.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

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

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    public void swaggerUi_loadsTheExistingContract() throws Exception {
        ResponseEntity<String> page = restTemplate.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("swagger-ui.css");

        ResponseEntity<String> config = restTemplate.getForEntity("/v3/api-docs/swagger-config", String.class);
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(config.getBody()).get("url").asText()).isEqualTo("/openapi.yaml");

        ResponseEntity<String> contract = restTemplate.getForEntity("/openapi.yaml", String.class);
        assertThat(contract.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contract.getBody()).isEqualTo(
                new ClassPathResource("openapi.yaml").getContentAsString(StandardCharsets.UTF_8));
        assertThat(restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void getAllTimeDeposits_emptyDatabase_returnsEmptyArray() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/time-deposits", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    public void getAllTimeDeposits_returnsAllDepositsAndWithdrawalsWithoutDuplicates() throws Exception {
        insertDeposit(1, "basic", 31, "1000.00");
        insertDeposit(2, "business", 1000, "5000.00");
        insertDeposit(3, "premium", 46, "1004.17");
        insertWithdrawal(21, 2, "250.50", LocalDate.of(2026, 6, 15));
        insertWithdrawal(31, 3, "0.99", LocalDate.of(2026, 8, 1));
        insertWithdrawal(32, 3, "100.00", LocalDate.of(2026, 9, 1));

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/time-deposits", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        JsonNode root = MAPPER.readTree(response.getBody());
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isEqualTo(3);

        Map<Integer, JsonNode> depositsById = new HashMap<>();
        for (JsonNode deposit : root) {
            // Exact contract field set: no extra implementation fields.
            assertThat(fieldNames(deposit))
                    .containsExactlyInAnyOrder("id", "planType", "balance", "days", "withdrawals");
            depositsById.put(deposit.get("id").asInt(), deposit);
        }
        // Three distinct ids: no deposit is duplicated.
        assertThat(depositsById.keySet()).containsExactlyInAnyOrder(1, 2, 3);

        JsonNode basic = depositsById.get(1);
        assertThat(basic.get("planType").asText()).isEqualTo("basic");
        assertThat(basic.get("balance").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(basic.get("days").asInt()).isEqualTo(31);
        assertThat(basic.get("withdrawals").isArray()).isTrue();
        assertThat(basic.get("withdrawals").size()).isZero();

        // Unknown plan types are preserved verbatim (contract planType is open).
        JsonNode business = depositsById.get(2);
        assertThat(business.get("planType").asText()).isEqualTo("business");
        assertThat(business.get("balance").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(business.get("days").asInt()).isEqualTo(1000);
        assertThat(business.get("withdrawals").size()).isEqualTo(1);
        JsonNode businessWithdrawal = business.get("withdrawals").get(0);
        assertThat(fieldNames(businessWithdrawal))
                .containsExactlyInAnyOrder("id", "timeDepositId", "amount", "date");
        assertThat(businessWithdrawal.get("id").asInt()).isEqualTo(21);
        assertThat(businessWithdrawal.get("timeDepositId").asInt()).isEqualTo(2);
        assertThat(businessWithdrawal.get("amount").decimalValue()).isEqualByComparingTo("250.50");
        assertThat(businessWithdrawal.get("date").isTextual()).isTrue();
        assertThat(businessWithdrawal.get("date").asText()).isEqualTo("2026-06-15");

        JsonNode premium = depositsById.get(3);
        assertThat(premium.get("planType").asText()).isEqualTo("premium");
        assertThat(premium.get("balance").decimalValue()).isEqualByComparingTo("1004.17");
        assertThat(premium.get("days").asInt()).isEqualTo(46);
        assertThat(premium.get("withdrawals").size()).isEqualTo(2);

        Map<String, JsonNode> premiumWithdrawalsByDate = new HashMap<>();
        for (JsonNode withdrawal : premium.get("withdrawals")) {
            assertThat(fieldNames(withdrawal))
                    .containsExactlyInAnyOrder("id", "timeDepositId", "amount", "date");
            assertThat(withdrawal.get("timeDepositId").asInt()).isEqualTo(3);
            assertThat(withdrawal.get("date").isTextual()).isTrue();
            premiumWithdrawalsByDate.put(withdrawal.get("date").asText(), withdrawal);
        }
        assertThat(premiumWithdrawalsByDate.keySet())
                .containsExactlyInAnyOrder("2026-08-01", "2026-09-01");
        assertThat(premiumWithdrawalsByDate.get("2026-08-01").get("amount").decimalValue())
                .isEqualByComparingTo("0.99");
        assertThat(premiumWithdrawalsByDate.get("2026-09-01").get("amount").decimalValue())
                .isEqualByComparingTo("100.00");
    }

    @Test
    public void getAllTimeDeposits_doesNotModifyStoredBalances() throws Exception {
        insertDeposit(1, "basic", 31, "1000.00");
        insertDeposit(2, "business", 1000, "5000.00");
        insertDeposit(3, "premium", 46, "1004.17");
        insertWithdrawal(21, 2, "250.50", LocalDate.of(2026, 6, 15));

        List<BigDecimal> balancesBefore = storedBalances();

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/time-deposits", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).size()).isEqualTo(3);

        List<BigDecimal> balancesAfter = storedBalances();
        assertThat(balancesAfter).hasSameSizeAs(balancesBefore);
        for (int i = 0; i < balancesBefore.size(); i++) {
            assertThat(balancesAfter.get(i)).isEqualByComparingTo(balancesBefore.get(i));
        }
    }

    @Test
    public void getAllTimeDeposits_balanceBeyondDoublePrecision_keepsCents() throws Exception {
        // 2^53 + one cent: beyond double's 53-bit precision, a double-based path would lose the cent.
        insertDeposit(1, "basic", 31, "9007199254740992.01");

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/time-deposits", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = MAPPER.readTree(response.getBody());
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isEqualTo(1);
        // With exact-decimal parsing enabled, this proves the cents survive the HTTP
        // serialization round-trip (a double-based serialization path would lose them).
        assertThat(root.get(0).get("balance").decimalValue()).isEqualByComparingTo("9007199254740992.01");
    }

    private List<BigDecimal> storedBalances() {
        return jdbcTemplate.queryForList(
                "SELECT balance FROM \"timeDeposits\" ORDER BY id", BigDecimal.class);
    }
}
