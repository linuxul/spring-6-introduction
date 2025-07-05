package com.example.sample.service;

import org.springframework.stereotype.Service;

@Service
public class DiscountServiceImpl implements DiscountService {

    private final DiscountProperties discountProperties;

    public DiscountServiceImpl(DiscountProperties discountProperties) {
        this.discountProperties = discountProperties;
    }

    @Override
    public int calculateDiscountPrice(int originalPrice) {

        double discountRate = discountProperties.getRate();
        int discountMax = discountProperties.getMax();
        
        System.out.println("할인율:" + discountRate);
        System.out.println("할인한도:" + discountMax);

        int discount = (int)(originalPrice * discountRate);

        if (discount > discountMax) {
            System.out.println("할인액이 할인한도를 초과했습니다. discount=" + discount + " max=" + discountMax);
            discount = discountMax;
        }

        return originalPrice - discount;
    }
}
