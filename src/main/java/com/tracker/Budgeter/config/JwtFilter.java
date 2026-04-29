package com.tracker.Budgeter.config;

import com.tracker.Budgeter.Service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import com.tracker.Budgeter.Service.UserDetailsServiceImpl; // Point to Option 1
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RestAuthenticationEntryPoint authenticationEntryPoint;

    // Inject the specific Service from Option 1 directly
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;
        String userName = null;

        try {
            // 1. Extract token and username from Header
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                userName = jwtService.extractUserName(token);
            }

            // 2. If username exists and user is not already authenticated in this session
            if (userName != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Use the injected userDetailsService (Option 1)
                UserDetails userDetails = userDetailsService.loadUserByUsername(userName);

                // 3. Validate token against database user details
                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 4. Set the authentication in the Security Context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute("auth_error", "JWT token has expired");
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("JWT token has expired", ex));
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute("auth_error", "JWT token is invalid");
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("JWT token is invalid", ex));
            return;
        }

        // 5. Continue with the filter chain
        filterChain.doFilter(request, response);
    }
}
