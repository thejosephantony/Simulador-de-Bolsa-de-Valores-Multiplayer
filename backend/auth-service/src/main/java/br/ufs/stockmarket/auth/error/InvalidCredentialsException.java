package br.ufs.stockmarket.auth.error;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas.");
    }
}
