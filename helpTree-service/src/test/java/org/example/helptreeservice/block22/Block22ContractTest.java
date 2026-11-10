package org.example.helptreeservice.block22;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.comments.CommentPageResponse;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.error.ErrorResponse;
import org.example.helptreeservice.dto.error.FieldErrorDto;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.dto.posts.PostBoostResult;
import org.example.helptreeservice.dto.reports.AdminReportResponse;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.wallet.WalletPricingDto;
import org.example.helptreeservice.enums.ErrorType;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.util.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 22.9 - the wire contract the frontend codes against.
 *
 * <p>These are fast unit tests: they serialise the real DTO classes with the
 * same Jackson conventions as the API and assert the field names, primitive
 * types and stable enum values. No database or HTTP client is needed.</p>
 */
class Block22ContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void paginationEnvelopeKeepsTheStableFields() throws Exception {
        PageResponse<String> page = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));
        JsonNode json = toJson(page);

        assertTrue(json.has("content"));
        assertEquals(2, json.get("content").size());
        assertEquals(1, json.get("number").asInt());
        assertEquals(2, json.get("size").asInt());
        assertEquals(5, json.get("totalElements").asLong());
        assertEquals(3, json.get("totalPages").asInt());
        assertTrue(json.get("hasNext").asBoolean());
        assertEquals(2, json.get("numberOfElements").asInt());
        assertFalse(json.get("first").asBoolean());
        assertFalse(json.get("last").asBoolean());
        assertFalse(json.get("empty").asBoolean());

        JsonNode empty = toJson(PageResponse.empty(0, 20));
        assertEquals(0, empty.get("content").size());
        assertFalse(empty.get("hasNext").asBoolean());
        assertTrue(empty.get("empty").asBoolean());
    }

    @Test
    void commentPaginationUsesItsOwnEnvelope() throws Exception {
        CommentPageResponse page = CommentPageResponse.builder()
                .content(List.of())
                .page(2)
                .size(10)
                .totalElements(25)
                .totalPages(3)
                .hasMore(true)
                .totalComments(80)
                .build();

        JsonNode json = toJson(page);
        assertEquals(2, json.get("page").asInt());
        assertEquals(10, json.get("size").asInt());
        assertEquals(25, json.get("totalElements").asLong());
        assertEquals(3, json.get("totalPages").asInt());
        assertTrue(json.get("hasMore").asBoolean());
        assertEquals(80, json.get("totalComments").asLong());
    }

    @Test
    void helpStatusIsSerializedAsAStableString() throws Exception {
        HelpResponse response = HelpResponse.builder()
                .id(1L)
                .status("CONFIRMED")
                .acceptedAt(LocalDateTime.of(2026, 11, 10, 12, 0))
                .build();

        JsonNode json = toJson(response);
        assertTrue(json.get("status").isTextual());
        assertEquals("CONFIRMED", json.get("status").asText());
        assertTrue(json.get("acceptedAt").isTextual(),
                "Dates are ISO strings, never numeric timestamps");
    }

    @Test
    void theUserReportDtoNeverExposesInternalNotes() throws Exception {
        ReportResponse userReport = ReportResponse.builder()
                .id(7L)
                .status(ReportStatus.OPEN)
                .build();
        AdminReportResponse adminReport = AdminReportResponse.builder()
                .id(7L)
                .status(ReportStatus.OPEN)
                .internalNote("staff only")
                .build();

        JsonNode userJson = toJson(userReport);
        JsonNode adminJson = toJson(adminReport);

        assertFalse(userJson.has("internalNote"),
                "A user-facing report must not even expose an internalNote field");
        assertTrue(adminJson.has("internalNote"));
        assertEquals("staff only", adminJson.get("internalNote").asText());
        assertEquals("OPEN", userJson.get("status").asText(),
                "Status enums use their stable names, not ordinal numbers");
    }

    @Test
    void chatUnreadCountIsANumber() throws Exception {
        ChatResponse response = ChatResponse.builder()
                .id(3L)
                .unreadCount(12L)
                .build();
        JsonNode json = toJson(response);
        assertTrue(json.get("unreadCount").isNumber());
        assertEquals(12L, json.get("unreadCount").asLong());
    }

    @Test
    void walletAndBoostDtosExposeServerValues() throws Exception {
        WalletPricingDto pricing = WalletPricingDto.builder()
                .boostPrice(5L)
                .boostHours(24)
                .vipPrice(50L)
                .vipDays(30)
                .accountUnblockPrice(100L)
                .debtBlockThreshold(5)
                .dailyBonusCoins(1L)
                .build();
        JsonNode pricingJson = toJson(pricing);
        assertEquals(5L, pricingJson.get("boostPrice").asLong());
        assertEquals(50L, pricingJson.get("vipPrice").asLong());
        assertEquals(100L, pricingJson.get("accountUnblockPrice").asLong());
        assertEquals(5, pricingJson.get("debtBlockThreshold").asInt());

        PostBoostResult boost = new PostBoostResult(null, 5L, 95L, true,
                LocalDateTime.of(2026, 11, 11, 12, 0));
        JsonNode boostJson = toJson(boost);
        assertEquals(5L, boostJson.get("price").asLong());
        assertEquals(95L, boostJson.get("balance").asLong());
        assertTrue(boostJson.get("applied").asBoolean());
        assertTrue(boostJson.get("boostedUntil").isTextual());
    }

    @Test
    void theErrorEnvelopeHasEveryContractField() throws Exception {
        ErrorResponse error = ErrorResponse.builder()
                .status(400)
                .code(ErrorType.VALIDATION_FAILED.getCode())
                .message("Invalid request")
                .traceId("trace-1")
                .fieldErrors(List.of(new FieldErrorDto("rating", "must be between 1 and 5", "Min")))
                .path("/api/reviews")
                .build();

        JsonNode json = toJson(error);
        assertTrue(json.get("timestamp").isTextual());
        assertEquals(400, json.get("status").asInt());
        assertEquals("VALIDATION_FAILED", json.get("code").asText());
        assertEquals("Invalid request", json.get("message").asText());
        assertEquals("trace-1", json.get("traceId").asText());
        assertEquals("/api/reviews", json.get("path").asText());
        JsonNode fieldErrors = json.get("fieldErrors");
        assertTrue(fieldErrors.isArray());
        assertEquals("rating", fieldErrors.get(0).get("field").asText());
        assertEquals("must be between 1 and 5", fieldErrors.get(0).get("message").asText());
        assertEquals("Min", fieldErrors.get(0).get("code").asText());
    }

    @Test
    void filtersWriteTheSameErrorEnvelopeAsMvc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reports");
        request.addHeader("X-Request-Id", "contract-test-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiErrorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                ErrorType.TOO_MANY_REQUESTS, "Too many requests",
                List.of(new FieldErrorDto("limit", "exceeded", null)));

        assertEquals(429, response.getStatus());
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertEquals(429, json.get("status").asInt());
        assertEquals("TOO_MANY_REQUESTS", json.get("code").asText());
        assertEquals("Too many requests", json.get("message").asText());
        assertEquals("/api/reports", json.get("path").asText());
        assertTrue(json.get("traceId").isTextual());
        assertEquals(1, json.get("fieldErrors").size());
    }

    private JsonNode toJson(Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }
}
