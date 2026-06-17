package com.lumenai.controller;

import com.lumenai.dto.BookDto;
import com.lumenai.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping("/search")
    public ResponseEntity<List<BookDto>> searchBooks(@RequestParam String q) {
        List<BookDto> results = bookService.searchBooks(q);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/recommended")
    public ResponseEntity<List<BookDto>> getRecommendedBooks(@RequestParam String section) {
        List<BookDto> results = bookService.getRecommendedBooks(section);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/saved")
    public ResponseEntity<List<BookDto>> getSavedBooks(Authentication authentication) {
        List<BookDto> saved = bookService.getSavedBooks(authentication.getName());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/save")
    public ResponseEntity<BookDto> saveBook(@RequestBody BookDto bookDto, Authentication authentication) {
        BookDto saved = bookService.saveBook(bookDto, authentication.getName());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/unsave/{id}")
    public ResponseEntity<Void> unsaveBook(@PathVariable Long id, Authentication authentication) {
        bookService.unsaveBook(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bookmark/{id}")
    public ResponseEntity<BookDto> toggleBookmark(@PathVariable Long id, Authentication authentication) {
        BookDto updated = bookService.toggleBookmark(id, authentication.getName());
        return ResponseEntity.ok(updated);
    }
}
