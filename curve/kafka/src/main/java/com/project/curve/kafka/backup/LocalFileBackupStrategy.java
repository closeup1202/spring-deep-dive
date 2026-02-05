package com.project.curve.kafka.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Local file system implementation of EventBackupStrategy.
 * <p>
 * Backs up failed events to a local directory with secure permissions.
 */
@Slf4j
public class LocalFileBackupStrategy implements EventBackupStrategy {

    private final String backupPath;
    private final ObjectMapper objectMapper;
    private final boolean isProduction;

    public LocalFileBackupStrategy(String backupPath, ObjectMapper objectMapper, boolean isProduction) {
        this.backupPath = backupPath != null ? backupPath : "./dlq-backup";
        this.objectMapper = objectMapper;
        this.isProduction = isProduction;
    }

    @Override
    public void backup(String eventId, Object payload, Throwable cause) {
        try {
            Path backupDir = Paths.get(backupPath);
            Files.createDirectories(backupDir);

            Path backupFile = backupDir.resolve(eventId + ".json");
            String content = serializeContent(payload);

            // Write file
            Files.writeString(backupFile, content, StandardOpenOption.CREATE);

            // Apply security permissions
            boolean securityApplied = applyFilePermissions(backupFile);

            if (!securityApplied) {
                handleSecurityFailure(eventId, backupFile);
            } else {
                log.error("Event backed up to file with restricted permissions: eventId={}, file={}", eventId, backupFile);
            }

        } catch (IOException e) {
            log.error("Failed to backup event to file: eventId={}", eventId, e);
            log.error("Event permanently lost. eventId={}, cause={}", eventId, e.getMessage());
        }
    }

    private String serializeContent(Object originalValue) {
        if (originalValue instanceof String) {
            return (String) originalValue;
        }
        try {
            return objectMapper.writeValueAsString(originalValue);
        } catch (Exception e) {
            log.warn("Failed to serialize value as JSON, using toString()", e);
            return String.valueOf(originalValue);
        }
    }

    private boolean applyFilePermissions(Path file) {
        Set<String> supportedViews = FileSystems.getDefault().supportedFileAttributeViews();

        if (supportedViews.contains("posix")) {
            return applyPosixPermissions(file);
        } else if (supportedViews.contains("acl")) {
            return applyWindowsAclPermissions(file);
        } else {
            log.warn("File system does not support POSIX or ACL. File permissions cannot be restricted: {}", file);
            return false;
        }
    }

    private boolean applyPosixPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file, perms);
            log.debug("Applied POSIX permissions (600) to file: {}", file);
            return true;
        } catch (Exception e) {
            log.error("Failed to set POSIX permissions on file: {}", file, e);
            return false;
        }
    }

    private boolean applyWindowsAclPermissions(Path file) {
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (aclView == null) {
                log.error("Failed to get ACL view for file: {}", file);
                return false;
            }

            UserPrincipal owner = Files.getOwner(file);
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(
                            EnumSet.of(
                                    AclEntryPermission.READ_DATA,
                                    AclEntryPermission.WRITE_DATA,
                                    AclEntryPermission.APPEND_DATA,
                                    AclEntryPermission.READ_ATTRIBUTES,
                                    AclEntryPermission.WRITE_ATTRIBUTES,
                                    AclEntryPermission.READ_ACL,
                                    AclEntryPermission.SYNCHRONIZE
                            )
                    )
                    .build();

            aclView.setAcl(Collections.singletonList(entry));
            log.debug("Applied Windows ACL permissions (owner-only) to file: {}", file);
            return true;
        } catch (Exception e) {
            log.error("Failed to set Windows ACL permissions on file: {}", file, e);
            return false;
        }
    }

    private void handleSecurityFailure(String eventId, Path backupFile) {
        String errorMessage = String.format(
                "Failed to apply secure file permissions. " +
                        "File may be accessible to unauthorized users: %s. " +
                        "Please configure file system security manually or use a POSIX/ACL-compliant file system.",
                backupFile);

        if (isProduction) {
            log.error("SECURITY VIOLATION in production: {}", errorMessage);
            throw new IllegalStateException(
                    "Cannot backup event with insecure file permissions in production environment. " +
                            "EventId: " + eventId + ". " + errorMessage);
        } else {
            log.warn("Event backed up without secure permissions (development mode): eventId={}, file={}",
                    eventId, backupFile);
            log.warn(errorMessage);
        }
    }
}
