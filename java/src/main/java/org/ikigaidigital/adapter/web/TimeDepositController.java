package org.ikigaidigital.adapter.web;

import java.util.List;

import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter exposing the retrieval operation of the OpenAPI contract.
 */
@RestController
public class TimeDepositController {

    private final GetTimeDepositsUseCase getTimeDeposits;

    public TimeDepositController(GetTimeDepositsUseCase getTimeDeposits) {
        this.getTimeDeposits = getTimeDeposits;
    }

    @GetMapping("/api/v1/time-deposits")
    public List<TimeDepositView> getAllTimeDeposits() {
        return getTimeDeposits.getTimeDeposits();
    }
}
