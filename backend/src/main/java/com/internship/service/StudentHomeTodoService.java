package com.internship.service;

import com.internship.entity.dto.StudentTodoItemDTO;

import java.util.List;

public interface StudentHomeTodoService {
    List<StudentTodoItemDTO> getActionableTodos(Long studentId);
}
