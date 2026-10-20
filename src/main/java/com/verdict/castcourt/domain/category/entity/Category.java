package com.verdict.castcourt.domain.category.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true, length = 10)
    private String name;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {}
    public Category(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }
    public void rename(String name) { this.name = name; }
    public Integer getId() { return id; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public boolean isFixed() { return "미분류".equals(name); }
}