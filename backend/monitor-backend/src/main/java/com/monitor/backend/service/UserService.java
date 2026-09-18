package com.monitor.backend.service;

import com.monitor.backend.model.entity.User;
import com.monitor.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    public boolean existsById(String id) {
        return userRepository.existsById(id);
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User createLocalUser(String keycloakId, String username, String email, String fullName, String phoneNumber) {
        User user = User.builder()
                .id(keycloakId)
                .username(username)
                .email(email)
                .fullName(fullName)
                .phoneNumber(phoneNumber)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }
}
