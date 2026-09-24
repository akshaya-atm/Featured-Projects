package com.akshaya.elmsbackend.tool;

import com.akshaya.elmsbackend.auth.AuthErrorCode;
import com.akshaya.elmsbackend.common.exception.AppException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ToolCallContext {

    public static final String EMPLOYEE_ID_KEY = "employeeId";
    public static final String DASHBOARD_CHANGE_TRACKER_KEY = "dashboardChangeTracker";
    public static final String CERTIFICATE_UPLOAD_TRACKER_KEY = "certificateUploadTracker";

    private ToolCallContext() {
    }

    public static String requireEmployeeId(ToolContext toolContext) {
        Object nativeEmployeeId = toolContext == null
                ? null
                : toolContext.getContext().get(EMPLOYEE_ID_KEY);

        if (nativeEmployeeId instanceof String employeeId && !employeeId.isBlank()) {
            return employeeId;
        }

        Object mcpEmployeeId = McpToolUtils.getMcpExchange(toolContext)
                .map(exchange -> exchange.transportContext())
                .map(context -> context.get(EMPLOYEE_ID_KEY))
                .orElse(null);

        if (mcpEmployeeId instanceof String employeeId && !employeeId.isBlank()) {
            return employeeId;
        }

        throw new AppException(
                AuthErrorCode.TOKEN_MISSING,
                "Authenticated employee context is unavailable"
        );
    }

    public static void markDashboardChanged(ToolContext toolContext) {
        if (toolContext == null) {
            return;
        }

        Object value = toolContext.getContext().get(DASHBOARD_CHANGE_TRACKER_KEY);
        if (value instanceof DashboardChangeTracker tracker) {
            tracker.markChanged();
        }
    }

    public static void requestCertificateUpload(ToolContext toolContext, Long requestId) {
        if (toolContext == null || requestId == null) {
            return;
        }

        Object value = toolContext.getContext().get(CERTIFICATE_UPLOAD_TRACKER_KEY);
        if (value instanceof CertificateUploadTracker tracker) {
            tracker.requestUploadFor(requestId);
        }
    }

    public static final class DashboardChangeTracker {
        private final AtomicBoolean changed = new AtomicBoolean(false);

        public void markChanged() {
            changed.set(true);
        }

        public boolean hasChanged() {
            return changed.get();
        }
    }

    public static final class CertificateUploadTracker {
        private final AtomicReference<Long> requestId = new AtomicReference<>();

        public void requestUploadFor(Long requestedId) {
            requestId.set(requestedId);
        }

        public Long requestedRequestId() {
            return requestId.get();
        }
    }
}
