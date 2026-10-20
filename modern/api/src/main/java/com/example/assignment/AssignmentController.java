package com.example.assignment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 과제 API. */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** {@code GET /api/assignments} — 과제 목록(레거시 {@code /assignments}). */
    @GetMapping
    public AssignmentListResponse listAssignments() {
        return assignmentService.listAssignments();
    }
}
