package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.GeocodingCacheEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface GeocodingCacheRepository extends JpaRepository<GeocodingCacheEntry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GeocodingCacheEntry g "
            + "WHERE g.attempts < :maxAttempts "
            + "AND (g.status IN ('PENDING', 'FAILED') "
            + "     OR (g.status = 'PROCESSING' AND g.updatedAt < :staleBefore)) "
            + "ORDER BY g.updatedAt ASC")
    List<GeocodingCacheEntry> findPendingForUpdate(@Param("maxAttempts") int maxAttempts,
                                                   @Param("staleBefore") LocalDateTime staleBefore,
                                                   Pageable pageable);

    @Modifying
    @Query(value = "INSERT INTO geocoding_cache "
            + "(city_key, query_city, status, attempts, created_at, updated_at) "
            + "VALUES (:cityKey, :queryCity, 'PENDING', 0, :now, :now) "
            + "ON CONFLICT (city_key) DO NOTHING",
            nativeQuery = true)
    int enqueue(@Param("cityKey") String cityKey,
                @Param("queryCity") String queryCity,
                @Param("now") LocalDateTime now);

    /**
     * Backfills coordinates for every user whose normalized city matches and
     * whose coordinates are still unknown. Manually set coordinates are never
     * overwritten.
     */
    @Modifying
    @Query(value = "UPDATE users SET latitude = :lat, longitude = :lng, updated_at = :now "
            + "WHERE COALESCE(deleted, FALSE) = FALSE "
            + "AND latitude IS NULL AND longitude IS NULL "
            + "AND lower(btrim(city)) IN (:cityKeys)",
            nativeQuery = true)
    int backfillUsers(@Param("cityKeys") Collection<String> cityKeys,
                      @Param("lat") double lat,
                      @Param("lng") double lng,
                      @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = "UPDATE posts p SET latitude = :lat, longitude = :lng, updated_at = :now "
            + "FROM users u "
            + "WHERE p.user_id = u.id "
            + "AND COALESCE(p.deleted, FALSE) = FALSE "
            + "AND p.latitude IS NULL AND p.longitude IS NULL "
            + "AND lower(btrim(u.city)) IN (:cityKeys)",
            nativeQuery = true)
    int backfillPosts(@Param("cityKeys") Collection<String> cityKeys,
                      @Param("lat") double lat,
                      @Param("lng") double lng,
                      @Param("now") LocalDateTime now);
}
