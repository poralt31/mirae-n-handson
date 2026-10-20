package com.example.assignment;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 과제 목록 서비스 — 레거시 {@code GET /assignments} 의 이관. */
@Service
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;

    public AssignmentService(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    public AssignmentListResponse listAssignments() {
        List<AssignmentListResponse.Row> rows = assignmentRepository.findAllForList().stream()
            .map(AssignmentListResponse.Row::from)
            .toList();
        return AssignmentListResponse.of(rows);
    }
}
