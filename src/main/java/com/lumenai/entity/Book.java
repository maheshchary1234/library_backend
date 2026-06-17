package com.lumenai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "books")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "google_book_id")
    private String googleBookId;

    private String title;
    private String author;
    private String category;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "preview_link", length = 2000)
    private String previewLink;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Double rating;
    private Boolean bookmarked;
    private String publisher;

    @Column(name = "published_date")
    private String publishedDate;

    private String isbn;

    @Column(name = "page_count")
    private Integer pageCount;

    private String language;
}
