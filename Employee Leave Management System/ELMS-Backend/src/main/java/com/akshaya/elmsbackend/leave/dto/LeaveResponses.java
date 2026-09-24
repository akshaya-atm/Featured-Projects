package com.akshaya.elmsbackend.leave.dto;

import java.time.LocalDate;
import java.util.List;

public final class LeaveResponses {
    private LeaveResponses() {}

    public record Balance(int total, int used, int reserved, int remaining) {}
    public record Balances(int year, Balance annual, Balance casual, Balance sick) {}
    public record MedicalCertificateDto(boolean required, boolean uploaded, String fileName, boolean overdue) {}
    public record Request(Long id, String employeeId, String employeeName,
                          String leaveType, LocalDate startDate, LocalDate endDate,
                          int workingDays, String status, String reason, MedicalCertificateDto medicalCertificate) {}
    public record Requests(List<Request> requests) {}
}
