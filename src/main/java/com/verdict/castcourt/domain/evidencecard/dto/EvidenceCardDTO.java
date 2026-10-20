package com.verdict.castcourt.domain.evidencecard.dto;

public class EvidenceCardDTO {
    private int id;
    private String side;
    private String title;
    private String body;
    private int weight;

    public EvidenceCardDTO() {}

    public EvidenceCardDTO(int id, String side, String title, String body, int weight) {
        this.id = id;
        this.side = side;
        this.title = title;
        this.body = body;
        this.weight = weight;
    }

    public int getId() {
        return id;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
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

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
