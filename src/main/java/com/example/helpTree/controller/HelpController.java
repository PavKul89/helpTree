package com.example.helpTree.controller;

import com.example.helpTree.dto.helps.HelpRequest;
import com.example.helpTree.dto.helps.HelpResponse;
import com.example.helpTree.service.HelpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/helps")
@RequiredArgsConstructor
public class HelpController {

    private final HelpService helpService;

    /**
     * 1. Откликнуться на пост (помочь)
     */
    @PostMapping("/accept")
    public ResponseEntity<HelpResponse> acceptHelp(@Valid @RequestBody HelpRequest request) {
        HelpResponse response = helpService.acceptHelp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. Отметить, что помощь выполнена (помощник)
     */
    @PostMapping("/{helpId}/complete")
    public ResponseEntity<HelpResponse> completeHelp(@PathVariable Long helpId) {
        return ResponseEntity.ok(helpService.completeHelp(helpId));
    }

    /**
     * 3. Подтвердить получение помощи (автор поста)
     * Здесь срабатывает правило пирамиды!
     */
    @PostMapping("/{helpId}/confirm")
    public ResponseEntity<HelpResponse> confirmHelp(@PathVariable Long helpId) {
        return ResponseEntity.ok(helpService.confirmHelp(helpId));
    }

    /**
     * 4. Отменить помощь
     */
    @PostMapping("/{helpId}/cancel")
    public ResponseEntity<HelpResponse> cancelHelp(@PathVariable Long helpId) {
        return ResponseEntity.ok(helpService.cancelHelp(helpId));
    }

    /**
     * Получить все помощи, где пользователь помогал
     */
    @GetMapping("/helper/{helperId}")
    public ResponseEntity<List<HelpResponse>> getHelpsByHelper(@PathVariable Long helperId) {
        return ResponseEntity.ok(helpService.getHelpsByHelper(helperId));
    }

    /**
     * Получить все помощи, где пользователю помогали
     */
    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<List<HelpResponse>> getHelpsByReceiver(@PathVariable Long receiverId) {
        return ResponseEntity.ok(helpService.getHelpsByReceiver(receiverId));
    }
}
