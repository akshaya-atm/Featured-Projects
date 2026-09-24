package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.leave.dto.LeaveResponses.Balances;
import com.akshaya.elmsbackend.leave.dto.LeaveResponses.Requests;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api")
public class LeaveController {
    private final LeaveReadService leaveReadService;
    private final LeaveDecisionService leaveDecisionService;
    private final MedicalCertificateService medicalCertificateService;

    public LeaveController(LeaveReadService leaveReadService, LeaveDecisionService leaveDecisionService, MedicalCertificateService medicalCertificateService) {
        this.leaveReadService = leaveReadService;
        this.leaveDecisionService = leaveDecisionService;
        this.medicalCertificateService = medicalCertificateService;
    }

    @GetMapping("/leave-balances/me")
    public Balances getBalances(Principal principal, @RequestParam(required = false) Integer year) {
        return leaveReadService.getBalances(principal.getName(), year);
    }

    @GetMapping("/leave-requests/me")
    public Requests getOwnRequests(Principal principal) {
        return leaveReadService.getOwnRequests(principal.getName());
    }

    @GetMapping("/leave-requests/team/pending")
    public Requests getPendingTeamRequests(Principal principal) {
        return leaveReadService.getPendingTeamRequests(principal.getName());
    }

    @PostMapping("/leave-requests/{requestId}/approve")
    public LeaveDecisionService.DecisionResult approve(Principal principal, @PathVariable Long requestId,
                                                       @RequestBody(required = false) DecisionBody body) {
        return leaveDecisionService.decide(principal.getName(), requestId, true, body == null ? null : body.reason());
    }

    @PostMapping("/leave-requests/{requestId}/reject")
    public LeaveDecisionService.DecisionResult reject(Principal principal, @PathVariable Long requestId,
                                                      @RequestBody(required = false) DecisionBody body) {
        return leaveDecisionService.decide(principal.getName(), requestId, false, body == null ? null : body.reason());
    }

    @PostMapping("/leave-requests/{requestId}/medical-certificate")
    public void uploadCertificate(Principal principal, @PathVariable Long requestId, 
                                  @RequestParam("document") org.springframework.web.multipart.MultipartFile document) {
        medicalCertificateService.uploadCertificate(principal.getName(), requestId, document);
    }

    @GetMapping("/leave-requests/{requestId}/medical-certificate")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadCertificate(
            Principal principal, @PathVariable Long requestId) {
        return medicalCertificateService.downloadCertificate(principal.getName(), requestId);
    }

    public record DecisionBody(String reason) {}
}
