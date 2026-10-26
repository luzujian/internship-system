package com.internship.importing;

import lombok.Data;

/** Database-owned identity, immutable source and fenced mutable import state. */
@Data
public class SmartImportWorkspaceRecord {
    private String id, ownerKey, sessionId, sourceRef, sourceSha256, sourceJson, filesJson, stateJson;
    private String leaseToken, leaseTurnId, cancelledTurnId;
    private long version, createdAt, updatedAt, leaseUntil;
}
