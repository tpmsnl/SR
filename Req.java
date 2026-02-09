package com.example.demo.models;

import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor
@Data
public class Req {
    Integer id;
    LocalDateTime arr;
    LocalDateTime dep;
    String origin;
    boolean rejectP1;
    boolean rejectP2;

    public Req(Integer id, LocalDateTime arr, LocalDateTime dep, String origin) {
        this.id = id;
        this.arr = arr;
        this.dep = dep;
        this.origin = origin;
        this.rejectP1 = false;
        this.rejectP2 = false;
    }
}