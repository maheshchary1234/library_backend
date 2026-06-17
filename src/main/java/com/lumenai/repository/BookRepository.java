package com.lumenai.repository;

import com.lumenai.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByCategory(String category);
    List<Book> findByTitleContainingIgnoreCase(String title);
    List<Book> findByUserId(Long userId);
    Optional<Book> findByUserIdAndGoogleBookId(Long userId, String googleBookId);
}
