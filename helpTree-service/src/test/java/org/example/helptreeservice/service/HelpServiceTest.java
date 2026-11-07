package org.example.helptreeservice.service;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.helps.HelpHistoryEntryDto;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.HelpStatusHistory;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpActorRole;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.mapper.HelpMapper;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.HelpStatusHistoryRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.repository.projection.GraphUserProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelpServiceTest {

    @Mock private HelpRepository helpRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private HelpMapper helpMapper;
    @Mock private RatingService ratingService;
    @Mock private AchievementService achievementService;
    @Mock private WalletService walletService;
    @Mock private ImageService imageService;
    @Mock private HelpStatusHistoryRepository statusHistoryRepository;
    @Mock private HelpGraphMetrics helpGraphMetrics;
    @Mock private UserBlockService userBlockService;

    private HelpService helpService;

    @BeforeEach
    void setUp() {
        EconomyConfig economyConfig = new EconomyConfig();
        helpService = new HelpService(
                helpRepository,
                postRepository,
                userRepository,
                userService,
                helpMapper,
                ratingService,
                achievementService,
                walletService,
                imageService,
                economyConfig,
                statusHistoryRepository,
                helpGraphMetrics,
                userBlockService
        );

        lenient().when(helpMapper.toResponse(any(Help.class))).thenAnswer(invocation -> {
            Help help = invocation.getArgument(0);
            return HelpResponse.builder()
                    .id(help.getId())
                    .status(help.getStatus().name())
                    .cancelReason(help.getCancelReason())
                    .cancelInitiator(help.getCancelInitiator() != null
                            ? help.getCancelInitiator().name() : null)
                    .build();
        });
        lenient().when(helpRepository.saveAndFlush(any(Help.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(helpRepository.save(any(Help.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.setField(helpService, "defaultMaxEdges", 500);
        ReflectionTestUtils.setField(helpService, "hardMaxEdges", 2000);
        ReflectionTestUtils.setField(helpService, "maxDepth", 10);
    }

    @Test
    void acceptHelp_shouldLockPostAndCreateSingleActiveHelp() {
        User author = user(2L);
        User helper = user(1L);
        Post post = post(10L, PostStatus.OPEN, author);
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(helper));
        when(helpRepository.existsByPostAndHelper(post, helper)).thenReturn(false);
        when(helpRepository.existsByPostIdAndStatusIn(eq(10L), anyList())).thenReturn(false);

        HelpResponse response = helpService.acceptHelp(request(10L), 1L);

        assertEquals("ACCEPTED", response.getStatus());
        assertEquals(PostStatus.IN_PROGRESS, post.getStatus());
        assertSame(helper, post.getHelper());
        verify(postRepository).findByIdForUpdate(10L);
        verify(helpRepository).saveAndFlush(any(Help.class));
        ArgumentCaptor<HelpStatusHistory> history = ArgumentCaptor.forClass(HelpStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertEquals(HelpStatus.ACCEPTED, history.getValue().getToStatus());
        assertEquals(HelpActorRole.HELPER, history.getValue().getActorRole());
        verify(ratingService).recalculateUsers(List.of(1L));
    }

    @Test
    void acceptHelp_shouldRejectAuthorHelpingHimself() {
        User author = user(1L);
        Post post = post(10L, PostStatus.OPEN, author);
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        assertThrows(BadRequestException.class, () -> helpService.acceptHelp(request(10L), 1L));
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void acceptHelp_shouldRejectWhenHelpRequestBlocked() {
        User author = user(2L);
        User helper = user(1L);
        Post post = post(10L, PostStatus.OPEN, author);
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(helper));
        doThrow(new ForbiddenException("blocked")).when(userBlockService)
                .assertInteractionAllowed(eq(1L), eq(2L), eq(UserBlockScope.HELP_REQUESTS), anyString());

        assertThrows(ForbiddenException.class, () -> helpService.acceptHelp(request(10L), 1L));
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void acceptHelp_shouldRejectWhenActiveHelpAlreadyExists() {
        User author = user(2L);
        User helper = user(1L);
        Post post = post(10L, PostStatus.OPEN, author);
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(helper));
        when(helpRepository.existsByPostAndHelper(post, helper)).thenReturn(false);
        when(helpRepository.existsByPostIdAndStatusIn(eq(10L), anyList())).thenReturn(true);

        assertThrows(ConflictException.class, () -> helpService.acceptHelp(request(10L), 1L));
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void acceptHelp_shouldRejectBlockedHelper() {
        User author = user(2L);
        User helper = user(1L);
        helper.setBlockedUntil(LocalDateTime.now().plusDays(1));
        Post post = post(10L, PostStatus.OPEN, author);
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(helper));

        assertThrows(ForbiddenException.class, () -> helpService.acceptHelp(request(10L), 1L));
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void acceptHelp_shouldRejectLegacyBlockedHelper() {
        User author = user(2L);
        User helper = user(1L);
        helper.setBlockedAt(LocalDateTime.now().minusDays(30));
        Post post = post(10L, PostStatus.OPEN, author);
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(helper));

        assertThrows(ForbiddenException.class, () -> helpService.acceptHelp(request(10L), 1L));
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void acceptHelp_shouldRejectReceiverOverDebtThreshold() {
        User author = user(2L);
        author.setDebtCount(6);
        author.setHelpedCount(0);
        User helper = user(1L);
        Post post = post(10L, PostStatus.OPEN, author);
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(helper));

        assertThrows(BadRequestException.class, () -> helpService.acceptHelp(request(10L), 1L));
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void completeHelp_shouldRejectReceiver() {
        Help help = help(5L, HelpStatus.ACCEPTED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        assertThrows(ForbiddenException.class,
                () -> helpService.completeHelp(5L, 2L, false));
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
        verifyNoInteractions(ratingService, walletService, achievementService);
    }

    @Test
    void completeHelp_shouldTransitionStatusAndRefreshProjectionWithoutAwards() {
        Help help = help(5L, HelpStatus.ACCEPTED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        HelpResponse response = helpService.completeHelp(5L, 1L, false);

        assertEquals("COMPLETED", response.getStatus());
        assertNotNull(help.getCompletedAt());
        verify(ratingService).recalculateUsers(List.of(1L));
        verifyNoInteractions(walletService, achievementService);
        verify(statusHistoryRepository).save(any(HelpStatusHistory.class));
    }

    @Test
    void completeHelp_shouldBeIdempotentWhenAlreadyCompleted() {
        Help help = help(5L, HelpStatus.COMPLETED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        HelpResponse response = helpService.completeHelp(5L, 1L, false);

        assertEquals("COMPLETED", response.getStatus());
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
        verify(statusHistoryRepository, never()).save(any(HelpStatusHistory.class));
        verifyNoInteractions(ratingService);
    }

    @Test
    void confirmHelp_shouldRejectHelper() {
        Help help = help(5L, HelpStatus.COMPLETED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        assertThrows(ForbiddenException.class,
                () -> helpService.confirmHelp(5L, 1L, false));
        verifyNoInteractions(walletService, achievementService, ratingService);
    }

    @Test
    void confirmHelp_shouldFinalizeExactlyOnce() {
        User helper = user(1L);
        User receiver = user(2L);
        Help help = help(5L, HelpStatus.COMPLETED, helper, receiver);
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));
        when(userRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(helper, receiver));

        HelpResponse response = helpService.confirmHelp(5L, 2L, false);

        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(PostStatus.COMPLETED, help.getPost().getStatus());
        verify(walletService).addCoinsForHelp(1L, 2L, 10L);
        verify(walletService).addCoinsForReceivedHelp(2L, 1L, 10L);
        verify(userService).incrementHelpedCount(2L);
        verify(userService).userHelpedSomeone(1L);
        verify(achievementService).checkAndAwardAchievements(helper, help);
        verify(ratingService, times(1)).recalculateUsers(List.of(1L, 2L));
        verify(statusHistoryRepository).save(any(HelpStatusHistory.class));
    }

    @Test
    void confirmHelp_shouldRejectBlockedReceiver() {
        User helper = user(1L);
        User receiver = user(2L);
        receiver.setBlockedUntil(LocalDateTime.now().plusDays(1));
        Help help = help(5L, HelpStatus.COMPLETED, helper, receiver);
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));
        when(userRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(helper, receiver));

        assertThrows(BadRequestException.class,
                () -> helpService.confirmHelp(5L, 2L, false));
        assertEquals(HelpStatus.COMPLETED, help.getStatus());
        verifyNoInteractions(walletService, achievementService, ratingService, userService);
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void confirmHelp_shouldNotAwardAgainWhenAlreadyConfirmed() {
        Help help = help(5L, HelpStatus.CONFIRMED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        HelpResponse response = helpService.confirmHelp(5L, 2L, false);

        assertEquals("CONFIRMED", response.getStatus());
        verifyNoInteractions(walletService, achievementService, ratingService, userService);
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
    }

    @Test
    void confirmHelp_shouldRejectAcceptedHelp() {
        Help help = help(5L, HelpStatus.ACCEPTED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        assertThrows(ConflictException.class,
                () -> helpService.confirmHelp(5L, 2L, false));
        verifyNoInteractions(walletService, achievementService, ratingService);
    }

    @Test
    void cancelHelp_helperMayCancelCompletedAndPostReopens() {
        User helper = user(1L);
        User receiver = user(2L);
        Help help = help(5L, HelpStatus.COMPLETED, helper, receiver);
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        HelpResponse response = helpService.cancelHelp(5L, 1L, false, "Не смогу");

        assertEquals("CANCELLED", response.getStatus());
        assertEquals("Не смогу", help.getCancelReason());
        assertEquals(HelpActorRole.HELPER, help.getCancelInitiator());
        assertEquals(PostStatus.OPEN, help.getPost().getStatus());
        assertNull(help.getPost().getHelper());
        verify(ratingService).recalculateUsers(List.of(1L));
        verifyNoInteractions(walletService, achievementService);
        verify(statusHistoryRepository).save(any(HelpStatusHistory.class));
    }

    @Test
    void cancelHelp_receiverCannotCancelCompleted() {
        Help help = help(5L, HelpStatus.COMPLETED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        assertThrows(ForbiddenException.class,
                () -> helpService.cancelHelp(5L, 2L, false, null));
        verifyNoInteractions(ratingService);
    }

    @Test
    void cancelHelp_adminMustProvideReason() {
        Help help = help(5L, HelpStatus.ACCEPTED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        assertThrows(BadRequestException.class,
                () -> helpService.cancelHelp(5L, 99L, true, "  "));
        verifyNoInteractions(ratingService);
    }

    @Test
    void cancelHelp_shouldBeIdempotent() {
        Help help = help(5L, HelpStatus.CANCELLED, user(1L), user(2L));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        HelpResponse response = helpService.cancelHelp(5L, 1L, false, null);

        assertEquals("CANCELLED", response.getStatus());
        verifyNoInteractions(ratingService, walletService, achievementService);
        verify(helpRepository, never()).saveAndFlush(any(Help.class));
        verify(statusHistoryRepository, never()).save(any(HelpStatusHistory.class));
    }

    @Test
    void cancelStaleAcceptedHelp_shouldOnlyCancelExpiredAccepted() {
        Help help = help(5L, HelpStatus.ACCEPTED, user(1L), user(2L));
        help.setAcceptedAt(LocalDateTime.now().minusDays(10));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        boolean cancelled = helpService.cancelStaleAcceptedHelp(
                5L, LocalDateTime.now().minusDays(7));

        assertTrue(cancelled);
        assertEquals(HelpStatus.CANCELLED, help.getStatus());
        assertEquals(HelpActorRole.SYSTEM, help.getCancelInitiator());
        assertNull(help.getCancelledById());
        verify(ratingService).recalculateUsers(List.of(1L));
    }

    @Test
    void cancelStaleAcceptedHelp_shouldSkipNonExpiredHelp() {
        Help help = help(5L, HelpStatus.ACCEPTED, user(1L), user(2L));
        help.setAcceptedAt(LocalDateTime.now().minusDays(1));
        when(helpRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(help));

        boolean cancelled = helpService.cancelStaleAcceptedHelp(
                5L, LocalDateTime.now().minusDays(7));

        assertFalse(cancelled);
        assertEquals(HelpStatus.ACCEPTED, help.getStatus());
        verifyNoInteractions(ratingService);
    }

    @Test
    void getHelpHistory_shouldRejectNonParticipant() {
        Help help = help(5L, HelpStatus.CONFIRMED, user(1L), user(2L));
        when(helpRepository.findById(5L)).thenReturn(Optional.of(help));

        assertThrows(ForbiddenException.class,
                () -> helpService.getHelpHistory(5L, 99L, false));
        verifyNoInteractions(statusHistoryRepository);
    }

    @Test
    void getHelpHistory_shouldReturnEntriesForParticipant() {
        Help help = help(5L, HelpStatus.CONFIRMED, user(1L), user(2L));
        when(helpRepository.findById(5L)).thenReturn(Optional.of(help));
        HelpStatusHistory entry = new HelpStatusHistory();
        entry.setId(7L);
        entry.setFromStatus(HelpStatus.COMPLETED);
        entry.setToStatus(HelpStatus.CONFIRMED);
        entry.setActorRole(HelpActorRole.RECEIVER);
        entry.setCreatedAt(LocalDateTime.now());
        when(statusHistoryRepository.findByHelpIdOrderByCreatedAtAsc(5L))
                .thenReturn(List.of(entry));

        List<HelpHistoryEntryDto> history = helpService.getHelpHistory(5L, 2L, false);

        assertEquals(1, history.size());
        assertEquals("COMPLETED", history.get(0).getFromStatus());
        assertEquals("CONFIRMED", history.get(0).getToStatus());
    }

    @Test
    void getHelpGraph_shouldUseHelpIdAsEdgeIdentityAndKeepDirectionAndPost() {
        GraphUserProjection firstUser = graphUser(1L);
        GraphUserProjection secondUser = graphUser(2L);
        when(helpRepository.findUserGraphEdges(eq(1L), any(), anyInt()))
                .thenReturn(List.<Object[]>of(graphRow(100L, 1L, "Helper", 2L, "Receiver", 10L, "Post")));
        when(helpRepository.countUserGraphEdges(eq(1L), any())).thenReturn(1L);
        when(userRepository.findGraphUsersByIds(any()))
                .thenReturn(List.of(firstUser, secondUser));

        org.example.helptreeservice.dto.graph.HelpGraphDto graph =
                helpService.getHelpGraph(1L, null, null);

        assertEquals(1, graph.getEdges().size());
        var edge = graph.getEdges().get(0);
        assertEquals(100L, edge.getId());
        assertEquals(1L, edge.getFromUserId());
        assertEquals(2L, edge.getToUserId());
        assertEquals(10L, edge.getPostId());
        assertEquals("CONFIRMED", edge.getStatus());
        assertEquals(1, graph.getTotalHelps());
        assertEquals(1, graph.getReturnedHelps());
        assertFalse(graph.isTruncated());
        verify(helpGraphMetrics).record(eq(false), anyLong(), eq(2), eq(1), eq(1), eq(false));
    }

    @Test
    void getHelpGraph_shouldKeepTotalHelpsWhenMaxEdgesTruncatesResponse() {
        GraphUserProjection firstUser = graphUser(1L);
        GraphUserProjection secondUser = graphUser(2L);
        when(helpRepository.findUserGraphEdges(eq(1L), any(), eq(1)))
                .thenReturn(List.<Object[]>of(graphRow(100L, 1L, "Helper", 2L, "Receiver", 10L, "Post")));
        when(helpRepository.countUserGraphEdges(eq(1L), any())).thenReturn(2L);
        when(userRepository.findGraphUsersByIds(any()))
                .thenReturn(List.of(firstUser, secondUser));

        org.example.helptreeservice.dto.graph.HelpGraphDto graph =
                helpService.getHelpGraph(1L, null, 1);

        assertEquals(2, graph.getTotalHelps());
        assertEquals(1, graph.getReturnedHelps());
        assertTrue(graph.isTruncated());
        verify(helpGraphMetrics).record(eq(false), anyLong(), eq(2), eq(2), eq(1), eq(true));
    }

    @Test
    void getHelpGraph_shouldPassTheBoundedDepthToTheDatabase() {
        GraphUserProjection firstUser = graphUser(1L);
        GraphUserProjection secondUser = graphUser(2L);
        when(helpRepository.findUserGraphEdges(eq(1L), eq(1), anyInt()))
                .thenReturn(List.<Object[]>of(graphRow(1L, 1L, "One", 2L, "Two", 10L, "P1")));
        when(helpRepository.countUserGraphEdges(eq(1L), eq(1))).thenReturn(1L);
        when(userRepository.findGraphUsersByIds(any()))
                .thenReturn(List.of(firstUser, secondUser));

        org.example.helptreeservice.dto.graph.HelpGraphDto graph =
                helpService.getHelpGraph(1L, 1, null);

        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getTotalHelps());
        assertEquals(1L, graph.getEdges().get(0).getId());
    }

    @Test
    void getHelpGraph_shouldClampDepthAndEdgeLimitToServerMaximums() {
        when(helpRepository.findUserGraphEdges(eq(1L), eq(10), eq(2000)))
                .thenReturn(List.of());
        when(helpRepository.countUserGraphEdges(eq(1L), eq(10))).thenReturn(0L);

        org.example.helptreeservice.dto.graph.HelpGraphDto graph =
                helpService.getHelpGraph(1L, 999, 999_999);

        assertEquals(0, graph.getEdges().size());
        assertFalse(graph.isTruncated());
        verify(helpRepository).findUserGraphEdges(1L, 10, 2000);
    }

    private Object[] graphRow(Long helpId,
                              Long helperId,
                              String helperName,
                              Long receiverId,
                              String receiverName,
                              Long postId,
                              String postTitle) {
        return new Object[]{
                helpId, helperId, helperName, receiverId, receiverName,
                postId, postTitle, "CONFIRMED", LocalDateTime.now()
        };
    }

    private GraphUserProjection graphUser(Long id) {
        GraphUserProjection user = mock(GraphUserProjection.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getName()).thenReturn("User " + id);
        lenient().when(user.getHelpedCount()).thenReturn(0);
        lenient().when(user.getDebtCount()).thenReturn(0);
        lenient().when(user.getRating()).thenReturn(3.0);
        return user;
    }

    private HelpRequest request(Long postId) {
        HelpRequest request = new HelpRequest();
        request.setPostId(postId);
        return request;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setDeleted(false);
        user.setDebtCount(0);
        user.setHelpedCount(0);
        return user;
    }

    private Post post(Long id, PostStatus status, User author) {
        Post post = new Post();
        post.setId(id);
        post.setTitle("Post " + id);
        post.setStatus(status);
        post.setUser(author);
        post.setDeleted(false);
        return post;
    }

    private Help help(Long id, HelpStatus status, User helper, User receiver) {
        Post post = post(10L, PostStatus.IN_PROGRESS, receiver);
        post.setHelper(helper);
        Help help = new Help();
        help.setId(id);
        help.setPost(post);
        help.setHelper(helper);
        help.setReceiver(receiver);
        help.setStatus(status);
        help.setAcceptedAt(LocalDateTime.now().minusDays(1));
        help.setCreatedAt(LocalDateTime.now().minusDays(1));
        help.setUpdatedAt(LocalDateTime.now().minusDays(1));
        help.setDeleted(false);
        return help;
    }
}
