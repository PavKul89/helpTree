package org.example.helptreeservice.enums;

/**
 * Who initiated a help lifecycle transition. Stored on every status history entry
 * and on a cancellation so the audit trail survives role changes.
 */
public enum HelpActorRole {
    HELPER,
    RECEIVER,
    ADMIN,
    SYSTEM
}
