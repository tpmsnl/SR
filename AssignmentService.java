package com.example.demo.services;

import java.util.List;

import com.example.demo.models.Assignment;
import com.example.demo.repos.AssignRepo;
import org.springframework.stereotype.*;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class AssignmentService {

    private final AssignRepo ar;

    public AssignmentService(AssignRepo ar) {
        this.ar = ar;
    }

    public List<Assignment> getAllAssignments() {
        return ar.findAll();
    }

    public Assignment getAssignmentById(Long id) {
        return ar.findById(id)
                .orElseThrow(() -> new RuntimeException("Assignment not found with id: " + id));
    }

    public Assignment createAssignment(Assignment assignment) {
        if (ar.findById(assignment.getId()).isPresent()) {
            throw new RuntimeException("Email already exists: " + assignment.getId());
        }
        return ar.save(assignment);
    }

    public Assignment updateAssignment(Long id, Assignment assignmentDetails) {
        Assignment assignment = getAssignmentById(id);
        assignment.setStatus(assignmentDetails.getStatus());
        assignment.setRoomID(assignmentDetails.getRoomID());
        assignment.setPosition(assignmentDetails.getPosition());
        return ar.save(assignment);
    }

    public void deleteAssignment(Long id) {
        Assignment assignment = getAssignmentById(id);
        ar.delete(assignment);
    }
}