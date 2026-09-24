package com.akshaya.elmsbackend.tool;

import com.akshaya.elmsbackend.rag.CompanyPolicyRagService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyPolicyTools {

    private final CompanyPolicyRagService companyPolicyRagService;

    public CompanyPolicyTools(CompanyPolicyRagService companyPolicyRagService) {
        this.companyPolicyRagService = companyPolicyRagService;
    }

    @Tool(
            name = "search_company_leave_policy",
            description = "Search the ABC Corp leave policy for rules, entitlements, approval routes, working-day rules, medical-certificate requirements, and other general policy questions. Do not use this tool for an employee's live balance, requests, reporting hierarchy, or approval assignments."
    )
    public PolicySearchResult searchCompanyLeavePolicy(
            @ToolParam(description = "The employee's general leave-policy question")
            String question) {

        List<PolicyPassage> passages = companyPolicyRagService
                .searchPolicy(question)
                .stream()
                .map(CompanyPolicyTools::toPolicyPassage)
                .toList();

        return new PolicySearchResult(passages);
    }

    private static PolicyPassage toPolicyPassage(Document document) {
        return new PolicyPassage(
                document.getText(),
                document.getScore()
        );
    }

    public record PolicySearchResult(List<PolicyPassage> passages) {
    }

    public record PolicyPassage(String text, Double relevanceScore) {
    }
}
