package org.ikigaidigital.adapter.jdbc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.model.WithdrawalView;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Production {@link TimeDepositRepository} adapter backed by PostgreSQL.
 */
@Repository
public class JdbcTimeDepositRepository implements TimeDepositRepository {

    private static final String FIND_ALL_SQL = """
            SELECT d.id AS deposit_id, d."planType" AS plan_type, d.balance, d.days,
                   w.id AS withdrawal_id, w."timeDepositId" AS withdrawal_time_deposit_id,
                   w.amount AS withdrawal_amount, w.date AS withdrawal_date
            FROM "timeDeposits" d
            LEFT JOIN withdrawals w ON w."timeDepositId" = d.id
            ORDER BY d.id, w.id
            """;

    private static final String FIND_ALL_FOR_UPDATE_SQL = """
            SELECT id, "planType" AS plan_type, balance, days
            FROM "timeDeposits"
            ORDER BY id
            FOR UPDATE
            """;

    private static final String UPDATE_BALANCE_SQL =
            "UPDATE \"timeDeposits\" SET balance = :balance WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTimeDepositRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TimeDepositView> findAll() {
        return jdbcTemplate.query(FIND_ALL_SQL, rs -> {
            Map<Integer, TimeDepositRow> depositsById = new LinkedHashMap<>();
            Map<Integer, List<WithdrawalView>> withdrawalsById = new LinkedHashMap<>();
            while (rs.next()) {
                TimeDepositRow row = new TimeDepositRow(
                        rs.getInt("deposit_id"), rs.getString("plan_type"),
                        rs.getBigDecimal("balance"), rs.getInt("days"));
                depositsById.putIfAbsent(row.id(), row);
                long withdrawalId = rs.getLong("withdrawal_id");
                if (rs.wasNull()) {
                    // LEFT JOIN miss: the deposit has no withdrawals.
                    continue;
                }
                withdrawalsById.computeIfAbsent(row.id(), id -> new ArrayList<>())
                        .add(new WithdrawalView(
                                (int) withdrawalId,
                                rs.getInt("withdrawal_time_deposit_id"),
                                rs.getBigDecimal("withdrawal_amount"),
                                rs.getObject("withdrawal_date", LocalDate.class)));
            }
            List<TimeDepositView> views = new ArrayList<>(depositsById.size());
            for (TimeDepositRow row : depositsById.values()) {
                views.add(new TimeDepositView(row.id(), row.planType(), row.balance(),
                        row.days(), withdrawalsById.getOrDefault(row.id(), List.of())));
            }
            return views;
        });
    }

    /**
     * The caller must supply an active transaction encompassing loading,
     * calculation, and saving: the {@code FOR UPDATE} row locks are held until
     * that transaction ends. This method does not open its own transaction, so
     * without one the locks would be released before the use case completes.
     */
    @Override
    public List<TimeDeposit> findAllForUpdate() {
        return jdbcTemplate.query(FIND_ALL_FOR_UPDATE_SQL, (rs, rowNum) -> new TimeDeposit(
                rs.getInt("id"),
                rs.getString("plan_type"),
                // The domain model keeps its Double balance; mapping happens here only.
                rs.getBigDecimal("balance").doubleValue(),
                rs.getInt("days")));
    }

    /**
     * Participates in the caller's transaction; never commits independently.
     */
    @Override
    public void updateBalances(List<TimeDeposit> deposits) {
        if (deposits.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = deposits.stream()
                .map(deposit -> new MapSqlParameterSource()
                        .addValue("id", deposit.getId())
                        // NUMERIC(19,2) storage policy: normalize the Double balance
                        // to scale 2 (HALF_UP). A storage decision, not a correction
                        // of the calculator's Double-based behaviour.
                        .addValue("balance", BigDecimal.valueOf(deposit.getBalance())
                                .setScale(2, RoundingMode.HALF_UP)))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(UPDATE_BALANCE_SQL, batch);
    }

    /** Row-level carry-over for grouping the joined result in {@link #findAll()}. */
    private record TimeDepositRow(int id, String planType, BigDecimal balance, int days) {
    }
}
