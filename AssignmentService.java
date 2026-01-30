package com.example.demo;

import java.util.List;
import org.springframework.stereotype.*;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;

    public AssignmentService(AssignmentRepository ur) {
        this.assignmentRepository = ur;
    }
    
    public List<Assignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }
    
    public Assignment getAssignmentById(Long id) {
        return assignmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Assignment not found with id: " + id));
    }
    
    public Assignment createAssignment(Assignment assignment) {
        if (assignmentRepository.findById(assignment.getId()).isPresent()) {
            throw new RuntimeException("Email already exists: " + assignment.getId());
        }
        return assignmentRepository.save(assignment);
    }
    
    public Assignment updateAssignment(Long id, Assignment assignmentDetails) {
        Assignment assignment = getAssignmentById(id);
        assignment.setStatus(assignmentDetails.getStatus());
        assignment.setRoomID(assignmentDetails.getRoomID());
        assignment.setPosition(assignmentDetails.getPosition());
        return assignmentRepository.save(assignment);
    }
    
    public void deleteAssignment(Long id) {
        Assignment assignment = getAssignmentById(id);
        assignmentRepository.delete(assignment);
    }
}