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
        Map<String, Object> overview = getMonthlyOverview(user);
        double remaining = (double) overview.getOrDefault("remainingBudget", 0.0);

        LocalDate now = LocalDate.now();
        int daysInMonth = now.lengthOfMonth();
        int dayOfMonth = now.getDayOfMonth();

        // Days remaining including today
        int daysRemaining = daysInMonth - dayOfMonth + 1;

        return (remaining <= 0) ? 0.0 : remaining / daysRemaining;
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
        List<Expense> expenses = expenseRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);
        if (expenses == null) expenses = new ArrayList<>();

        // 1. Calculate total for the filtered range
        double rangeTotal = expenses.stream().mapToDouble(Expense::getAmount).sum();

        // 2. GET MONTHLY OVERVIEW for budget logic
        Map<String, Object> monthlyOverview = getMonthlyOverview(user);
        double remainingBudget = (double) monthlyOverview.get("remainingBudget");

        // 3. CALCULATE DAYS REMAINING
        LocalDate now = LocalDate.now();
        int daysInMonth = now.lengthOfMonth();
        int daysRemaining = daysInMonth - now.getDayOfMonth() + 1;

        // 4. CALCULATE SAFE DAILY LIMIT
        // This replaces the old "All-time average" logic
        double safeDailyLimit = (remainingBudget <= 0) ? 0.0 : remainingBudget / daysRemaining;

        // 5. CHART DATA
        Map<String, Double> categoryMap = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory().getName() : "General",
                        Collectors.summingDouble(Expense::getAmount)
                ));

        List<Map<String, Object>> chartData = categoryMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("category", entry.getKey());
                    map.put("total", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("expenses", expenses);
        response.put("total", rangeTotal);
        response.put("dailyAverage", safeDailyLimit); // Now correctly shows Rs 88.88 instead of 1650
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
