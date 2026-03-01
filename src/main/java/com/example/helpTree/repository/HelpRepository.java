package com.example.helpTree.repository;

import com.example.helpTree.entity.Help;
import com.example.helpTree.entity.Post;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.HelpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HelpRepository extends JpaRepository<Help, Long> {

    // Найти все помощи по посту
    List<Help> findByPost(Post post);

    // Найти все помощи, где пользователь помогал
    List<Help> findByHelper(User helper);

    // Найти все помощи, где пользователю помогали
    List<Help> findByReceiver(User receiver);

    // Найти активную помощь по посту (не отмененную)
    Optional<Help> findByPostAndStatusNot(Post post, HelpStatus status);

    // Проверить, откликался ли уже пользователь на пост
    boolean existsByPostAndHelper(Post post, User helper);

    // Найти все завершенные помощи (подтвержденные)
    List<Help> findByStatus(HelpStatus status);
}
