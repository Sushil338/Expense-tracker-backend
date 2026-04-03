package com.tracker.Budgeter.Repository;

import com.tracker.Budgeter.Model.Expense;
import com.tracker.Budgeter.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // ============================================================
    // ✅ BASIC
    // ============================================================

    List<Expense> findByUserOrderByDateDesc(User user);

    Optional<Expense> findByIdAndUser(Long id, User user);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.user = :user")
    Double sumTotalAmount(@Param("user") User user);

    @Query("SELECT COUNT(DISTINCT e.date) FROM Expense e WHERE e.user = :user")
    long countUniqueDays(@Param("user") User user);

    // ============================================================
    // ✅ CATEGORY SUMMARY (CHART)
    // ============================================================

    @Query("SELECT e.category.name as category, COALESCE(SUM(e.amount), 0) as total " +
            "FROM Expense e " +
            "WHERE e.user = :user " +
            "GROUP BY e.category.name")
    List<CategorySum> summarizeByGroup(@Param("user") User user);

    // ============================================================
    // ✅ DATE RANGE (LocalDate) - KEEP FOR CUSTOM RANGE
    // ============================================================

    @Query("SELECT e FROM Expense e " +
            "WHERE e.user = :user AND e.date BETWEEN :startDate AND :endDate " +
            "ORDER BY e.date DESC")
    List<Expense> findByUserAndDateBetween(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
            "WHERE e.user = :user AND e.date BETWEEN :startDate AND :endDate")
    Double sumTotalAmountInRange(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // ============================================================
    // ✅ DATETIME RANGE (24h, 7d, 30d, etc.)
    // ============================================================

    List<Expense> findByUserAndDateBetweenOrderByDateDesc(
            User user,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
            "WHERE e.user = :user AND e.date BETWEEN :start AND :end")
    Double sumTotalAmountByDateTimeRange(
            @Param("user") User user,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT e.category.name as category, COALESCE(SUM(e.amount), 0) as total " +
            "FROM Expense e " +
            "WHERE e.user = :user AND e.date BETWEEN :start AND :end " +
            "GROUP BY e.category.name")
    List<CategorySum> getCategorySumByUserAndDateRange(
            @Param("user") User user,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // ============================================================
    // ✅ FIX: COUNT DISTINCT DAYS (IMPORTANT FIX)
    // ============================================================

    @Query("SELECT COUNT(DISTINCT e.date) " +
            "FROM Expense e " +
            "WHERE e.user = :user AND e.date BETWEEN :start AND :end")
    long countUniqueDaysInRange(
            @Param("user") User user,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
