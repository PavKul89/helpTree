package org.example.helptreeservice.dto.auth;

import lombok.Data;

@Data
public class LogoutRequest {

    /**
     * Optional. When the caller is authenticated the access token already identifies
     * the session; a refresh token may be supplied as an additional proof and must
     * belong to that same session.
     */
    private String refreshToken;
}
