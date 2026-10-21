package mx.ipn.escom.tt.ensayosbackend.dto;

/**
 * Política de contraseñas (RNF-10): mínimo 8 caracteres, una mayúscula, una minúscula, un número y un
 * carácter especial (cualquier símbolo que no sea letra, número ni espacio).
 */
public final class PasswordPolicy {
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\p{L}\\p{N}\\s]).{8,}$";
    public static final String MENSAJE =
            "La contraseña debe tener mínimo 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial";

    private PasswordPolicy() {
    }
}
