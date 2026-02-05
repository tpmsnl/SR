package com.example.demo;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain getSecChain(@NonNull HttpSecurity http) {
        http
            .authorizeHttpRequests(x-> x
                    .requestMatchers("/user/**").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .formLogin(x-> x.defaultSuccessUrl("/hello", true))
            .httpBasic(basic -> {});

        return http.build();
    }

    @Bean
    public UserDetailsService getUserDetails() {
        UserDetails normal = User.builder()
                .username("user")
                .password(encodePwd().encode("12345"))
                .roles("USER").build();
        UserDetails admin = User.builder()
                .username("admin")
                .password(encodePwd().encode("54321"))
                .roles("ADMIN").build();
        return new InMemoryUserDetailsManager(normal, admin);
    }

    @Bean
    public PasswordEncoder encodePwd() {
        return new BCryptPasswordEncoder();
    }
}
