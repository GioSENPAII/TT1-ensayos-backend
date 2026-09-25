package mx.ipn.escom.tt.ensayosbackend.dto;

/** Política de contraseñas (RNF-10): mínimo 8 caracteres, una mayúscula, una minúscula y un número. */
public final class PasswordPolicy {
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";
    public static final String MENSAJE =
            "La contraseña debe tener mínimo 8 caracteres, una mayúscula, una minúscula y un número";

    private PasswordPolicy() {
    }
}
