package br.ufs.stockmarket.auth.application;

import br.ufs.stockmarket.auth.api.UserResponse;
import br.ufs.stockmarket.auth.domain.UserRepository;
import br.ufs.stockmarket.auth.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }
}
