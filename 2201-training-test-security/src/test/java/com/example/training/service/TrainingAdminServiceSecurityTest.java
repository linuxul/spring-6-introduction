package com.example.training.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Sql("TrainingAdminServiceSecurityTest.sql")
class TrainingAdminServiceSecurityTest {
    @Autowired
    TrainingAdminService trainingAdminService;
    
    @Test
    void test_delete_GUEST유저호출불가() {
        Assertions.assertThatThrownBy(() -> {
            trainingAdminService.delete("t01");
        }).isInstanceOf(AccessDeniedException.class);
    }
    
    @Test
    void test_delete_ADMIN유저호출가능() {
        trainingAdminService.delete("t01");
    }
    
    @Test
    void test_delete_인증되지않으면호출불가() {
        Assertions.assertThatThrownBy(() -> {
            trainingAdminService.delete("t01");
        }).isInstanceOf(AccessDeniedException.class);        
    }
}
