package com.example.demo;

import jakarta.persistence.*;

@Entity
@Table(name = "assignment")
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String status;

    private Integer requestID;

    private Integer roomID;

    private Integer position;

    public Assignment() {}

    public Assignment(String status, Integer requestID, Integer roomID) {
        this.status = status;
        this.requestID = requestID;
        this.roomID = roomID;
        this.position = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRequestID() {
        return requestID;
    }

    public void setRequestID(Integer requestID) {
        this.requestID = requestID;
    }

    public Integer getRoomID() {
        return roomID;
    }

    public void setRoomID(Integer roomID) {
        this.roomID = roomID;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "id=" + id +
                ", status='" + status + '\'' +
                ", requestID=" + requestID +
                ", roomID=" + roomID +
                ", position=" + position +
                '}';
    }
}