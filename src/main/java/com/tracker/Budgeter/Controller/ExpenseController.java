package com.tracker.Budgeter.Controller;

import com.tracker.Budgeter.Model.Category;
import com.tracker.Budgeter.Model.Expense;
import com.tracker.Budgeter.Model.MonthlyExtraBudgetRequest;
import com.tracker.Budgeter.Model.User;
import com.tracker.Budgeter.Repository.CategoryRepository;
import com.tracker.Budgeter.Repository.CategorySum;
import com.tracker.Budgeter.Repository.ExpenseRepository;
import com.tracker.Budgeter.Repository.UserRepository;
import com.tracker.Budgeter.Service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/budget")
public class ExpenseController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // ============================================================
    // ✅ Helper
    // ============================================================

    private User getAuthenticatedUser(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put("username", user.getUsername());
        userResponse.put("email", user.getEmail());
        userResponse.put("monthlyBudget", user.getMonthlyBudget());
        userResponse.put("currentMonthExtraBudget", user.getCurrentMonthExtraBudget());
        userResponse.put("extraBudgetMonth", user.getExtraBudgetMonth());
        userResponse.put("extraBudgetYear", user.getExtraBudgetYear());
        return userResponse;
    }

    // ============================================================
    // ✅ NEW: SINGLE FILTER API (BEST PRACTICE)
    // ============================================================

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Principal principal) {

        User user = getAuthenticatedUser(principal);

        if (start != null && end != null) {
            return ResponseEntity.ok(expenseService.getFilteredDashboard(user, start, end));
        }

        // Default "ALL" view
        List<Expense> expenses = expenseRepository.findByUserOrderByDateDesc(user);
        Double total = expenseRepository.sumTotalAmount(user); // Uses your existing Repo method

        return ResponseEntity.ok(Map.of(
                "expenses", expenses,
                "total", total != null ? total : 0.0, // Ensure total is included here
                "dailyAverage", expenseService.calculateDailyAverage(user),
                "chartData", expenseService.getChartData(user)
        ));
    }

    // ============================================================
    // ✅ KEEP (OPTIONAL BACKWARD COMPATIBILITY)
    // ============================================================

    @GetMapping("/expenses")
    public ResponseEntity<List<Expense>> getExpenses(Principal principal) {
        User user = getAuthenticatedUser(principal);
        return ResponseEntity.ok(expenseRepository.findByUserOrderByDateDesc(user));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(Principal principal) {
        User user = getAuthenticatedUser(principal);
        return ResponseEntity.ok(Map.of(
                "dailyAverage", expenseService.calculateDailyAverage(user)
        ));
    }

    @GetMapping("/monthly-overview")
    public ResponseEntity<Map<String, Object>> getMonthlyOverview(Principal principal) {
        User user = getAuthenticatedUser(principal);
        return ResponseEntity.ok(expenseService.getMonthlyOverview(user));
    }

    @GetMapping("/chart-data")
    public ResponseEntity<List<CategorySum>> getChartData(Principal principal) {
        User user = getAuthenticatedUser(principal);
        return ResponseEntity.ok(expenseService.getChartData(user));
    }

    // ============================================================
    // ✅ ADD EXPENSE (UNCHANGED)
    // ============================================================

    @PostMapping("/add")
    public ResponseEntity<?> addExpense(@RequestBody Expense expense, Principal principal) {

        if (expense.getCategory() == null || expense.getCategory().getName() == null || expense.getCategory().getName().isBlank()) {
            return ResponseEntity.badRequest().body("Error: Category name is required.");
        }

        try {
            User user = getAuthenticatedUser(principal);

            String categoryName = expense.getCategory().getName().trim();

            Category category = categoryRepository.findByName(categoryName)
                    .orElseGet(() -> categoryRepository.save(new Category(categoryName)));

            expense.setCategory(category);
            expense.setUser(user);

            Expense savedExpense = expenseRepository.save(expense);
            return ResponseEntity.ok(savedExpense);

        } catch (Exception e) {
            System.out.println("ERROR ADDING EXPENSE: " + e.getMessage());
            return ResponseEntity.internalServerError().body("An error occurred while saving the expense.");
        }
    }

    @PostMapping("/extra-budget")
    public ResponseEntity<?> addExtraBudget(@RequestBody MonthlyExtraBudgetRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        try {
            User user = getAuthenticatedUser(principal);
            User updatedUser = expenseService.addExtraBudget(user, request.getAmount());

            return ResponseEntity.ok(Map.of(
                    "message", "Extra budget added successfully",
                    "user", buildUserResponse(updatedUser)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<?> updateExpense(
            @PathVariable Long id,
            @RequestBody Expense updatedExpense,
            Principal principal) {

        if (updatedExpense.getCategory() == null || updatedExpense.getCategory().getName() == null
                || updatedExpense.getCategory().getName().isBlank()) {
            return ResponseEntity.badRequest().body("Error: Category name is required.");
        }

        try {
            User user = getAuthenticatedUser(principal);
            Expense existingExpense = expenseService.getExpenseById(id, user);

            String categoryName = updatedExpense.getCategory().getName().trim();

            Category category = categoryRepository.findByName(categoryName)
                    .orElseGet(() -> categoryRepository.save(new Category(categoryName)));

            existingExpense.setDescription(updatedExpense.getDescription());
            existingExpense.setAmount(updatedExpense.getAmount());
            existingExpense.setDate(updatedExpense.getDate());
            existingExpense.setCategory(category);

            Expense savedExpense = expenseRepository.save(existingExpense);
            return ResponseEntity.ok(savedExpense);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            System.out.println("ERROR UPDATING EXPENSE: " + e.getMessage());
            return ResponseEntity.internalServerError().body("An error occurred while updating the expense.");
        }
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<?> deleteExpense(@PathVariable Long id, Principal principal) {
        try {
            User user = getAuthenticatedUser(principal);
            Expense expense = expenseService.getExpenseById(id, user);
            expenseRepository.delete(expense);
            return ResponseEntity.ok(Map.of("message", "Expense deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            System.out.println("ERROR DELETING EXPENSE: " + e.getMessage());
            return ResponseEntity.internalServerError().body("An error occurred while deleting the expense.");
        }
    }

    // ============================================================
    // ✅ CUSTOM DATE RANGE (KEEP)
    // ============================================================

    @GetMapping("/range")
    public ResponseEntity<?> getExpensesByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal principal) {

        User user = getAuthenticatedUser(principal);

        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("Start date must be before end date.");
        }

        return ResponseEntity.ok(expenseService.getExpensesByRange(user, startDate, endDate));
    }
}
