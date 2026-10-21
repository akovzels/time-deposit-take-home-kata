BEGIN;

WITH deposits AS (
    INSERT INTO "timeDeposits" ("planType", days, balance)
    VALUES
        ('basic', 30, 1200.00),
        ('basic', 31, 1200.00),
        ('student', 31, 402.00),
        ('student', 365, 1200.00),
        ('student', 366, 1200.00),
        ('premium', 45, 1200.00),
        ('premium', 46, 1200.00),
        ('business', 1000, 1200.00)
    RETURNING id, "planType", days
)
INSERT INTO withdrawals ("timeDepositId", amount, date)
SELECT d.id, w.amount, w.date
FROM deposits d
CROSS JOIN (VALUES
    (50.25, DATE '2026-06-15'),
    (25.00, DATE '2026-06-20')
) AS w(amount, date)
WHERE d."planType" = 'basic' AND d.days = 31;

COMMIT;
