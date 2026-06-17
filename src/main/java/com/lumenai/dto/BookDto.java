package com.lumenai.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookDto {
    private Long id;
    private String googleBookId;
    private String title;
    private String author;
    private String category;
    private String imageUrl;
    private String previewLink;
    private String description;
    private Double rating;
    private Boolean bookmarked;
    private String publisher;
    private String publishedDate;
    private String isbn;
    private Integer pageCount;
    private String language;
}
