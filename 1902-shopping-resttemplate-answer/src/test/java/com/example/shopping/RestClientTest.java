package com.example.shopping;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.example.shopping.entity.Product;
import com.example.shopping.input.ProductMaintenanceInput;

public class RestClientTest {
    @Test
    public void test() {
        RestTemplate restTemplate
                = new RestTemplateBuilder()
                .rootUri("http://localhost:8080")
                .build();

        ProductMaintenanceInput productMaintenanceInput = new ProductMaintenanceInput();
        productMaintenanceInput.setName("문서파쇄기");
        productMaintenanceInput.setPrice(3500);
        productMaintenanceInput.setStock(11);

        URI location = restTemplate
                .postForLocation("/api/products", productMaintenanceInput);

        Product product = restTemplate.getForObject(location, Product.class);
        System.out.println("가져온 상품 이름=" + product.getName());

        productMaintenanceInput.setName("문서파쇄기(신형)");
        restTemplate.put(location, productMaintenanceInput);

        restTemplate.delete(location);
    }
}
