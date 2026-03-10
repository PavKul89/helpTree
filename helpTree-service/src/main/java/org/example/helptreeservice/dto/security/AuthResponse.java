package org.example.helptreeservice.dto.security;

import org.example.helptreeservice.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    @Default
    private String type = "Bearer";
    private Long id;
    private String name;
    private String email;
    private Role role;
}
