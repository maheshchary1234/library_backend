package com.lumenai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumenai.dto.BookDto;
import com.lumenai.entity.Book;
import com.lumenai.entity.User;
import com.lumenai.repository.BookRepository;
import com.lumenai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.books.api.key:}")
    private String apiKey;

    private static final String GOOGLE_BOOKS_URL = "https://www.googleapis.com/books/v1/volumes";
    private static final String OPEN_LIBRARY_SEARCH_URL = "https://openlibrary.org/search.json";
    private static final String OPEN_LIBRARY_COVER_URL = "https://covers.openlibrary.org/b/id/%s-M.jpg";
    private static final String OPEN_LIBRARY_BOOK_URL = "https://openlibrary.org%s";

    public List<BookDto> searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getFallbackBooks("technology");
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GOOGLE_BOOKS_URL)
                    .queryParam("q", query)
                    .queryParam("maxResults", 20);

            if (apiKey != null && !apiKey.isEmpty() && !apiKey.contains("YOUR_")) {
                builder.queryParam("key", apiKey);
            }

            String url = builder.toUriString();
            log.info("Searching Google Books API: {}", url);

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            List<BookDto> googleResults = parseGoogleBooksResponse(response);
            if (!googleResults.isEmpty()) {
                return googleResults;
            }
            log.info("Google Books returned empty for '{}', trying Open Library...", query);
        } catch (Exception e) {
            log.warn("Google Books API failed ({}), trying Open Library fallback...", e.getMessage());
        }

        // Open Library fallback
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String olUrl = OPEN_LIBRARY_SEARCH_URL + "?q=" + encodedQuery +
                    "&fields=key,title,author_name,cover_i,first_publish_year,subject,isbn,number_of_pages_median&limit=20";
            log.info("Searching Open Library API: {}", olUrl);
            JsonNode olResponse = restTemplate.getForObject(olUrl, JsonNode.class);
            List<BookDto> olResults = parseOpenLibraryResponse(olResponse);
            if (!olResults.isEmpty()) return olResults;
        } catch (Exception e) {
            log.warn("Open Library API also failed ({}), falling back to stubs.", e.getMessage());
        }

        return getFallbackBooks(query);
    }

    public List<BookDto> getRecommendedBooks(String section) {
        String query;
        switch (section.toLowerCase()) {
            case "new":
                query = "subject:technology&orderBy=newest";
                break;
            case "popular":
                query = "best sellers";
                break;
            case "ai":
                query = "artificial intelligence";
                break;
            case "trending":
            default:
                query = "subject:fiction";
                break;
        }
        return searchBooks(query);
    }

    public List<BookDto> getSavedBooks(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return bookRepository.findByUserId(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public BookDto saveBook(BookDto bookDto, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Book> existing = bookRepository.findByUserIdAndGoogleBookId(user.getId(), bookDto.getGoogleBookId());
        if (existing.isPresent()) {
            return mapToDto(existing.get());
        }

        Book book = Book.builder()
                .userId(user.getId())
                .googleBookId(bookDto.getGoogleBookId())
                .title(bookDto.getTitle())
                .author(bookDto.getAuthor())
                .category(bookDto.getCategory())
                .imageUrl(bookDto.getImageUrl())
                .previewLink(bookDto.getPreviewLink())
                .description(bookDto.getDescription())
                .rating(bookDto.getRating())
                .bookmarked(false)
                .publisher(bookDto.getPublisher())
                .publishedDate(bookDto.getPublishedDate())
                .isbn(bookDto.getIsbn())
                .pageCount(bookDto.getPageCount())
                .language(bookDto.getLanguage())
                .build();

        Book saved = bookRepository.save(book);
        return mapToDto(saved);
    }

    public void unsaveBook(Long bookId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));

        if (!book.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized to unsave this book");
        }

        bookRepository.delete(book);
    }

    public BookDto toggleBookmark(Long bookId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));

        if (!book.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized to bookmark this book");
        }

        book.setBookmarked(book.getBookmarked() == null ? true : !book.getBookmarked());
        Book updated = bookRepository.save(book);
        return mapToDto(updated);
    }

    private List<BookDto> parseOpenLibraryResponse(JsonNode response) {
        List<BookDto> books = new ArrayList<>();
        if (response == null || !response.has("docs")) return books;

        for (JsonNode doc : response.get("docs")) {
            try {
                String key = doc.path("key").asText("");
                String title = doc.path("title").asText("Unknown Title");

                String author = "Unknown Author";
                if (doc.has("author_name") && doc.get("author_name").isArray() && doc.get("author_name").size() > 0) {
                    List<String> authors = new ArrayList<>();
                    doc.get("author_name").forEach(a -> authors.add(a.asText()));
                    author = String.join(", ", authors.subList(0, Math.min(3, authors.size())));
                }

                String category = "General";
                if (doc.has("subject") && doc.get("subject").isArray() && doc.get("subject").size() > 0) {
                    category = doc.get("subject").get(0).asText();
                    if (category.length() > 30) category = category.substring(0, 30);
                }

                long coverId = doc.path("cover_i").asLong(0);
                String imageUrl = coverId > 0
                        ? String.format(OPEN_LIBRARY_COVER_URL, coverId)
                        : "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&w=400&q=80";

                String previewLink = key.isEmpty() ? "" : String.format(OPEN_LIBRARY_BOOK_URL, key);
                String publishedDate = doc.path("first_publish_year").asText("N/A");
                int pageCount = doc.path("number_of_pages_median").asInt(0);

                String isbn = "N/A";
                if (doc.has("isbn") && doc.get("isbn").isArray() && doc.get("isbn").size() > 0) {
                    isbn = doc.get("isbn").get(0).asText();
                }

                books.add(BookDto.builder()
                        .googleBookId("ol-" + key.replace("/works/", ""))
                        .title(title)
                        .author(author)
                        .category(category)
                        .imageUrl(imageUrl)
                        .previewLink(previewLink)
                        .description("From Open Library: " + title + " by " + author)
                        .rating(4.0)
                        .bookmarked(false)
                        .publisher("Open Library")
                        .publishedDate(publishedDate)
                        .isbn(isbn)
                        .pageCount(pageCount)
                        .language("EN")
                        .build());
            } catch (Exception e) {
                log.warn("Skipping Open Library item: {}", e.getMessage());
            }
        }
        return books;
    }

    private List<BookDto> parseGoogleBooksResponse(JsonNode response) {
        List<BookDto> books = new ArrayList<>();
        if (response == null || !response.has("items")) {
            return books;
        }

        JsonNode items = response.get("items");
        for (JsonNode item : items) {
            try {
                String googleBookId = item.path("id").asText();
                JsonNode volumeInfo = item.path("volumeInfo");

                String title = volumeInfo.path("title").asText("Unknown Title");

                // Author mapping
                String author = "Unknown Author";
                if (volumeInfo.has("authors") && volumeInfo.get("authors").isArray()) {
                    List<String> authorList = new ArrayList<>();
                    volumeInfo.get("authors").forEach(a -> authorList.add(a.asText()));
                    author = String.join(", ", authorList);
                }

                // Category mapping
                String category = "General";
                if (volumeInfo.has("categories") && volumeInfo.get("categories").isArray() && volumeInfo.get("categories").size() > 0) {
                    category = volumeInfo.get("categories").get(0).asText();
                }

                // Image URL mapping with HTTPS upgrade
                String imageUrl = "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&w=400&q=80";
                JsonNode imageLinks = volumeInfo.path("imageLinks");
                if (imageLinks.has("thumbnail")) {
                    imageUrl = imageLinks.get("thumbnail").asText().replace("http://", "https://");
                } else if (imageLinks.has("smallThumbnail")) {
                    imageUrl = imageLinks.get("smallThumbnail").asText().replace("http://", "https://");
                }

                String previewLink = volumeInfo.path("previewLink").asText("");
                String description = volumeInfo.path("description").asText("No description available.");
                double rating = volumeInfo.path("averageRating").asDouble(4.0);
                String publisher = volumeInfo.path("publisher").asText("Unknown Publisher");
                String publishedDate = volumeInfo.path("publishedDate").asText("N/A");
                int pageCount = volumeInfo.path("pageCount").asInt(0);
                String language = volumeInfo.path("language").asText("en").toUpperCase();

                // ISBN mapping
                String isbn = "N/A";
                if (volumeInfo.has("industryIdentifiers") && volumeInfo.get("industryIdentifiers").isArray()) {
                    for (JsonNode idNode : volumeInfo.get("industryIdentifiers")) {
                        String type = idNode.path("type").asText();
                        if ("ISBN_13".equals(type) || "ISBN_10".equals(type)) {
                            isbn = idNode.path("identifier").asText();
                            if ("ISBN_13".equals(type)) {
                                break; // Prefer ISBN_13
                            }
                        }
                    }
                }

                BookDto dto = BookDto.builder()
                        .googleBookId(googleBookId)
                        .title(title)
                        .author(author)
                        .category(category)
                        .imageUrl(imageUrl)
                        .previewLink(previewLink)
                        .description(description)
                        .rating(rating)
                        .bookmarked(false)
                        .publisher(publisher)
                        .publishedDate(publishedDate)
                        .isbn(isbn)
                        .pageCount(pageCount)
                        .language(language)
                        .build();

                books.add(dto);
            } catch (Exception e) {
                log.warn("Skipping item due to parsing error: {}", e.getMessage());
            }
        }
        return books;
    }

    private BookDto mapToDto(Book book) {
        return BookDto.builder()
                .id(book.getId())
                .googleBookId(book.getGoogleBookId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .category(book.getCategory())
                .imageUrl(book.getImageUrl())
                .previewLink(book.getPreviewLink())
                .description(book.getDescription())
                .rating(book.getRating())
                .bookmarked(book.getBookmarked() != null && book.getBookmarked())
                .publisher(book.getPublisher())
                .publishedDate(book.getPublishedDate())
                .isbn(book.getIsbn())
                .pageCount(book.getPageCount())
                .language(book.getLanguage())
                .build();
    }

    private List<BookDto> getFallbackBooks(String filterTerm) {
        List<BookDto> allStubs = new ArrayList<>();

        allStubs.add(BookDto.builder()
                .googleBookId("stub-clean-code")
                .title("Clean Code: A Handbook of Agile Software Craftsmanship")
                .author("Robert C. Martin")
                .category("Technology")
                .imageUrl("https://covers.openlibrary.org/b/isbn/9780132350884-M.jpg")
                .previewLink("https://books.google.com/books?isbn=9780132350884")
                .description("Even bad code can function. But if code isn't clean, it can bring a development organization to its knees. Every year, countless hours and significant resources are lost because of poorly written code. But it doesn't have to be that way.")
                .rating(4.8)
                .publisher("Prentice Hall")
                .publishedDate("2008")
                .isbn("9780132350884")
                .pageCount(464)
                .language("EN")
                .build());

        allStubs.add(BookDto.builder()
                .googleBookId("stub-pragmatic-programmer")
                .title("The Pragmatic Programmer: Your Journey to Mastery")
                .author("David Thomas, Andrew Hunt")
                .category("Technology")
                .imageUrl("https://covers.openlibrary.org/b/isbn/9780135957059-M.jpg")
                .previewLink("https://books.google.com/books?isbn=9780135957059")
                .description("The Pragmatic Programmer is one of the most significant books in computer science. It cuts through the increasing specialization and technicalities of modern software development to examine the core process--taking a requirement and producing working, maintainable code.")
                .rating(4.9)
                .publisher("Addison-Wesley Professional")
                .publishedDate("2019")
                .isbn("9780135957059")
                .pageCount(352)
                .language("EN")
                .build());

        allStubs.add(BookDto.builder()
                .googleBookId("stub-designing-data")
                .title("Designing Data-Intensive Applications")
                .author("Martin Kleppmann")
                .category("Technology")
                .imageUrl("https://covers.openlibrary.org/b/isbn/9781449373320-M.jpg")
                .previewLink("https://books.google.com/books?isbn=9781449373320")
                .description("Data is at the center of many challenges in system design today. Difficult issues need to be figured out, such as scalability, consistency, reliability, efficiency, and maintainability. Martin Kleppmann helps you navigate this diverse landscape by examining the pros and cons of various technologies for processing and storing data.")
                .rating(4.9)
                .publisher("O'Reilly Media")
                .publishedDate("2017")
                .isbn("9781449373320")
                .pageCount(616)
                .language("EN")
                .build());

        allStubs.add(BookDto.builder()
                .googleBookId("stub-atomic-habits")
                .title("Atomic Habits: An Easy & Proven Way to Build Good Habits & Break Bad Ones")
                .author("James Clear")
                .category("Self Help")
                .imageUrl("https://covers.openlibrary.org/b/isbn/9780735211292-M.jpg")
                .previewLink("https://books.google.com/books?isbn=9780735211292")
                .description("No matter your goals, Atomic Habits offers a proven framework for improving--every day. James Clear, one of the world's leading experts on habit formation, reveals practical strategies that will teach you exactly how to form good habits, break bad ones, and master the tiny behaviors that lead to remarkable results.")
                .rating(4.8)
                .publisher("Avery")
                .publishedDate("2018")
                .isbn("9780735211292")
                .pageCount(320)
                .language("EN")
                .build());

        allStubs.add(BookDto.builder()
                .googleBookId("stub-deep-work")
                .title("Deep Work: Rules for Focused Success in a Distracted World")
                .author("Cal Newport")
                .category("Self Help")
                .imageUrl("https://covers.openlibrary.org/b/isbn/9781455586691-M.jpg")
                .previewLink("https://books.google.com/books?isbn=9781455586691")
                .description("One of the most valuable skills in our economy is becoming increasingly rare. If you master this skill, you'll achieve extraordinary results. Deep work is the ability to focus without distraction on a cognitively demanding task.")
                .rating(4.7)
                .publisher("Grand Central Publishing")
                .publishedDate("2016")
                .isbn("9781455586691")
                .pageCount(304)
                .language("EN")
                .build());

        allStubs.add(BookDto.builder()
                .googleBookId("stub-artificial-intelligence")
                .title("Artificial Intelligence: A Modern Approach")
                .author("Stuart Russell, Peter Norvig")
                .category("AI")
                .imageUrl("https://covers.openlibrary.org/b/isbn/9780134610993-M.jpg")
                .previewLink("https://books.google.com/books?isbn=9780134610993")
                .description("The long-anticipated revision of Artificial Intelligence: A Modern Approach explores the full breadth and depth of the field of artificial intelligence (AI). The 4th Edition brings readers up to date on the latest technologies, presents concepts in a more unified manner, and offers new or expanded coverage of machine learning, deep learning, transfer learning, multiagent systems, robotics, natural language processing, causality, probabilistic programming, privacy, fairness, and safe AI.")
                .rating(4.7)
                .publisher("Pearson")
                .publishedDate("2020")
                .isbn("9780134610993")
                .pageCount(1152)
                .language("EN")
                .build());

        // Simple filtering logic matching query
        if (filterTerm == null || filterTerm.trim().isEmpty() || filterTerm.equalsIgnoreCase("all")) {
            return allStubs;
        }

        String lowerTerm = filterTerm.toLowerCase();
        List<BookDto> filtered = allStubs.stream()
                .filter(b -> b.getTitle().toLowerCase().contains(lowerTerm)
                        || b.getAuthor().toLowerCase().contains(lowerTerm)
                        || b.getCategory().toLowerCase().contains(lowerTerm))
                .collect(Collectors.toList());

        return filtered.isEmpty() ? allStubs : filtered;
    }
}
