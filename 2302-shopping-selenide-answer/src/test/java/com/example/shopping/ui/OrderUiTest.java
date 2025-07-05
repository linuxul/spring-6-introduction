package com.example.shopping.ui;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;

import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import com.codeborne.selenide.Configuration;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OrderUiTest {
    @Value("${local.server.port}")
    int randomPort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        Configuration.baseUrl = "http://localhost:" + randomPort;
    }

    @Test
    @Sql("OrderUiTest.sql")
    @Sql(value = "clear.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
    void test_주문() {
        open("/catalog/display-list");
        $("a[href*=productId]").click();
        $("input[name=quantity]").setValue("2");
        $("input[value=장바구니에추가]").click();
        $("input[value=주문하기]").click();
        $("input[name=name]").setValue("김철수");
        $("input[name=address]").setValue("서울시");
        $("input[name=phone]").setValue("010-0000-0000");
        $("input[name=emailAddress]").setValue("taro@example.com");
        $("input[name=paymentMethod]").selectRadio("CONVENIENCE_STORE");
        $("input[value=주문내용확인]").click();
        $("input[value=주문확정]").click();

        String orderId = $("div span").text();
        Map<String, Object> reservationMap = jdbcTemplate.queryForMap("SELECT * FROM t_order WHERE id=?", orderId);
        Assertions.assertThat(reservationMap.get("customer_name")).isEqualTo("김철수");
    }

    @Test
    @Sql("OrderUiTest.sql")
    @Sql(value = "clear.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
    void test_주문양식입력오류() {
        open("/catalog/display-list");
        $("a[href*=productId]").click();
        $("input[name=quantity]").setValue("2");
        $("input[value=장바구니에추가]").click();
        $("input[value=주문하기]").click();
        $("input[value=주문내용확인]").click();
        $("td div").should(exactText("이름은 필수입니다"));
    }
}
