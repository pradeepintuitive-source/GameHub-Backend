package com.gamehub.player.infrastructure;

import com.gamehub.persistence.infrastructure.BaseEntity;
import com.gamehub.security.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean guest;

    @Column(nullable = false, length = 255)
    private String roles;

    public Set<UserRole> roleSet() {
        if (roles == null || roles.isBlank()) {
            return EnumSet.noneOf(UserRole.class);
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(UserRole::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
    }

    public void roleSet(Set<UserRole> roleSet) {
        this.roles = roleSet.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
