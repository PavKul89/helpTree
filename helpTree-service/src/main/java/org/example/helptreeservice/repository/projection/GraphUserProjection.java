package org.example.helptreeservice.repository.projection;

/**
 * Public fields only. The graph never needs email, phone or moderation flags,
 * so loading a full {@code User} entity would move PII into the response path
 * for no reason.
 */
public interface GraphUserProjection {

    Long getId();

    String getName();

    String getAvatarUrl();

    Integer getHelpedCount();

    Integer getDebtCount();

    Double getRating();
}
