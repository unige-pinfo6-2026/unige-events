package ch.unige.events.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Gère la sauvegarde des fichiers image uploadés.
 * Valide le type MIME, génère un nom UUID unique et copie le fichier
 * dans le répertoire configuré par {@code app.uploads.path}.
 *
 * @return le chemin public de l'image : {@code /api/uploads/{uuid}.{ext}}
 */
@ApplicationScoped
public class FileStorageService {

    @ConfigProperty(name = "app.uploads.path", defaultValue = "/tmp/unige-events-uploads")
    String uploadsPath;

    public String saveImage(FileUpload fileUpload) {
        String contentType = fileUpload.contentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException(
                    "File must be an image (accepted: image/jpeg, image/png, image/webp, image/gif)");
        }

        String originalName = fileUpload.fileName();
        String extension = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".bin";

        String uniqueFileName = UUID.randomUUID() + extension;
        Path targetDir = Path.of(uploadsPath);
        Path targetFile = targetDir.resolve(uniqueFileName);
        try {
            Files.createDirectories(targetDir);
            Files.copy(fileUpload.uploadedFile(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to save image: " + e.getMessage());
        }

        return "/api/uploads/" + uniqueFileName;
    }
}
