package com.verdict.castcourt.domain.work.entity;

import com.verdict.castcourt.domain.category.entity.Category;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "work")
public class Work {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 100)
    private String author;
    @Column(name = "released_on", nullable = false)
    private LocalDate releasedOn;
    @Column(length = 200)
    private String summary;
    @Column(nullable = false, length = 255)
    private String poster;
    @Column(name = "is_usage", nullable = false)
    private boolean isUsage = true;

    protected Work() {}
    public Work(Category category, String title, String author, LocalDate releasedOn,
                String summary, String poster) {
        this.category = category;
        this.title = title;
        this.author = author;
        this.releasedOn = releasedOn;
        this.summary = summary;
        this.poster = poster;
    }
    public Integer getId() { return id; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public LocalDate getReleasedOn() { return releasedOn; }
    public String getSummary() { return summary; }
    public String getPoster() { return poster; }

    public void deactivate() { this.isUsage = false; }
    public boolean isUsage() { return isUsage; }

    public void update(Category category, String title, String author, LocalDate releasedOn,
                       String summary, String poster) {
        this.category = category;
        this.title = title;
        this.author = author;
        this.releasedOn = releasedOn;
        this.summary = summary;
        this.poster = poster;
    }}
