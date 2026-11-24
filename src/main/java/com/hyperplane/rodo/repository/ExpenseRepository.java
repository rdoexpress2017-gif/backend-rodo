package com.hyperplane.rodo.repository;

import com.hyperplane.rodo.model.Expense;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends ReactiveMongoRepository<Expense, String> {
}
