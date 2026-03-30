package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.HelpService;
import org.example.helptreeservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UserService userService;

    @PostMapping("/accept")
    public ResponseEntity<HelpResponse> acceptHelp(@Valid @RequestBody HelpRequest request) {
        var currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException("Для отклика на пост необходимо войти в систему");
        }
        if (userService.isUserBlocked(currentUser.getUserId())) {
            throw new ForbiddenException("Ваш аккаунт заблокирован за долг. Помогите другим пользователям, чтобы разблокировать аккаунт.");
        }
        HelpResponse response = helpService.acceptHelp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<HelpResponse>> getHelpsByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(helpService.getHelpsByPost(postId));
    }

    @PostMapping("/{helpId}/complete")
    public ResponseEntity<HelpResponse> completeHelp(@PathVariable Long helpId) {
        Help help = helpService.getHelpById(helpId);
        Long helperId = help.getHelper().getId();
        Long receiverId = help.getReceiver().getId();
        if (!authService.canAccessHelp(helperId, receiverId)) {
            throw new ForbiddenException("Вы можете завершить только свою помощь");
        }
        return ResponseEntity.ok(helpService.completeHelp(helpId));
    }

    @PostMapping("/{helpId}/confirm")
    public ResponseEntity<HelpResponse> confirmHelp(@PathVariable Long helpId) {
        Help help = helpService.getHelpById(helpId);
        Long helperId = help.getHelper().getId();
        Long receiverId = help.getReceiver().getId();
        if (!authService.canAccessHelp(helperId, receiverId)) {
            throw new ForbiddenException("Вы можете подтвердить только полученную вами помощь");
        }
        return ResponseEntity.ok(helpService.confirmHelp(helpId));
    }

    @PostMapping("/{helpId}/cancel")
    public ResponseEntity<HelpResponse> cancelHelp(@PathVariable Long helpId) {
        Help help = helpService.getHelpById(helpId);
        Long helperId = help.getHelper().getId();
        Long receiverId = help.getReceiver().getId();
        if (!authService.canAccessHelp(helperId, receiverId)) {
            throw new ForbiddenException("Вы можете отменить только свою помощь");
        }
        return ResponseEntity.ok(helpService.cancelHelp(helpId));
    }

    @GetMapping("/helper/{helperId}")
    public ResponseEntity<List<HelpResponse>> getHelpsByHelper(@PathVariable Long helperId) {
        if (!authService.canAccessHelp(helperId, null)) {
            throw new ForbiddenException("Вы можете просматривать только свои записи");
        }
        return ResponseEntity.ok(helpService.getHelpsByHelper(helperId));
    }

    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<List<HelpResponse>> getHelpsByReceiver(@PathVariable Long receiverId) {
        if (!authService.canAccessHelp(null, receiverId)) {
            throw new ForbiddenException("Вы можете просматривать только свои записи");
        }
        return ResponseEntity.ok(helpService.getHelpsByReceiver(receiverId));
    }

    @GetMapping("/graph")
    public ResponseEntity<org.example.helptreeservice.dto.graph.HelpGraphDto> getHelpGraph(
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(helpService.getHelpGraph(userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<org.example.helptreeservice.dto.graph.HelpStatsDto> getHelpStats() {
        return ResponseEntity.ok(helpService.getHelpStats());
    }

    @GetMapping("/new-responses/count")
    public ResponseEntity<Long> getNewResponsesCount(@RequestParam(required = false) String since) {
        var currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException("Необходимо войти в систему");
        }
        return ResponseEntity.ok(helpService.getNewResponsesCount(currentUser.getUserId(), since));
    }

    @GetMapping("/new-responses")
    public ResponseEntity<List<org.example.helptreeservice.dto.helps.NewResponseDto>> getNewResponses(@RequestParam(required = false) String since) {
        try {
            var currentUser = authService.getCurrentUser();
            if (currentUser == null) {
                throw new ForbiddenException("Необходимо войти в систему");
            }
            return ResponseEntity.ok(helpService.getNewResponses(currentUser.getUserId(), since));
        } catch (Exception e) {
            log.error("Error getting new responses: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }
}