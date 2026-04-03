package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.enums.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.user.id = :userId AND (p.deleted = false OR p.deleted IS NULL)")
    List<Post> findByUserId(@Param("userId") Long userId);
    
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.id = :id")
    Post findByIdWithUser(@Param("id") Long id);
    
    long countByUserId(Long userId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.deleted = false AND p.status = 'OPEN'")
    List<Post> findAllActive();

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.id IN :ids")
    List<Post> findByIdsWithUser(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.deleted = false OR p.deleted IS NULL")
    List<Post> findAllNotDeleted();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.status = :status AND (p.deleted = false OR p.deleted IS NULL)")
    long countByStatus(@Param("status") PostStatus status);
}
