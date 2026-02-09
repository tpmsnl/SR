package com.example.demo.controllers;

import com.example.demo.models.Assignment;
import com.example.demo.services.ProcessList;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin")
@AllArgsConstructor
public class AdminController {

    private final ProcessList pl;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/hello")
    public String helloAdmin() {
        return "Hello, ADMIN";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/waitlist")
    public ResponseEntity<Object> getWaitlist() {
        List<Assignment> waiting = pl.getWaitlist();
        if(waiting.isEmpty())
            return new ResponseEntity<>("Empty waitlist", HttpStatus.NO_CONTENT);
        return new ResponseEntity<>(waiting, HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/p1")
    public ResponseEntity<Object> getP1() {
        List<Assignment> waiting = pl.getP1();
        if(waiting.isEmpty())
            return new ResponseEntity<>("Empty p1 list", HttpStatus.NO_CONTENT);
        return new ResponseEntity<>(waiting, HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/p2")
    public ResponseEntity<Object> getP2() {
        List<Assignment> waiting = pl.getP2();
        if(waiting.isEmpty())
            return new ResponseEntity<>("Empty p2 list", HttpStatus.NO_CONTENT);
        return new ResponseEntity<>(waiting, HttpStatus.OK);
    }

}
