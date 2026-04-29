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
    public HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository() {
        return new HttpCookieOAuth2AuthorizationRequestRepository();
    }

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
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtFilter jwtFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for development
                .cors(Customizer.withDefaults()) // Allow React to connect
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**").permitAll() // Allow Login/Register
                        .anyRequest().authenticated() // Protect everything else
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .requestCache(requestCache -> requestCache.disable())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(authorizationRequestRepository)
                        )
                        .successHandler((request, response, authentication) -> handleOAuthSuccess(request, response, authentication, authorizationRequestRepository))
                        .failureHandler((request, response, exception) -> handleOAuthFailure(request, response, authorizationRequestRepository))
                );
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.addFilterBefore(jwtFilter , UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void handleOAuthSuccess(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.Authentication authentication,
            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        Boolean emailVerified = oauthUser.getAttribute("email_verified");

        if (email == null || email.trim().isEmpty()) {
            handleOAuthFailure(request, response, authorizationRequestRepository);
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

        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
        response.sendRedirect(redirectUrl);
    }

    private void handleOAuthFailure(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) throws IOException {
        String redirectUrl = UriComponentsBuilder
                .fromUriString(oauth2RedirectUri)
                .queryParam("oauthError", "Google login failed")
                .build()
                .toUriString();

        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
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
