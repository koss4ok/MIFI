package ru.mifi.booking;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {
    public record Credentials(@NotBlank @Size(max = 100) String username, @NotBlank @Size(min = 6, max = 72) String password) {}
    public record UserInput(@NotBlank @Size(max = 100) String username, @NotBlank @Size(min = 6, max = 72) String password, @NotBlank String role) {}
    public record UpdateInput(@NotBlank @Size(max = 100) String username, @Size(min = 6, max = 72) String password, @NotBlank String role) {}
    private final UserService service;
    UserController(UserService service) { this.service = service; }
    @PostMapping("/auth/register") public UserService.TokenDto register(@Valid @RequestBody Credentials input) {
        service.create(input.username(), input.password(), "USER"); return service.login(input.username(), input.password());
    }
    @PostMapping("/auth/login") public UserService.TokenDto login(@Valid @RequestBody Credentials input) { return service.login(input.username(), input.password()); }
    @GetMapping("/users") @PreAuthorize("hasAuthority('SCOPE_ADMIN')") public List<UserService.UserDto> users() { return service.all(); }
    @PostMapping("/users") @PreAuthorize("hasAuthority('SCOPE_ADMIN')") public UserService.UserDto create(@Valid @RequestBody UserInput input) { return service.create(input.username(), input.password(), input.role()); }
    @PutMapping("/users/{id}") @PreAuthorize("hasAuthority('SCOPE_ADMIN')") public UserService.UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateInput input) { return service.update(id, input.username(), input.password(), input.role()); }
    @DeleteMapping("/users/{id}") @PreAuthorize("hasAuthority('SCOPE_ADMIN')") public void delete(@PathVariable Long id) { service.delete(id); }
}
