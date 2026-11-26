package com.gymwork.api.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Identifica o tipo real da imagem pelos primeiros bytes (não confia na extensão nem no
 * Content-Type enviado) e lê as dimensões quando possível.
 */
public final class ImageInspector {

    public enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }
    }

    public record ImageInfo(ImageType type, Integer width, Integer height) {
    }

    private ImageInspector() {
    }

    public static Optional<ImageInfo> inspect(byte[] content) {
        ImageType type = detect(content);
        if (type == null) {
            return Optional.empty();
        }
        Integer width = null;
        Integer height = null;
        if (type != ImageType.WEBP) {
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(input);
                        width = reader.getWidth(0);
                        height = reader.getHeight(0);
                    } finally {
                        reader.dispose();
                    }
                }
            } catch (IOException | RuntimeException e) {
                return Optional.empty(); // cabeçalho válido, mas imagem corrompida
            }
            if (width == null) {
                return Optional.empty();
            }
        }
        return Optional.of(new ImageInfo(type, width, height));
    }

    static ImageType detect(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return ImageType.PNG;
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }
}
