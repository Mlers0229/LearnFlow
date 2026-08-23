package com.learnflow.service;

import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class LearningProgressService {

    private final StudyPlanDayRepository studyPlanDayRepository;
    private final MasteryService masteryService;

    public LearningProgressService(StudyPlanDayRepository studyPlanDayRepository, MasteryService masteryService) {
        this.studyPlanDayRepository = studyPlanDayRepository;
        this.masteryService = masteryService;
    }

    @Transactional
    public void updateDayStatus(Long dayId, Long userId, String normalizedStatus) {
        StudyPlanDay day = studyPlanDayRepository.findById(dayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习日不存在或无权访问"));
        StudyPlan plan = day.getPlan();
        if (plan == null || userId == null || !userId.equals(plan.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学习日不存在或无权访问");
        }
        String previousStatus = day.getStatus() == null ? "NOT_STARTED" : day.getStatus();
        if (previousStatus.equals(normalizedStatus)) {
            return;
        }
        LocalDateTime priorUpdate = day.getUpdatedAt();
        String transitionVersion = priorUpdate == null ? "initial" : priorUpdate.toString();
        day.setStatus(normalizedStatus);
        studyPlanDayRepository.save(day);
        masteryService.recordPlanDayStatus(userId, day, previousStatus, normalizedStatus, transitionVersion);
    }
}

