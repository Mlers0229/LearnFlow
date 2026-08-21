package com.learnflow.controller;

import com.learnflow.dto.ExerciseRecordListResponse;
import com.learnflow.service.ExerciseRecordService;
import com.learnflow.service.CurrentUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 练习记录查询接口。
 */
@RestController
@RequestMapping("/api/exercise-records")
public class ExerciseRecordController {

    private final ExerciseRecordService exerciseRecordService;
    private final CurrentUserService currentUserService;

    public ExerciseRecordController(ExerciseRecordService exerciseRecordService, CurrentUserService currentUserService) {
        this.exerciseRecordService = exerciseRecordService;
        this.currentUserService = currentUserService;
    }

    /**
     * 查询练习记录列表，用于练习回顾页。
     */
    @GetMapping
    public ResponseEntity<ExerciseRecordListResponse> listRecords(
            @RequestParam(name = "planId", required = false) Long planId,
            @RequestParam(name = "dayId", required = false) Long dayId,
            @RequestParam(name = "limit", defaultValue = "50") Integer limit) {
        ExerciseRecordListResponse response = exerciseRecordService.listRecords(
                currentUserService.requireUserId(), planId, dayId, limit
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 删除单条练习记录。
     */
    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> deleteRecord(@PathVariable("recordId") Long recordId) {
        try {
            exerciseRecordService.deleteRecord(recordId, currentUserService.requireUserId());
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 清空某个学习日下的练习记录。
     */
    @DeleteMapping("/day/{dayId}")
    public ResponseEntity<Map<String, Object>> deleteRecordsByDay(@PathVariable("dayId") Long dayId) {
        long deletedCount = exerciseRecordService.deleteRecordsByDay(dayId, currentUserService.requireUserId());
        return new ResponseEntity<>(
                Map.of(
                        "success", true,
                        "dayId", dayId,
                        "deletedCount", deletedCount
                ),
                new HttpHeaders(),
                HttpStatus.OK
        );
    }
}
