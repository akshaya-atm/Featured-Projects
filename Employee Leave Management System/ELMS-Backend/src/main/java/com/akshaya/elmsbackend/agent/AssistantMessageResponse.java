package com.akshaya.elmsbackend.agent;

public record AssistantMessageResponse(
        String reply,
        String conversationId,
        boolean dashboardChanged,
        CertificateUploadAction certificateUpload
) {
    public record CertificateUploadAction(Long requestId) {
    }
}
