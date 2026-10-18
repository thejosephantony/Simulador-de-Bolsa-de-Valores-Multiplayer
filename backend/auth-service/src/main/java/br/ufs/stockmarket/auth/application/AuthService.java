package br.ufs.stockmarket.auth.application;

import br.ufs.stockmarket.auth.api.AuthResponse;
import br.ufs.stockmarket.auth.api.LoginRequest;
import br.ufs.stockmarket.auth.api.RegisterRequest;
import br.ufs.stockmarket.auth.api.UserResponse;
import br.ufs.stockmarket.auth.domain.UserEntity;
import br.ufs.stockmarket.auth.domain.UserRepository;
import br.ufs.stockmarket.auth.error.ConflictException;
import br.ufs.stockmarket.auth.error.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username já está em uso.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail já está em uso.");
        }

        UserEntity user = userRepository.save(
                new UserEntity(username, email, passwordEncoder.encode(request.password()))
        );

        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();

        UserEntity user = userRepository
                .findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return response(user);
    }

    private AuthResponse response(UserEntity user) {
        return new AuthResponse(
                jwtService.issue(user),
                "Bearer",
                jwtService.expirationSeconds(),
                UserResponse.from(user)
        );
    }
}
