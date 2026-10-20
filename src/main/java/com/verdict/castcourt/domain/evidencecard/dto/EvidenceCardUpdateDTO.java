package com.verdict.castcourt.domain.evidencecard.dto;

public class EvidenceCardUpdateDTO {
    private Integer id;
    private String title;
    private String body;
    private Integer weight;

    public EvidenceCardUpdateDTO() {}

    public EvidenceCardUpdateDTO(Integer id, String title, String body, Integer weight) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.weight = weight;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }
}
