package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByHelpAndFromUser(Help help, User fromUser);

    boolean existsByHelpAndFromUser(Help help, User fromUser);

    @Query("SELECT r FROM Review r JOIN FETCH r.fromUser JOIN FETCH r.help WHERE r.help = :help")
    List<Review> findByHelpWithDetails(@Param("help") Help help);

    @Query("SELECT r FROM Review r JOIN FETCH r.fromUser WHERE r.toUser = :user")
    List<Review> findByToUserWithFromUser(@Param("user") User user);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.toUser = :user")
    Double calculateAverageRating(@Param("user") User user);
}
