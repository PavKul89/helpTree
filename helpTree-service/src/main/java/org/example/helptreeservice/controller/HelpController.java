package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.helps.CancelHelpRequest;
import org.example.helptreeservice.dto.helps.HelpHistoryEntryDto;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.HelpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/helps")
@Slf4j
@RequiredArgsConstructor
public class HelpController {

    private final HelpService helpService;
    private final AuthorizationService authService;

    @PostMapping("/accept")
    public ResponseEntity<HelpResponse> acceptHelp(@Valid @RequestBody HelpRequest request) {
        Long actorId = requireCurrentUserId("Для отклика на пост необходимо войти в систему");
        HelpResponse response = helpService.acceptHelp(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<HelpResponse>> getHelpsByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(helpService.getHelpsByPost(postId));
    }

    @PostMapping("/{helpId}/complete")
    public ResponseEntity<HelpResponse> completeHelp(@PathVariable Long helpId) {
        Long actorId = requireCurrentUserId("Для завершения помощи необходимо войти в систему");
        return ResponseEntity.ok(helpService.completeHelp(helpId, actorId, authService.isAdmin()));
    }

    @PostMapping("/{helpId}/confirm")
    public ResponseEntity<HelpResponse> confirmHelp(@PathVariable Long helpId) {
        Long actorId = requireCurrentUserId("Для подтверждения помощи необходимо войти в систему");
        return ResponseEntity.ok(helpService.confirmHelp(helpId, actorId, authService.isAdmin()));
    }

    @PostMapping("/{helpId}/cancel")
    public ResponseEntity<HelpResponse> cancelHelp(
            @PathVariable Long helpId,
            @RequestBody(required = false) @Valid CancelHelpRequest request) {
        Long actorId = requireCurrentUserId("Для отмены помощи необходимо войти в систему");
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(helpService.cancelHelp(
                helpId, actorId, authService.isAdmin(), reason));
    }

    @GetMapping("/{helpId}/history")
    public ResponseEntity<List<HelpHistoryEntryDto>> getHelpHistory(@PathVariable Long helpId) {
        Long actorId = requireCurrentUserId("Для просмотра истории помощи необходимо войти в систему");
        return ResponseEntity.ok(helpService.getHelpHistory(helpId, actorId, authService.isAdmin()));
    }

    @GetMapping("/helper/{helperId}")
    public ResponseEntity<Page<HelpResponse>> getHelpsByHelper(
            @PathVariable Long helperId,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {
        if (!authService.canAccessHelp(helperId, null)) {
            throw new ForbiddenException("Вы можете просматривать только свои записи");
        }
        return ResponseEntity.ok(helpService.getHelpsByHelper(helperId, pageable));
    }

    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<Page<HelpResponse>> getHelpsByReceiver(
            @PathVariable Long receiverId,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {
        if (!authService.canAccessHelp(null, receiverId)) {
            throw new ForbiddenException("Вы можете просматривать только свои записи");
        }
        return ResponseEntity.ok(helpService.getHelpsByReceiver(receiverId, pageable));
    }

    @GetMapping("/graph")
    public ResponseEntity<org.example.helptreeservice.dto.graph.HelpGraphDto> getHelpGraph(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer depth,
            @RequestParam(required = false) Integer maxEdges) {
        return ResponseEntity.ok(helpService.getHelpGraph(userId, depth, maxEdges));
    }

    @GetMapping("/stats")
    public ResponseEntity<org.example.helptreeservice.dto.graph.HelpStatsDto> getHelpStats() {
        return ResponseEntity.ok(helpService.getHelpStats());
    }

    @GetMapping("/new-responses/count")
    public ResponseEntity<Long> getNewResponsesCount(@RequestParam(required = false) String since) {
        Long actorId = requireCurrentUserId("Необходимо войти в систему");
        return ResponseEntity.ok(helpService.getNewResponsesCount(actorId, since));
    }

    @GetMapping("/new-responses")
    public ResponseEntity<List<org.example.helptreeservice.dto.helps.NewResponseDto>> getNewResponses(
            @RequestParam(required = false) String since) {
        Long actorId = requireCurrentUserId("Необходимо войти в систему");
        return ResponseEntity.ok(helpService.getNewResponses(actorId, since));
    }

    private Long requireCurrentUserId(String message) {
        var currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException(message);
        }
        return currentUser.getUserId();
    }
}
