package org.ikigaidigital.adapter.web;

import java.util.List;

import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateBalancesUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the time deposit API.
 */
@RestController
public class TimeDepositController {

    private final GetTimeDepositsUseCase getTimeDeposits;
    private final UpdateBalancesUseCase updateBalances;

    public TimeDepositController(
            GetTimeDepositsUseCase getTimeDeposits, UpdateBalancesUseCase updateBalances) {
        this.getTimeDeposits = getTimeDeposits;
        this.updateBalances = updateBalances;
    }

    @GetMapping("/api/v1/time-deposits")
    public List<TimeDepositView> getAllTimeDeposits() {
        return getTimeDeposits.getTimeDeposits();
    }

    @PostMapping("/api/v1/time-deposits/balance-updates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateBalances() {
        updateBalances.updateBalances();
    }
}
