package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/assignments")
@CrossOrigin(origins = "http://localhost:4200")
public class AssignmentController {
    
    private final AssignmentService assignmentService;
    private final AssignmentRepository ar;
    private final SR1 sr1;

    public AssignmentController(AssignmentService us, AssignmentRepository ar, SR1 sr1) {
        this.assignmentService = us;
        this.ar = ar;
        this.sr1 = sr1;
    }
    
    @GetMapping
    public ResponseEntity<List<Assignment>> getAllAssignments() {
        List<Assignment> assignments = assignmentService.getAllAssignments();
        return ResponseEntity.ok(assignments);
    }

    @PutMapping("/waitlist/reorder")
    public ResponseEntity<List<Assignment>> reorderWaitlist(@RequestBody List<Long> ids) {
        List<Assignment> reordered = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Assignment a = ar.findById(ids.get(i)).orElseThrow();
            a.setPosition(i);
            reordered.add(ar.save(a));
        }
        return ResponseEntity.ok(reordered);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Assignment> getAssignmentById(@PathVariable Long id) {
        Assignment assignment = assignmentService.getAssignmentById(id);
        return ResponseEntity.ok(assignment);
    }
    
    @PostMapping
    public ResponseEntity<Assignment> createAssignment(@Valid @RequestBody Assignment assignment) {
        Assignment savedAssignment = assignmentService.createAssignment(assignment);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedAssignment);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Assignment> updateAssignment(@PathVariable Long id, @Valid @RequestBody Assignment assignmentDetails) {
        Assignment updatedAssignment = assignmentService.updateAssignment(id, assignmentDetails);
        return ResponseEntity.ok(updatedAssignment);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/waitlist")
    public ResponseEntity<List<Assignment>> getWaitList() {
        return ResponseEntity.ok(sr1.getWaitList());
    }
}
