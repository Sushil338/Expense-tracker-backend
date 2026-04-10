package com.tracker.Budgeter.Controller;

import com.tracker.Budgeter.Model.UpdateUserRequest;
import com.tracker.Budgeter.Model.User;
import com.tracker.Budgeter.Repository.UserRepository;
import com.tracker.Budgeter.Service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder encoder;

    @GetMapping("/test")
    public String test() {
        return "Working";
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

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        // Encrypt the password before saving to MySQL
        user.setPassword(encoder.encode(user.getPassword()));
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody User user) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));

            if (authentication.isAuthenticated()) {
                User dbUser = userRepository.findByUsername(user.getUsername()).orElseThrow(() ->
                        new RuntimeException("User not found"));

                String token = jwtService.generateToken(dbUser.getUsername());

                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("username", dbUser.getUsername());
                response.put("id", dbUser.getId());
                response.put("user", buildUserResponse(dbUser));

                return ResponseEntity.ok(response);
            }
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication failed");
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        if (principal == null) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        User user = userRepository.findByUsername(principal.getName()).orElse(null);

        if (user == null) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateUser( @PathVariable Long id, @RequestBody UpdateUserRequest request) {
        User existingUser = userRepository.findById(id).orElse(null);

        if (existingUser == null) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // Update fields only if provided
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            User existingByUsername = userRepository.findByUsername(request.getUsername()).orElse(null);

            if (existingByUsername != null && !existingByUsername.getId().equals(id)) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Username already taken");
                return ResponseEntity.badRequest().body(error);
            }
            existingUser.setUsername(request.getUsername());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            existingUser.setPassword(encoder.encode(request.getPassword()));
        }

        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            existingUser.setEmail(request.getEmail());
        }

        if (request.getMonthlyBudget() != null) {
            existingUser.setMonthlyBudget(request.getMonthlyBudget());
        }

        // Save updated user
        userRepository.save(existingUser);

        String newToken = jwtService.generateToken(existingUser.getUsername());
        // Prepare clean response
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User updated successfully");
        response.put("user", buildUserResponse(existingUser));
        response.put("token", newToken);

        return ResponseEntity.ok(response);
    }

}
