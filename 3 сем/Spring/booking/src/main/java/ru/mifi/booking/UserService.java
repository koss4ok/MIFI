package ru.mifi.booking;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.common.ApiException;

@Service
public class UserService {
    public record UserDto(Long id, String username, String role) {}
    public record TokenDto(String token, String type, long expiresIn) {}
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    UserService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder) { this.users = users; this.passwords = passwords; this.encoder = encoder; }

    @Transactional public UserDto create(String username, String password, String role) {
        if (!role.equals("USER") && !role.equals("ADMIN")) throw new ApiException(HttpStatus.BAD_REQUEST, "Неизвестная роль");
        if (users.existsByUsername(username)) throw new ApiException(HttpStatus.CONFLICT, "Имя пользователя уже занято");
        return dto(users.save(new User(username, passwords.encode(password), role)));
    }
    @Transactional(readOnly = true) public TokenDto login(String username, String password) {
        User user = users.findByUsername(username).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Неверное имя или пароль"));
        if (!passwords.matches(password, user.password)) throw new ApiException(HttpStatus.UNAUTHORIZED, "Неверное имя или пароль");
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(user.id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("scope", user.role).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenDto(token, "Bearer", 3600);
    }
    @Transactional(readOnly = true) public List<UserDto> all() { return users.findAll().stream().map(UserService::dto).toList(); }
    @Transactional public UserDto update(Long id, String username, String password, String role) {
        User user = get(id);
        if (!user.username.equals(username) && users.existsByUsername(username)) throw new ApiException(HttpStatus.CONFLICT, "Имя пользователя уже занято");
        if (!role.equals("USER") && !role.equals("ADMIN")) throw new ApiException(HttpStatus.BAD_REQUEST, "Неизвестная роль");
        user.username = username; user.role = role;
        if (password != null && !password.isBlank()) user.password = passwords.encode(password);
        return dto(user);
    }
    @Transactional public void delete(Long id) { users.delete(get(id)); }
    User get(Long id) { return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Пользователь не найден")); }
    private static UserDto dto(User user) { return new UserDto(user.id, user.username, user.role); }
}
