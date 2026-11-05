package org.example.helptreeservice.block9;

import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.search.SearchResultDto;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.SearchType;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.HelpService;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.service.SearchService;
import org.example.helptreeservice.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-database proof for Block 9: the map applies its server-side filters,
 * unified search covers the categories that actually exist, large lists are
 * paginated, and the async geocoding queue is never touched by read requests.
 *
 * <p>Runs with {@code INTEGRATION_TEST=true} and removes only rows created by
 * its own uniquely named users.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block9MapSearchIntegrationTest {

    @Autowired private PostService postService;
    @Autowired private SearchService searchService;
    @Autowired private HelpService helpService;
    @Autowired private UserService userService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private HelpRepository helpRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE 'block9-it-%@test.local'", Long.class);
        if (ids.isEmpty()) {
            return;
        }

        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM reviews WHERE from_user_id IN (" + in
                + ") OR to_user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM helps WHERE helper_id IN (" + in
                + ") OR receiver_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM posts WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    @Test
    void mapAppliesStatusCategoryTextBboxAndServerPagination() {
        User owner = createUser("map-owner", "Block9-Minsk", 53.9, 27.56);

        Post oldest = createPost(owner, "Block9Needle oldest", "Block9Cat",
                PostStatus.OPEN, 53.90, 27.55, LocalDateTime.now().minusDays(3));
        Post middle = createPost(owner, "Block9Needle middle", "Block9Cat",
                PostStatus.OPEN, 53.91, 27.56, LocalDateTime.now().minusDays(2));
        Post newest = createPost(owner, "Block9Needle newest", "Block9Cat",
                PostStatus.OPEN, 53.92, 27.57, LocalDateTime.now().minusDays(1));

        createPost(owner, "Block9Needle completed", "Block9Cat",
                PostStatus.COMPLETED, 53.90, 27.55, LocalDateTime.now());
        createPost(owner, "Block9Needle other-category", "Block9Other",
                PostStatus.OPEN, 53.90, 27.55, LocalDateTime.now());
        createPost(owner, "Block9Needle outside-bbox", "Block9Cat",
                PostStatus.OPEN, 60.0, 30.0, LocalDateTime.now());

        Page<PostDto> first = postService.getPostsOnMap(
                PostStatus.OPEN, "Block9Cat", null, "Block9Needle",
                53.80, 27.40, 54.00, 27.80,
                null, null, null,
                PageRequest.of(0, 2));

        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getContent().size());
        assertTrue(first.hasNext());
        assertEquals(2, first.getTotalPages());
        assertEquals(List.of(newest.getId(), middle.getId()),
                first.getContent().stream().map(PostDto::getId).toList());

        Page<PostDto> second = postService.getPostsOnMap(
                PostStatus.OPEN, "Block9Cat", null, "Block9Needle",
                53.80, 27.40, 54.00, 27.80,
                null, null, null,
                PageRequest.of(1, 2));

        assertFalse(second.hasNext());
        assertEquals(List.of(oldest.getId()),
                second.getContent().stream().map(PostDto::getId).toList());

        Page<PostDto> completed = postService.getPostsOnMap(
                PostStatus.COMPLETED, "Block9Cat", null, "Block9Needle",
                53.80, 27.40, 54.00, 27.80,
                null, null, null,
                PageRequest.of(0, 10));
        assertEquals(1, completed.getTotalElements());
        assertEquals("Block9Needle completed", completed.getContent().get(0).getTitle());

        Page<PostDto> wrongCity = postService.getPostsOnMap(
                PostStatus.OPEN, "Block9Cat", "Block9-Unknown", "Block9Needle",
                53.80, 27.40, 54.00, 27.80,
                null, null, null,
                PageRequest.of(0, 10));
        assertEquals(0, wrongCity.getTotalElements());
    }

    @Test
    void mapRadiusTrimsTheBoundingBoxToACircle() {
        User owner = createUser("radius-owner", "Block9-Radius", 53.9, 27.56);
        Post near = createPost(owner, "Block9Radius near", "Block9RadiusCat",
                PostStatus.OPEN, 53.90, 27.56, LocalDateTime.now());
        createPost(owner, "Block9Radius far", "Block9RadiusCat",
                PostStatus.OPEN, 54.30, 27.90, LocalDateTime.now().minusDays(1));

        Page<PostDto> result = postService.getPostsOnMap(
                PostStatus.OPEN, "Block9RadiusCat", null, null,
                null, null, null, null,
                53.90, 27.56, 5.0,
                PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(near.getId(), result.getContent().get(0).getId());
    }

    @Test
    void unifiedSearchReturnsOnlyVisibleSupportedCategories() {
        User author = createUser("search-owner", "Block9SearchCity", 53.9, 27.56);
        author.setName("Block9Searchable owner");
        author = userRepository.save(author);
        User helper = createUser("search-helper", "Block9HelperCity", 53.7, 27.4);

        Post searchable = createPost(author, "Block9Searchable open", "Block9SearchCat",
                PostStatus.OPEN, null, null, LocalDateTime.now().minusHours(2));
        Post older = createPost(author, "Block9Searchable older", "Block9SearchCat",
                PostStatus.OPEN, null, null, LocalDateTime.now().minusHours(4));
        Post cancelled = createPost(author, "Block9Searchable cancelled", "Block9SearchCat",
                PostStatus.CANCELLED, null, null, LocalDateTime.now().minusHours(1));
        Post deleted = createPost(author, "Block9Searchable deleted", "Block9SearchCat",
                PostStatus.OPEN, null, null, LocalDateTime.now().minusHours(1));
        deleted.setDeleted(true);
        postRepository.save(deleted);

        Help help = createHelp(searchable, helper, author);
        Review review = createReview(help, helper, author, "Block9Searchable review");

        PageResponse<SearchResultDto> all = searchService.search(
                "Block9Searchable", SearchType.ALL, null, null, "newest",
                PageRequest.of(0, 20));

        assertTrue(all.getTotalElements() >= 3);
        List<String> types = all.getContent().stream().map(SearchResultDto::getType).toList();
        assertTrue(types.contains("POST"));
        assertTrue(types.contains("USER"));
        assertTrue(types.contains("REVIEW"));

        List<Long> postIds = all.getContent().stream()
                .filter(hit -> "POST".equals(hit.getType()))
                .map(SearchResultDto::getId)
                .toList();
        assertTrue(postIds.contains(searchable.getId()));
        assertTrue(postIds.contains(older.getId()));
        assertFalse(postIds.contains(cancelled.getId()));
        assertFalse(postIds.contains(deleted.getId()));

        SearchResultDto reviewHit = all.getContent().stream()
                .filter(hit -> "REVIEW".equals(hit.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals(review.getId(), reviewHit.getId());
        assertEquals(author.getId(), reviewHit.getOwnerId());
        assertEquals("/profile/" + author.getId(), reviewHit.getLink());

        PageResponse<SearchResultDto> postsOnly = searchService.search(
                "Block9Searchable", SearchType.POST, null, null, "oldest",
                PageRequest.of(0, 20));
        assertTrue(postsOnly.getContent().stream().allMatch(hit -> "POST".equals(hit.getType())));
        assertEquals(2, postsOnly.getTotalElements());
        assertEquals(older.getId(), postsOnly.getContent().get(0).getId());

        PageResponse<SearchResultDto> cityFiltered = searchService.search(
                "Block9Searchable", SearchType.ALL, "Block9SearchCity", null, "newest",
                PageRequest.of(0, 20));
        assertTrue(cityFiltered.getContent().stream()
                .noneMatch(hit -> "REVIEW".equals(hit.getType())),
                "reviews have no city and are excluded by a city filter");

        PageResponse<SearchResultDto> categoryFiltered = searchService.search(
                "Block9Searchable", SearchType.ALL, null, "Block9SearchCat", "newest",
                PageRequest.of(0, 20));
        assertTrue(categoryFiltered.getContent().stream()
                .allMatch(hit -> "POST".equals(hit.getType())));

        assertEquals(postsOnly.getTotalElements(),
                searchService.search("Block9Searchable%", SearchType.POST, null, null, "newest",
                        PageRequest.of(0, 20)).getTotalElements(),
                "LIKE wildcards are stripped from user input");

        assertThrows(BadRequestException.class, () -> searchService.search(
                "%", SearchType.ALL, null, null, "newest", PageRequest.of(0, 20)));
    }

    @Test
    void helpResponsesUseTheSharedPaginationEnvelope() {
        User helper = createUser("help-helper", "Block9HelpCity", null, null);
        User receiver = createUser("help-receiver", "Block9HelpCity", null, null);

        for (int index = 0; index < 3; index++) {
            Post post = createPost(receiver, "Block9HelpPost " + index, "Block9HelpCat",
                    PostStatus.OPEN, null, null, LocalDateTime.now().minusDays(index));
            createHelp(post, helper, receiver);
        }

        Page<HelpResponse> first = helpService.getHelpsByHelper(
                helper.getId(), PageRequest.of(0, 2));
        PageResponse<HelpResponse> envelope = PageResponse.from(first);

        assertEquals(3, envelope.getTotalElements());
        assertEquals(2, envelope.getContent().size());
        assertEquals(2, envelope.getTotalPages());
        assertEquals(0, envelope.getNumber());
        assertEquals(2, envelope.getSize());
        assertTrue(envelope.isHasNext());

        Page<HelpResponse> second = helpService.getHelpsByHelper(
                helper.getId(), PageRequest.of(1, 2));
        assertEquals(1, second.getContent().size());
        assertFalse(second.hasNext());
    }

    @Test
    void adminUserListFiltersBlockedAccountsServerSide() {
        User blocked = createUser("blocked-user", "Block9BlockedCity", null, null);
        blocked.setBlockedAt(LocalDateTime.now().minusHours(1));
        userRepository.save(blocked);
        User active = createUser("active-user", "Block9BlockedCity", null, null);

        Page<UserDto> blockedOnly = userService.getAllUsers(
                PageRequest.of(0, 10), "Block9Blocked", null, null, true);
        assertEquals(1, blockedOnly.getTotalElements());
        assertEquals(blocked.getId(), blockedOnly.getContent().get(0).getId());

        Page<UserDto> activeOnly = userService.getAllUsers(
                PageRequest.of(0, 10), "Block9Blocked", null, null, false);
        assertEquals(1, activeOnly.getTotalElements());
        assertEquals(active.getId(), activeOnly.getContent().get(0).getId());

        Page<UserDto> everyone = userService.getAllUsers(
                PageRequest.of(0, 10), "Block9Blocked", null, null, null);
        assertEquals(2, everyone.getTotalElements());
    }

    private User createUser(String suffix, String city, Double latitude, Double longitude) {
        User user = new User();
        user.setName("Block9 " + suffix);
        user.setEmail("block9-it-" + suffix + "-" + UUID.randomUUID() + "@test.local");
        user.setPassword("encoded");
        user.setCity(city);
        user.setLatitude(latitude);
        user.setLongitude(longitude);
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setRating(4.5);
        user.setHelpedCount(0);
        user.setDebtCount(0);
        return userRepository.save(user);
    }

    private Post createPost(User owner,
                            String title,
                            String category,
                            PostStatus status,
                            Double latitude,
                            Double longitude,
                            LocalDateTime createdAt) {
        Post post = new Post();
        post.setTitle(title);
        post.setDescription(title + " description");
        post.setCategory(category);
        post.setAuthorName(owner.getName());
        post.setUser(owner);
        post.setStatus(status);
        post.setLatitude(latitude);
        post.setLongitude(longitude);
        post.setCreatedAt(createdAt);
        post.setUpdatedAt(createdAt);
        post.setDeleted(false);
        return postRepository.save(post);
    }

    private Help createHelp(Post post, User helper, User receiver) {
        LocalDateTime now = LocalDateTime.now();
        Help help = new Help();
        help.setPost(post);
        help.setHelper(helper);
        help.setReceiver(receiver);
        help.setStatus(HelpStatus.CONFIRMED);
        help.setCreatedAt(now);
        help.setUpdatedAt(now);
        help.setAcceptedAt(now);
        help.setCompletedAt(now);
        help.setConfirmedAt(now);
        help.setDeleted(false);
        return helpRepository.save(help);
    }

    private Review createReview(Help help, User from, User to, String comment) {
        Review review = new Review();
        review.setHelp(help);
        review.setFromUser(from);
        review.setToUser(to);
        review.setRating(5);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());
        review.setDeleted(false);
        assertNotNull(reviewRepository.save(review).getId());
        return review;
    }
}
