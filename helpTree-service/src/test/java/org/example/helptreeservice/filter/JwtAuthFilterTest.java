package org.example.helptreeservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.example.helptreeservice.util.TestJwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @Mock
    private FilterChain filterChain;

    private static final String SECRET = "testSecretKeyForFilterTesting123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtAuthFilter, "jwtSecret", SECRET);
    }

    private String generateToken(Long userId, String email, String role) {
        return TestJwtUtils.generateToken(userId, email, role, SECRET);
    }

    @Test
    void isPublicPath_shouldReturnTrueForAuthPaths() throws Exception {
        Method method = JwtAuthFilter.class.getDeclaredMethod("isPublicPath", String.class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/api/auth/login", "POST"));
        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/api/auth/register", "POST"));
        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/api/auth/refresh", "POST"));
    }

    @Test
    void isPublicPath_shouldReturnTrueForGetPosts() throws Exception {
        Method method = JwtAuthFilter.class.getDeclaredMethod("isPublicPath", String.class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/api/posts", "GET"));
        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/api/posts/1", "GET"));
        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/api/posts/123", "GET"));
    }

    @Test
    void isPublicPath_shouldReturnFalseForPutPosts() throws Exception {
        Method method = JwtAuthFilter.class.getDeclaredMethod("isPublicPath", String.class, String.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(jwtAuthFilter, "/api/posts/1", "PUT"));
        assertFalse((Boolean) method.invoke(jwtAuthFilter, "/api/posts/1", "DELETE"));
        assertFalse((Boolean) method.invoke(jwtAuthFilter, "/api/posts", "POST"));
    }

    @Test
    void isPublicPath_shouldReturnTrueForActuator() throws Exception {
        Method method = JwtAuthFilter.class.getDeclaredMethod("isPublicPath", String.class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/actuator/health", "GET"));
    }

    @Test
    void isPublicPath_shouldReturnTrueForWebSocket() throws Exception {
        Method method = JwtAuthFilter.class.getDeclaredMethod("isPublicPath", String.class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(jwtAuthFilter, "/ws/chat", "GET"));
    }

    @Test
    void filter_shouldRejectRequestWithoutToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/posts/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void filter_shouldPassForPublicPaths() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldSetAttributesForValidToken() throws ServletException, IOException {
        String token = generateToken(42L, "user@test.com", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/posts/1");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertEquals("42", request.getAttribute("X-User-Id"));
        assertEquals("USER", request.getAttribute("X-User-Role"));
        assertEquals("user@test.com", request.getAttribute("X-User-Email"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldRejectInternalPathsForNonAdmin() throws ServletException, IOException {
        String token = generateToken(1L, "user@test.com", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void filter_shouldAllowInternalPathsForAdmin() throws ServletException, IOException {
        String token = generateToken(1L, "admin@test.com", "ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
