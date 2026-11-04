package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.dto.StatsDto;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final List<HelpStatus> ACTIVE_STATUSES =
            List.of(HelpStatus.ACCEPTED, HelpStatus.COMPLETED);

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final HelpRepository helpRepository;

    @Transactional(readOnly = true)
    public StatsDto getStats() {
        long totalUsers = userRepository.countActive();
        long helpRecords = helpRepository.countActive();
        long confirmedHelps = helpRepository.countByStatus(HelpStatus.CONFIRMED);
        long activeHelps = helpRepository.countByStatusIn(ACTIVE_STATUSES);
        long cancelledHelps = helpRepository.countByStatus(HelpStatus.CANCELLED);
        long activePosts = postRepository.countByStatus(PostStatus.OPEN);

        return StatsDto.builder()
                .totalUsers(totalUsers)
                // Headline metric is the same confirmed set used by the graph.
                .totalHelps(confirmedHelps)
                .helpRecords(helpRecords)
                .activeHelps(activeHelps)
                .confirmedHelps(confirmedHelps)
                .cancelledHelps(cancelledHelps)
                // Every CONFIRMED act has exactly one receiver.
                .successfulReceipts(confirmedHelps)
                .activePosts(activePosts)
                .build();
    }
}
