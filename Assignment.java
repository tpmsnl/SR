package com.example.demo.models;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
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
    private Integer priority;
    private Boolean rejectRoom;

    public Assignment(String status, Integer requestID, Integer roomID, Integer priority) {
        this.status = status;
        this.requestID = requestID;
        this.roomID = roomID;
        this.position = 0;
        this.priority = priority;
        this.rejectRoom = false;
    }
}