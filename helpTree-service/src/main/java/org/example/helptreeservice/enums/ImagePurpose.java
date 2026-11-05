package org.example.helptreeservice.enums;

/**
 * Why an object was uploaded. Purpose is advisory for quotas and cleanup, but
 * it is also used to stop an avatar from silently becoming a post attachment.
 */
public enum ImagePurpose {
    AVATAR,
    POST,
    GENERAL
}
