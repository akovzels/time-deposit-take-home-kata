package org.ikigaidigital.adapter.web;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.model.WithdrawalView;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateBalancesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Docker-free controller slice test: only the web layer is loaded, the use
 * case is a Mockito mock. Strict JSON comparison ({@code json(..., true)})
 * pins the OpenAPI response shape: exact field set (no extra implementation
 * fields), numeric monetary values, empty withdrawals array, preserved
 * unknown plan types, ISO-8601 date strings.
 *
 * <p>Note: BigDecimal serializes as a JSON number directly - there is no
 * double conversion in the HTTP layer.</p>
 */
@WebMvcTest(TimeDepositController.class)
public class TimeDepositControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetTimeDepositsUseCase getTimeDeposits;

    @MockitoBean
    private UpdateBalancesUseCase updateBalances;

    @Test
    public void getAllTimeDeposits_unexpectedFailure_returnsSafeProblemDetail() throws Exception {
        when(getTimeDeposits.getTimeDeposits()).thenThrow(new IllegalStateException("Sensitive database details"));

        expectInternalServerProblem(get("/api/v1/time-deposits"), "/api/v1/time-deposits");
    }

    @Test
    public void updateBalances_unexpectedFailure_returnsSafeProblemDetail() throws Exception {
        doThrow(new IllegalStateException("Sensitive database details")).when(updateBalances).updateBalances();

        expectInternalServerProblem(post("/api/v1/time-deposits/balance-updates"),
                "/api/v1/time-deposits/balance-updates");
    }

    @Test
    public void unsupportedMethod_preservesMethodNotAllowedStatusAndHeader() throws Exception {
        mockMvc.perform(put("/api/v1/time-deposits"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists(HttpHeaders.ALLOW))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));

        verifyNoInteractions(getTimeDeposits, updateBalances);
    }

    @Test
    public void unknownPath_preservesNotFoundStatus() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verifyNoInteractions(getTimeDeposits, updateBalances);
    }

    private void expectInternalServerProblem(MockHttpServletRequestBuilder request, String instance) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("""
                        {
                          "type": "about:blank",
                          "title": "Internal Server Error",
                          "status": 500,
                          "detail": "An unexpected error occurred.",
                          "instance": "%s"
                        }
                        """.formatted(instance), true));
    }

    @Test
    public void updateBalances_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/time-deposits/balance-updates"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(updateBalances).updateBalances();
        verifyNoMoreInteractions(updateBalances);
        verifyNoInteractions(getTimeDeposits);
    }

    @Test
    public void getAllTimeDeposits_emptyRepository_returnsEmptyArray() throws Exception {
        when(getTimeDeposits.getTimeDeposits()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]", true));

        verify(getTimeDeposits, times(1)).getTimeDeposits();
        verifyNoMoreInteractions(getTimeDeposits);
    }

    @Test
    public void getAllTimeDeposits_mixedDeposits_returnsStrictContractJson() throws Exception {
        when(getTimeDeposits.getTimeDeposits()).thenReturn(List.of(
                new TimeDepositView(1, "student", new BigDecimal("1002.50"), 45,
                        List.of(new WithdrawalView(11, 1, new BigDecimal("50.25"),
                                LocalDate.of(2026, 6, 15)))),
                new TimeDepositView(2, "basic", new BigDecimal("1004.17"), 46, List.of()),
                new TimeDepositView(3, "business", new BigDecimal("5000.00"), 1000, List.of())));

        // Strict mode: exact field set, no extra fields, exact array sizes.
        String expectedJson = """
                [
                  {
                    "id": 1,
                    "planType": "student",
                    "balance": 1002.50,
                    "days": 45,
                    "withdrawals": [
                      {
                        "id": 11,
                        "timeDepositId": 1,
                        "amount": 50.25,
                        "date": "2026-06-15"
                      }
                    ]
                  },
                  {
                    "id": 2,
                    "planType": "basic",
                    "balance": 1004.17,
                    "days": 46,
                    "withdrawals": []
                  },
                  {
                    "id": 3,
                    "planType": "business",
                    "balance": 5000.00,
                    "days": 1000,
                    "withdrawals": []
                  }
                ]
                """;

        mockMvc.perform(get("/api/v1/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedJson, true));

        verify(getTimeDeposits, times(1)).getTimeDeposits();
        verifyNoMoreInteractions(getTimeDeposits);
    }
}
