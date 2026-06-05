package de.eberhardt.unlockcapture.audit

sealed interface AuditLogVerification {
    data object Empty : AuditLogVerification

    data class Ok(
        val entries: Int,
    ) : AuditLogVerification

    data class Tampered(
        val atLine: Int,
        val reason: String,
    ) : AuditLogVerification
}
