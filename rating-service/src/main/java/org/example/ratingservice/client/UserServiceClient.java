package org.example.ratingservice.client;

import org.example.ratingservice.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "helpTree-service", url = "${helptree.service.url:http://localhost:8081}")
public interface UserServiceClient {

    @GetMapping("/api/users/{id}/public")
    UserDto getUserById(@PathVariable("id") Long id);

    @PostMapping("/api/users/public/batch")
    List<UserDto> getUsersByIds(@RequestBody List<Long> ids);

    @GetMapping("/api/users")
    List<UserDto> getAllUsers();

    @PutMapping("/internal/users/{id}/rating")
    void updateUserRating(@PathVariable("id") Long id, @RequestParam("rating") Double rating);
}
