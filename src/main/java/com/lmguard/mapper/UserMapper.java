package com.lmguard.mapper;

import com.lmguard.dto.auth.UserResponse;
import com.lmguard.entity.User;
import org.springframework.stereotype.Component;

/** Entity to DTO conversion for users. The password hash never crosses this boundary. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
