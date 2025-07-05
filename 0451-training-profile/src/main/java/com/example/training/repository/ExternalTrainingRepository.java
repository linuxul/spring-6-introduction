package com.example.training.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.training.entity.Training;

@Repository
public class ExternalTrainingRepository implements TrainingRepository {
    @Override
    public List<Training> selectAll() {
        System.out.println("외부 시스템에서 데이터를 가져옵니다.");
        // 외부에서 가져온다고 가정한다
        List<Training> trainings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Training training = new Training();
            training.setTitle("ex_title_" + i);
            trainings.add(training);
        }
        return trainings;
    }
}
