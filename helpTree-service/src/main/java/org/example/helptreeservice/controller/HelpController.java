package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.graph.HelpGraphDto;
import org.example.helptreeservice.dto.graph.PublicHelpGraphDto;
import org.example.helptreeservice.dto.helps.CancelHelpRequest;
import org.example.helptreeservice.dto.helps.HelpHistoryEntryDto;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.HelpService;
import org.example.helptreeservice.service.PrivacyPolicy;
import org.example.helptreeservice.util.PageRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/helps")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Helps & Graph", description = "Отклики, история помощи и публичный граф связей")
public class HelpController {

    private final HelpService helpService;
    private final AuthorizationService authService;
    private final PrivacyPolicy privacyPolicy;
    private final PaginationConfig paginationConfig;

    @PostMapping("/accept")
    @Operation(summary = "Откликнуться на заявку",
            description = "Создаёт запись помощи. Требуется авторизация; автор не может откликнуться на свою заявку.")
    public ResponseEntity<HelpResponse> acceptHelp(@Valid @RequestBody HelpRequest request) {
        Long actorId = requireCurrentUserId("Для отклика на пост необходимо войти в систему");
        HelpResponse response = helpService.acceptHelp(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "Отклики на заявку (постранично)",
            description = "Возвращает PageResponse. Видимость откликов проверяется сервером.")
    public ResponseEntity<PageResponse<HelpResponse>> getHelpsByPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = responsePageable(page, size);
        return ResponseEntity.ok(PageResponse.from(helpService.getHelpsByPost(postId, pageable)));
    }

    @PostMapping("/{helpId}/complete")
    @Operation(summary = "Отметить помощь выполненной",
            description = "Исполнитель или ADMIN. Идемпотентная смена состояния.")
    public ResponseEntity<HelpResponse> completeHelp(@PathVariable Long helpId,
                                                     @RequestParam(required = false) String reason) {
        Long actorId = requireCurrentUserId("Для завершения помощи необходимо войти в систему");
        boolean admin = authService.isAdmin();
        HelpResponse response = admin
                ? helpService.completeHelp(helpId, actorId, true, reason)
                : helpService.completeHelp(helpId, actorId, false);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{helpId}/confirm")
    @Operation(summary = "Подтвердить выполнение помощи",
            description = "Получатель помощи или ADMIN. Начисление монет выполняется сервером.")
    public ResponseEntity<HelpResponse> confirmHelp(@PathVariable Long helpId,
                                                    @RequestParam(required = false) String reason) {
        Long actorId = requireCurrentUserId("Для подтверждения помощи необходимо войти в систему");
        boolean admin = authService.isAdmin();
        HelpResponse response = admin
                ? helpService.confirmHelp(helpId, actorId, true, reason)
                : helpService.confirmHelp(helpId, actorId, false);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{helpId}/cancel")
    @Operation(summary = "Отменить помощь")
    public ResponseEntity<HelpResponse> cancelHelp(
            @PathVariable Long helpId,
            @RequestBody(required = false) @Valid CancelHelpRequest request) {
        Long actorId = requireCurrentUserId("Для отмены помощи необходимо войти в систему");
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(helpService.cancelHelp(
                helpId, actorId, authService.isAdmin(), reason));
    }

    @GetMapping("/{helpId}/history")
    @Operation(summary = "История изменений помощи")
    public ResponseEntity<List<HelpHistoryEntryDto>> getHelpHistory(@PathVariable Long helpId) {
        Long actorId = requireCurrentUserId("Для просмотра истории помощи необходимо войти в систему");
        return ResponseEntity.ok(helpService.getHelpHistory(helpId, actorId, authService.isAdmin()));
    }

    @GetMapping("/helper/{helperId}")
    public ResponseEntity<PageResponse<HelpResponse>> getHelpsByHelper(
            @PathVariable Long helperId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (!privacyPolicy.canViewHelp(helperId, null)) {
            throw new ForbiddenException("Вы можете просматривать только свои записи");
        }
        Page<HelpResponse> helps = helpService.getHelpsByHelper(helperId, responsePageable(page, size));
        return ResponseEntity.ok(PageResponse.from(helps));
    }

    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<PageResponse<HelpResponse>> getHelpsByReceiver(
            @PathVariable Long receiverId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (!privacyPolicy.canViewHelp(null, receiverId)) {
            throw new ForbiddenException("Вы можете просматривать только свои записи");
        }
        Page<HelpResponse> helps = helpService.getHelpsByReceiver(receiverId, responsePageable(page, size));
        return ResponseEntity.ok(PageResponse.from(helps));
    }

    @GetMapping("/graph")
    @Operation(summary = "Граф связей помощи",
            description = "Публичный граф обезличивается. Приватный граф (с именами и деталями) "
                    + "возвращается только владельцу или ADMIN. Параметры depth и maxEdges ограничивают размер.")
    @ApiResponse(responseCode = "200", description = "Приватный или публичный граф",
            content = @Content(schema = @Schema(oneOf = {HelpGraphDto.class, PublicHelpGraphDto.class})))
    @SecurityRequirements
    public ResponseEntity<?> getHelpGraph(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer depth,
            @RequestParam(required = false) Integer maxEdges) {
        HelpGraphDto graph = helpService.getHelpGraph(userId, depth, maxEdges);
        if (privacyPolicy.canViewPrivateGraph(userId)) {
            return ResponseEntity.ok(graph);
        }
        return ResponseEntity.ok(helpService.toPublicGraph(graph));
    }

    @GetMapping("/stats")
    @Operation(summary = "Публичная статистика помощи")
    @SecurityRequirements
    public ResponseEntity<org.example.helptreeservice.dto.graph.HelpStatsDto> getHelpStats() {
        return ResponseEntity.ok(helpService.getHelpStats());
    }

    @GetMapping("/new-responses/count")
    public ResponseEntity<Long> getNewResponsesCount(@RequestParam(required = false) String since) {
        Long actorId = requireCurrentUserId("Необходимо войти в систему");
        return ResponseEntity.ok(helpService.getNewResponsesCount(actorId, since));
    }

    @GetMapping("/new-responses")
    @Operation(summary = "Новые отклики на мои заявки")
    public ResponseEntity<List<org.example.helptreeservice.dto.helps.NewResponseDto>> getNewResponses(
            @RequestParam(required = false) String since) {
        Long actorId = requireCurrentUserId("Необходимо войти в систему");
        return ResponseEntity.ok(helpService.getNewResponses(actorId, since));
    }

    private Pageable responsePageable(Integer page, Integer size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequests.of(page, size, paginationConfig, sort);
    }

    private Long requireCurrentUserId(String message) {
        var currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException(message);
        }
        return currentUser.getUserId();
    }
}
