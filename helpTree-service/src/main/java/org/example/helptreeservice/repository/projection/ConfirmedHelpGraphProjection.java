package org.example.helptreeservice.repository.projection;

import org.example.helptreeservice.enums.HelpStatus;

import java.time.LocalDateTime;

/**
 * Typed projection of a confirmed, non-deleted help record used by the help
 * graph. The edge identity is {@code helpId}; a user id must never be used as
 * the edge id because the same helper can have several separate help acts.
 */
public interface ConfirmedHelpGraphProjection {

    Long getHelpId();

    Long getHelperId();

    String getHelperName();

    Long getReceiverId();

    String getReceiverName();

    Long getPostId();

    String getPostTitle();

    HelpStatus getStatus();

    LocalDateTime getConfirmedAt();
}
