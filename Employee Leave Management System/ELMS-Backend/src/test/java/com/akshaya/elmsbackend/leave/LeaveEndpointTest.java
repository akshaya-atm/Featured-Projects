package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.auth.CustomUserDetailsService;
import com.akshaya.elmsbackend.employee.EmployeeService;
import com.akshaya.elmsbackend.auth.JwtService;
import com.akshaya.elmsbackend.config.JWTConfig;
import com.akshaya.elmsbackend.config.SecurityConfig;
import com.akshaya.elmsbackend.employee.EmployeeController;
import com.akshaya.elmsbackend.leave.dto.LeaveResponses;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.common.exception.GlobalExceptionHandler;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeaveEndpointTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private LeaveReadService reads;
    private LeaveDecisionService decisions;
    private String token;

    @Configuration @EnableWebSecurity @EnableWebMvc
    static class TestBeans {
        @Bean CustomUserDetailsService userDetailsService() { return mock(CustomUserDetailsService.class); }
        @Bean LeaveReadService leaveReadService() { return mock(LeaveReadService.class); }
        @Bean LeaveDecisionService leaveDecisionService() { return mock(LeaveDecisionService.class); }
        @Bean EmployeeService employeeService() { return mock(EmployeeService.class); }
        @Bean MedicalCertificateService medicalCertificateService() { return mock(MedicalCertificateService.class); }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "app.jwt.issuer=test-elms");
        context.register(TestBeans.class, SecurityConfig.class, JWTConfig.class,
                EmployeeController.class, LeaveController.class, GlobalExceptionHandler.class);
        context.refresh();
        reads = context.getBean(LeaveReadService.class);
        decisions = context.getBean(LeaveDecisionService.class);
        token = new JwtService(context.getBean(JwtEncoder.class), "test-elms", 900).generateAccessToken("EMP002");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void close() { if (context != null) context.close(); }

    @Test void readAndDecisionEndpointsRejectMissingToken() throws Exception {
        mvc.perform(get("/api/leave-balances/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/leave-requests/10/approve")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reads, decisions);
    }
    @Test void malformedBearerIsRejected() throws Exception {
        mvc.perform(get("/api/leave-requests/team/pending").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reads);
    }
    @Test void mcpEndpointRequiresAuthenticatedBearer() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp").header("Authorization", "Bearer invalid")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void signedJwtSubjectDeterminesEmployeeNotQueryParameter() throws Exception {
        when(reads.getBalances("EMP002", 2026)).thenReturn(new LeaveResponses.Balances(2026,
                new LeaveResponses.Balance(20, 3, 4, 13), null, null));
        mvc.perform(get("/api/leave-balances/me?year=2026&employeeId=EMP999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.annual.remaining").value(13));
        verify(reads).getBalances("EMP002", 2026);
    }
    @Test void wrongIssuerIsRejected() throws Exception {
        String wrongIssuer = new JwtService(context.getBean(JwtEncoder.class), "other-system", 900).generateAccessToken("EMP002");
        mvc.perform(get("/api/leave-balances/me").header("Authorization", "Bearer " + wrongIssuer))
                .andExpect(status().isUnauthorized());
    }
    @Test void teamAccessDeniedReturnsJsonError() throws Exception {
        when(reads.getPendingTeamRequests("EMP002")).thenThrow(new AppException(LeaveErrorCode.NOT_YOUR_TEAM, "Not your team"));
        mvc.perform(get("/api/leave-requests/team/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorMessage").value("Not your team"));
    }
    @Test void decisionUsesAuthenticatedActorAndReturnsRefreshSignal() throws Exception {
        when(decisions.decide("EMP002", 10L, false, "Coverage needed"))
                .thenReturn(new LeaveDecisionService.DecisionResult(10L, "REJECTED", true));
        mvc.perform(post("/api/leave-requests/10/reject").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"reason\":\"Coverage needed\",\"employeeId\":\"EMP999\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.dashboardChanged").value(true));
        verify(decisions).decide("EMP002", 10L, false, "Coverage needed");
    }
    @Test void repeatDecisionConflictIsPreserved() throws Exception {
        when(decisions.decide("EMP002", 10L, true, null)).thenThrow(new AppException(LeaveErrorCode.ALREADY_DECIDED, "Already decided"));
        mvc.perform(post("/api/leave-requests/10/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorMessage").value("Already decided"));
    }
    @Test void ownRequestListReturnsEmptyArray() throws Exception {
        when(reads.getOwnRequests("EMP002")).thenReturn(new LeaveResponses.Requests(List.of()));
        mvc.perform(get("/api/leave-requests/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requests").isEmpty());
    }
    @Test void corsAllowsFrontendBearerCallsButNotUntrustedOrigin() throws Exception {
        mvc.perform(options("/api/leave-requests/10/approve")
                        .header("Origin", "http://127.0.0.1:5500")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"));
        mvc.perform(options("/api/leave-requests/10/approve")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
