package com.example.training;

import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.support.JdbcTransactionManager;

import com.example.training.entity.Reservation;
import com.example.training.input.ReservationInput;
import com.example.training.service.ReservationService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@SpringBootApplication
public class TrainingApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(TrainingApplication.class, args);
        // 트랜잭션 제어 로그를 출력하도록 설정
        ((Logger) LoggerFactory.getLogger(JdbcTransactionManager.class)).setLevel(Level.DEBUG);
        ReservationService reservationService = context.getBean(ReservationService.class);

        ReservationInput reservationInput = new ReservationInput();
        reservationInput.setName("김철수");
        reservationInput.setPhone("010-0000-0000");
        reservationInput.setEmailAddress("taro@example.com");
        reservationInput.setStudentTypeCode("FREELANCE");
        reservationInput.setTrainingId("t01");

        Reservation reservation = reservationService.reserve(reservationInput);

        System.out.println("신청을 마쳤습니다. 신청 ID=" + reservation.getId());
    }

}

