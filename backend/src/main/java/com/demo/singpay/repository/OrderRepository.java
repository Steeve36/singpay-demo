package com.demo.singpay.repository;

import com.demo.singpay.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByReference(String reference);

    Optional<Order> findByReference(String reference);

    Optional<Order> findByReferenceAndUserId(String reference, Long userId);
}
