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
    List<Post> findByUserId(Long userId);
    long countByUserId(Long userId);

    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.deleted = false AND p.status = 'OPEN'")
    List<Post> findAllActive();
}
