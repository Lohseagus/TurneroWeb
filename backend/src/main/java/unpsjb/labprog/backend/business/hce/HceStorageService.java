package unpsjb.labprog.backend.business.hce;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HceStorageService {

    private static final Set<String> TIPOS = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif");

    private final Path root;
    private final long maxBytes;

    public HceStorageService(
            @Value("${hce.storage.path:./data/hce}") String path,
            @Value("${hce.storage.max-bytes:10485760}") long maxBytes) {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de la historia clínica", e);
        }
    }

    public String guardar(Integer pacienteId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (archivo.getSize() > maxBytes) {
            throw new IllegalArgumentException("El archivo supera el máximo de " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String contentType = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de archivo no permitido. Use PDF o imagen (JPEG, PNG, WEBP, GIF).");
        }
        String nombre = sanitizar(archivo.getOriginalFilename());
        String key = pacienteId + "/" + UUID.randomUUID() + "_" + nombre;
        Path destino = resolver(key);
        try {
            Files.createDirectories(destino.getParent());
            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo", e);
        }
        return key;
    }

    public Path resolver(String storageKey) {
        if (storageKey == null || storageKey.contains("..")) {
            throw new IllegalArgumentException("Ruta de archivo inválida");
        }
        Path destino = root.resolve(storageKey).normalize();
        if (!destino.startsWith(root)) {
            throw new IllegalArgumentException("Ruta de archivo inválida");
        }
        return destino;
    }

    public long maxBytes() {
        return maxBytes;
    }

    private String sanitizar(String original) {
        String base = original == null || original.isBlank() ? "archivo" : original;
        base = base.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank()) {
            base = "archivo";
        }
        return base.length() > 80 ? base.substring(base.length() - 80) : base;
    }
}
