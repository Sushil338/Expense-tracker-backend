package com.tracker.Budgeter.Model;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String username;
    private String password;
    private String email;
    private Double monthlyBudget;
}