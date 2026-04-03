package com.tracker.Budgeter.Service;

import com.tracker.Budgeter.Model.Expense;
import com.tracker.Budgeter.Model.User;
import com.tracker.Budgeter.Repository.UserRepository;
import com.tracker.Budgeter.Repository.CategorySum;
import com.tracker.Budgeter.Repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    // ============================================================
    // ✅ OVERALL SUMMARY
    // ============================================================

    public Double calculateDailyAverage(User user) {
        long daysCount = expenseRepository.countUniqueDays(user);

        if (daysCount == 0) return 0.0;

        Double totalAmount = expenseRepository.sumTotalAmount(user);
        if (totalAmount == null) totalAmount = 0.0;

        return totalAmount / daysCount;
    }

    public List<CategorySum> getChartData(User user) {
        return expenseRepository.summarizeByGroup(user);
    }

    public Map<String, Object> getMonthlyOverview(User user) {
        LocalDate now = LocalDate.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = now.plusMonths(1).withDayOfMonth(1).atStartOfDay().minusSeconds(1);

        Double spent = expenseRepository.sumTotalAmountByDateTimeRange(user, startOfMonth, endOfMonth);
        if (spent == null) spent = 0.0;

        Double monthlyBudget = user.getMonthlyBudget();
        if (monthlyBudget == null) monthlyBudget = 0.0;

        double extraBudget = getCurrentMonthExtraBudget(user, now);
        double totalAvailableBudget = monthlyBudget + extraBudget;
        double remainingBudget = totalAvailableBudget - spent;
        double budgetUsedPercentage = totalAvailableBudget <= 0 ? 0.0 : (spent / totalAvailableBudget) * 100;

        Map<String, Object> response = new HashMap<>();
        response.put("month", now.getMonth().toString());
        response.put("monthlyBudget", monthlyBudget);
        response.put("extraBudget", extraBudget);
        response.put("totalAvailableBudget", totalAvailableBudget);
        response.put("currentMonthSpent", spent);
        response.put("remainingBudget", remainingBudget);
        response.put("budgetUsedPercentage", Math.min(budgetUsedPercentage, 100.0));

        return response;
    }

    // ============================================================
    // ✅ CUSTOM DATE RANGE (LocalDate)
    // ============================================================

    public Map<String, Object> getExpensesByRange(User user, LocalDate start, LocalDate end) {

        List<Expense> expenses = expenseRepository.findByUserAndDateBetween(user, start, end);

        Double total = expenseRepository.sumTotalAmountInRange(user, start, end);
        if (total == null) total = 0.0;

        return Map.of(
                "expenses", expenses != null ? expenses : Collections.emptyList(),
                "totalInRange", total,
                "startDate", start,
                "endDate", end
        );
    }

    // ============================================================
    // ✅ FILTER BASED (24h, 7d, 30d, etc.) - LocalDateTime
    // ============================================================

    public Map<String, Object> getFilteredDashboard(User user, LocalDateTime start, LocalDateTime end) {
        // 1. Fetch the filtered list from the database
        List<Expense> expenses = expenseRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);
        if (expenses == null) expenses = new ArrayList<>();

        // 2. Calculate the Total Amount Spent for this range
        double total = expenses.stream()
                .mapToDouble(Expense::getAmount)
                .sum();

        // 3. Optional: Calculate Daily Average safely if you still want it available
        // We use a helper to ensure we handle any Date type correctly
        long uniqueDays = expenses.stream()
                .map(e -> {
                    // This handles LocalDateTime, java.util.Date, or Timestamp safely
                    return e.getDate().toString().split("T")[0];
                })
                .distinct()
                .count();

        double dailyAvg = (uniqueDays == 0) ? 0.0 : total / uniqueDays;

        // 4. ✅ CHART DATA: Group by category name
        Map<String, Double> categoryMap = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.summingDouble(Expense::getAmount)
                ));

        // 5. Convert to List<Map> for frontend compatibility
        List<Map<String, Object>> chartData = categoryMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("category", entry.getKey());
                    map.put("total", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());

        // 6. Return the map with the "total" key for your React frontend
        Map<String, Object> response = new HashMap<>();
        response.put("expenses", expenses);
        response.put("total", total);           // Use this for the "Total Spent" display
        response.put("dailyAverage", dailyAvg); // Keep original logic for non-filtered view
        response.put("chartData", chartData);
        response.put("start", start);
        response.put("end", end);

        return response;
    }

    // ============================================================
    // ✅ CHART ONLY FILTER
    // ============================================================

    public List<CategorySum> getChartDataByRange(User user, LocalDateTime start, LocalDateTime end) {
        return expenseRepository.getCategorySumByUserAndDateRange(user, start, end);
    }

    public Expense getExpenseById(Long expenseId, User user) {
        return expenseRepository.findByIdAndUser(expenseId, user)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
    }

    public double getCurrentMonthExtraBudget(User user) {
        return getCurrentMonthExtraBudget(user, LocalDate.now());
    }

    public double getCurrentMonthExtraBudget(User user, LocalDate date) {
        if (user.getCurrentMonthExtraBudget() == null) return 0.0;
        if (user.getExtraBudgetMonth() == null || user.getExtraBudgetYear() == null) return 0.0;

        if (user.getExtraBudgetMonth() == date.getMonthValue() && user.getExtraBudgetYear() == date.getYear()) {
            return user.getCurrentMonthExtraBudget();
        }

        return 0.0;
    }

    public User addExtraBudget(User user, Double amount) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Extra budget amount must be greater than 0");
        }

        LocalDate now = LocalDate.now();
        double existingExtraBudget = getCurrentMonthExtraBudget(user, now);

        user.setCurrentMonthExtraBudget(existingExtraBudget + amount);
        user.setExtraBudgetMonth(now.getMonthValue());
        user.setExtraBudgetYear(now.getYear());

        return userRepository.save(user);
    }
}
