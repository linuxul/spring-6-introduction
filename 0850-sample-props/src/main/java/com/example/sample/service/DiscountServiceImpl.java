package com.example.sample.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DiscountServiceImpl implements DiscountService {

    /** 할인율 */
    @Value("${discount.rate}")
    private double discountRate;

    /** 할인한도 */
    @Value("${discount.max}")
    private int discountMax;

    @Override
    public int calculateDiscountPrice(int originalPrice) {

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
