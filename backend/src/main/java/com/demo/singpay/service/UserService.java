package com.demo.singpay.service;

import com.demo.singpay.model.User;
import com.demo.singpay.model.enums.Role;
import com.demo.singpay.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository  userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo        = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepo.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));
    }

    @Transactional
    public User register(String firstName, String lastName, String email, String rawPassword) {
        String normalizedEmail = email.toLowerCase().trim();
        if (userRepo.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.ROLE_USER);
        user.setEnabled(true);
        return userRepo.save(user);
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        if (id == null) throw new UsernameNotFoundException("ID utilisateur requis");
        return userRepo.findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + id));
    }
}
