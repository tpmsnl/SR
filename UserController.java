package com.example.demo.controllers;

import com.example.demo.services.ProcessList;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final ProcessList pl;

    public UserController(ProcessList pl) {
        this.pl = pl;
    }

    @GetMapping("/hello")
    public String getHello() {
        return "Hello, USER";
    }

    @PostMapping("/rejectP1")
    public ResponseEntity<String> rejectP1(@RequestParam String reqId) {
        Integer id = Integer.parseInt(reqId);
        pl.rejectP1(id);
        return new ResponseEntity<>("Sent rejection request", HttpStatus.ACCEPTED);
    }

    @PostMapping("/rejectP2")
    public ResponseEntity<String> rejectP2(@RequestParam String reqId) {
        Integer id = Integer.parseInt(reqId);
        pl.rejectP2(id);
        return new ResponseEntity<>("Sent rejection request", HttpStatus.ACCEPTED);
    }
}
