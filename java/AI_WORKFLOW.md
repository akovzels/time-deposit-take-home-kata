# AI-Assisted Development

## Tools and responsibilities

- **Cline with GLM-5.3 through ClinePass:** implementation tasks, unit and
  integration tests, and test execution. Two agents were used: **GLM-5.3 (High)**
  for implementation and subagent coordination, and **GLM-5.3 Flash**
  for writing and running tests.
- **Codex:** requirements discussions, task decomposition, prompts for GLM,
  independent code review, and implementation of a few of the most complex parts.
- **Developer:** resolved ambiguities, reviewed and edited changes, approved
  design choices, and controlled commits.

AI helped establish characterization tests before refactoring, then implement
the contract, application core, bootstrap, JDBC adapter, and HTTP endpoints in
small steps. Review focused on independent assertions, preserved arithmetic,
rollback after actual SQL updates, and observable database lock waits.

## Workflow and reproduction

The developer first used Codex to analyze and discuss the assignment, decompose
the work, and prepare prompts for GLM-5.3. They then opened the repository in
Cline, selected GLM-5.3 through ClinePass, and asked it to create a subagent for
writing tests. Credentials were configured locally and are not included here.

Development proceeded one task at a time, repeating this cycle:

1. **Plan:** the developer discussed the next step with Codex and received a
   focused implementation prompt.
2. **Implement and test:** the developer passed that prompt to GLM-5.3 in Cline.
   GLM implemented the change, delegated test creation to the testing subagent,
   and coordinated test execution and fixes until the tests passed.
3. **Independent review:** Codex reviewed the changes after tests passed and
   discussed findings with the developer. Where fixes were needed, Codex
   supplied another prompt for GLM; implementation, tests, and review repeated.
4. **Approve and commit:** after the fixes, the developer performed the final
   review and committed the code. The cycle then continued with the next task.

Codex also implemented a few of the most complex parts directly. GLM remained
responsible for test execution: `mvn test` and `mvn verify` from `java`, with
Docker available for integration tests. Codex's compilation checks were separate
from test execution.

The repo contains the build, Docker setup, and test configuration needed to
repeat verification. The agent instructions below summarize the conversation;
there are no additional checked-in Cline rules or agent configuration files.
Agent output can vary, so reproducibility relies on the contract and checks.

<details>
<summary>Working rules and condensed task prompts</summary>

These are condensed instructions reconstructed from the development conversation,
not verbatim session transcripts or additional installed system prompts.

**Working rules**

> Re-read the current requirements, code, and Git diff before each task.
> Preserve unrelated changes. Keep tasks
> focused and comments minimal. Preserve TimeDeposit, the updateBalance method
> signature, and observable calculation behavior. Do not commit or push.

**Characterization and refactoring**

> Add JUnit 5 tests with AssertJ and Instancio. Cover deterministic boundaries,
> randomized inputs, and existing rounding behavior. Refactor only after tests
> establish compatibility; preserve the original double arithmetic.

**Incremental implementation**

> Implement only the agreed step: OpenAPI contract, framework-independent core,
> Spring Boot bootstrap, PostgreSQL adapter, or HTTP endpoint. Follow the existing
> ports and contract. Use Mockito for isolated tests and PostgreSQL Testcontainers
> for integration tests. Keep unrelated behavior unchanged.

**Final verification and review**

> Review the current uncommitted changes and run mvn clean verify with Docker
> available. Check rollback after actual SQL updates, concurrent requests waiting
> for row locks, and compatibility with the calculator and GET endpoint. Apply
> focused fixes and report test results and remaining concerns. Do not commit.

</details>
