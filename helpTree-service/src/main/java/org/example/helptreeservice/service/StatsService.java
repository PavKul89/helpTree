package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.dto.StatsDto;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final HelpRepository helpRepository;

    public StatsDto getStats() {
        long totalUsers = userRepository.count();
        long totalHelps = helpRepository.count();
        long activePosts = postRepository.countByStatus(PostStatus.OPEN);

        return StatsDto.builder()
                .totalUsers(totalUsers)
                .totalHelps(totalHelps)
                .activePosts(activePosts)
                .build();
    }
}
