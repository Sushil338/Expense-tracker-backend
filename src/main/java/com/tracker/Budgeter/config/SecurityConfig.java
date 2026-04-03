package com.tracker.Budgeter.config;

import com.tracker.Budgeter.Model.User;
import com.tracker.Budgeter.Repository.UserRepository;
import com.tracker.Budgeter.Service.JwtService;
import com.tracker.Budgeter.Service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Value("${app.oauth2.redirect-uri:http://localhost:5173}")
    private String oauth2RedirectUri;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authProvider(UserDetailsServiceImpl userDetailsServiceImpl){

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsServiceImpl);
        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception{
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for development
                .cors(Customizer.withDefaults()) // Allow React to connect
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**").permitAll() // Allow Login/Register
                        .anyRequest().authenticated() // Protect everything else
                )
                .oauth2Login(oauth -> oauth
                        .successHandler((request, response, authentication) -> handleOAuthSuccess(response, authentication))
                        .failureHandler((request, response, exception) -> handleOAuthFailure(response))
                );
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        http.addFilterBefore(jwtFilter , UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void handleOAuthSuccess(jakarta.servlet.http.HttpServletResponse response, org.springframework.security.core.Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        Boolean emailVerified = oauthUser.getAttribute("email_verified");

        if (email == null || email.trim().isEmpty()) {
            handleOAuthFailure(response);
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(generateUsername(name, email));
            newUser.setEmail(email);
            newUser.setVerified(Boolean.TRUE.equals(emailVerified));
            newUser.setPassword(passwordEncoder().encode(UUID.randomUUID().toString()));
            return userRepository.save(newUser);
        });

        String token = jwtService.generateToken(user.getUsername());

        String redirectUrl = UriComponentsBuilder
                .fromUriString(oauth2RedirectUri)
                .queryParam("token", token)
                .queryParam("username", user.getUsername())
                .queryParam("id", user.getId())
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private void handleOAuthFailure(jakarta.servlet.http.HttpServletResponse response) throws IOException {
        String redirectUrl = UriComponentsBuilder
                .fromUriString(oauth2RedirectUri)
                .queryParam("oauthError", "Google login failed")
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private String generateUsername(String name, String email) {
        String baseUsername = name;

        if (baseUsername == null || baseUsername.trim().isEmpty()) {
            baseUsername = email != null ? email.split("@")[0] : "googleuser";
        }

        baseUsername = baseUsername.toLowerCase().replaceAll("[^a-z0-9]", "");

        if (baseUsername.isEmpty()) {
            baseUsername = "googleuser";
        }

        String candidate = baseUsername;
        int suffix = 1;

        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = baseUsername + suffix;
            suffix++;
        }

        return candidate;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
