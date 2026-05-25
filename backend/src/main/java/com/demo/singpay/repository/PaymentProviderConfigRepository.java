package com.demo.singpay.repository;

import com.demo.singpay.model.PaymentProviderConfig;
import com.demo.singpay.model.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentProviderConfigRepository extends JpaRepository<PaymentProviderConfig, Long> {

    List<PaymentProviderConfig> findByMethodAndActiveOrderByPriorityAsc(PaymentMethod method, boolean active);
}
