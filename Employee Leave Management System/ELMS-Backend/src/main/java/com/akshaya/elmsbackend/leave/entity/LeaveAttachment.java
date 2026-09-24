package com.akshaya.elmsbackend.leave.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "leave_attachments")
public class LeaveAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest leaveRequest;

    @Column(name = "attachment_type", nullable = false, length = 30)
    private String attachmentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_data", nullable = false)
    private byte[] fileData;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected LeaveAttachment() {
    }

    public LeaveAttachment(LeaveRequest leaveRequest, String attachmentType, String fileName, String contentType, byte[] fileData) {
        this.leaveRequest = leaveRequest;
        this.attachmentType = attachmentType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileData = fileData;
        this.uploadedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LeaveRequest getLeaveRequest() {
        return leaveRequest;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
