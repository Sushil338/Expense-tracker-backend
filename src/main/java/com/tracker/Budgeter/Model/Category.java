// Model / Data class for the 'category' to categorize the expenses.

package com.tracker.Budgeter.Model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "Food", "Fees", "Entertainment"

    // Constructor for easy initialization
    public Category(String name) {
        this.name = name;
    }
}
