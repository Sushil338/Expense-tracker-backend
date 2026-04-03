package com.tracker.Budgeter.config;

import com.tracker.Budgeter.Model.Category;
import com.tracker.Budgeter.Repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DataSeeder implements CommandLineRunner {
    private final CategoryRepository categoryRepository;

    public DataSeeder(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (categoryRepository.count() == 0) {
            Arrays.asList("Food", "Travel", "Stationery", "Hostel Fees", "Entertainment")
                    .forEach(name -> categoryRepository.save(new Category(name)));
            System.out.println("--> Default Categories Seeded to MySQL!");
        }
    }
}
