package org.ikigaidigital;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Minimal smoke test that only verifies the Spring application context loads
 * with the real bootstrap configuration ({@link TimeDepositApplication}).
 *
 * <p>The deposit endpoints and database functionality are NOT implemented yet,
 * and no application-service beans are wired (their production repository
 * adapter does not exist yet), so no database, Docker, external services, or
 * mocks are involved — the context is loaded with the plain classpath
 * configuration only.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TimeDepositApplicationTests {

    /**
     * The context load itself is the assertion: if the Spring application
     * context fails to start, this test fails. No further checks are needed.
     */
    @Test
    public void contextLoads() {
        // Intentionally empty: successful context loading is the verification.
    }
}
