package mx.ipn.escom.tt.ensayosbackend.controller;

import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.service.FotoPerfilService;
import mx.ipn.escom.tt.ensayosbackend.service.UsuarioActualService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Foto de perfil, para cualquier rol autenticado. */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final FotoPerfilService fotoPerfilService;
    private final UsuarioActualService usuarioActual;

    // multipart con "file": JPG o PNG de hasta 5 MB; se guarda como JPEG de 512 px máximo
    @PutMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> subirFoto(@RequestPart("file") MultipartFile file) {
        fotoPerfilService.subir(usuarioActual.obtener(), file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/photo")
    public ResponseEntity<Void> eliminarFoto() {
        fotoPerfilService.eliminar(usuarioActual.obtener());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> miFoto() {
        return foto(usuarioActual.obtener().getIdUsuario());
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> foto(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(fotoPerfilService.leer(usuarioActual.obtener(), id));
    }
}
