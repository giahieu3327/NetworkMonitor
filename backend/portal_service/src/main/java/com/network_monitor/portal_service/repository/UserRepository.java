
package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // Kiểm tra username đã tồn tại hay chưa.
    boolean existsByUsername(String username);

    // Kiểm tra email đã tồn tại hay chưa.
    boolean existsByEmail(String email);

    // Tìm người dùng theo username.
    Optional<User> findByUsername(String username);

    // Tìm người dùng theo email.
    Optional<User> findByEmail(String email);
}
