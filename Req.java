package com.example.demo;

import java.time.LocalDateTime;

public class Req implements Comparable<Req> {
    Integer id;
    LocalDateTime arr;
    LocalDateTime dep;
    String origin;
    Integer prior;
    boolean rejectP1;

    public Req() {
    }

    public Req(Integer id, LocalDateTime arr, LocalDateTime dep, String origin) {
        this.id = id;
        this.arr = arr;
        this.dep = dep;
        this.origin = origin;
        this.prior = 0;
        this.rejectP1 = false;
    }

    public int getId() {
        return id;
    }

    public LocalDateTime getArr() {
        return arr;
    }

    public LocalDateTime getDep() {
        return dep;
    }

    public String getOrigin() {
        return origin;
    }

    public Integer getPrior() {
        return prior;
    }

    public void setPrior(Integer prior) {
        this.prior = prior;
    }

    public boolean isRejectP1() {
        return rejectP1;
    }

    public void setRejectP1(boolean rejectP1) {
        this.rejectP1 = rejectP1;
    }

    @Override
    public String toString() {
        return "Req{" +
                "id=" + id +
                ", arr=" + arr +
                ", dep=" + dep +
                ", origin='" + origin + '\'' +
                ", prior=" + prior +
                ", rejectP1=" + rejectP1 +
                '}';
    }

    @Override
    public int compareTo(Req other) {
        return Integer.compare(this.prior, other.prior);
    }
}