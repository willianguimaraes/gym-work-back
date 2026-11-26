package com.gymwork.api.assessment;

import com.gymwork.api.assessment.dto.PhotoResponse;
import com.gymwork.api.assessment.dto.PhotoUrlResponse;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.common.exception.ResourceNotFoundException;
import com.gymwork.api.storage.FileCleanup;
import com.gymwork.api.storage.ImageInspector;
import com.gymwork.api.storage.ImageInspector.ImageInfo;
import com.gymwork.api.storage.SignedUrlService;
import com.gymwork.api.storage.SignedUrlService.SignedUrl;
import com.gymwork.api.storage.StorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fotos de evolução. Valida o tipo real pelos bytes (JPEG, PNG ou WebP) e o tamanho (até 10 MB),
 * grava no storage privado e entrega só por URL assinada e temporária.
 */
@Service
public class PhotoService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_PER_ASSESSMENT = 12;

    private final ProgressPhotoRepository photoRepository;
    private final AssessmentService assessmentService;
    private final StorageService storageService;
    private final SignedUrlService signedUrlService;
    private final FileCleanup fileCleanup;
    private final AssessmentViews views;

    public PhotoService(
            ProgressPhotoRepository photoRepository,
            AssessmentService assessmentService,
            StorageService storageService,
            SignedUrlService signedUrlService,
            FileCleanup fileCleanup,
            AssessmentViews views) {
        this.photoRepository = photoRepository;
        this.assessmentService = assessmentService;
        this.storageService = storageService;
        this.signedUrlService = signedUrlService;
        this.fileCleanup = fileCleanup;
        this.views = views;
    }

    @Transactional
    public PhotoResponse upload(UUID userId, UUID assessmentId, MultipartFile file, PhotoPosition position) {
        BodyAssessment assessment = assessmentService.find(userId, assessmentId);
        if (file == null || file.isEmpty()) {
            throw new InvalidFieldException("file", "Envie uma imagem");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidFieldException("file", "A imagem pode ter no máximo 10 MB");
        }
        if (photoRepository.countByAssessment(assessmentId) >= MAX_PER_ASSESSMENT) {
            throw new InvalidFieldException("file", "Limite de %d fotos por avaliação".formatted(MAX_PER_ASSESSMENT));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ImageInfo image = ImageInspector.inspect(content)
                .orElseThrow(() -> new InvalidFieldException("file", "Formato não suportado: envie JPEG, PNG ou WebP"));

        String key = "photos/" + UUID.randomUUID() + "." + image.type().extension();
        storageService.put(key, content);
        fileCleanup.deleteOnRollback(key);
        ProgressPhoto photo = photoRepository.save(new ProgressPhoto(
                assessment,
                position == null ? PhotoPosition.OTHER : position,
                key,
                image.type().contentType(),
                content.length,
                image.width(),
                image.height()));
        return views.photo(photo);
    }

    /** Nova URL temporária (a anterior expira em poucos minutos). */
    @Transactional(readOnly = true)
    public PhotoUrlResponse url(UUID userId, UUID photoId) {
        SignedUrl url = signedUrlService.sign(find(userId, photoId).getStorageKey());
        return new PhotoUrlResponse(url.url(), url.expiresAt());
    }

    @Transactional
    public PhotoResponse move(UUID userId, UUID photoId, PhotoPosition position) {
        ProgressPhoto photo = find(userId, photoId);
        photo.moveTo(position);
        return views.photo(photo);
    }

    @Transactional
    public void delete(UUID userId, UUID photoId) {
        ProgressPhoto photo = find(userId, photoId);
        photoRepository.delete(photo);
        fileCleanup.deleteAfterCommit(List.of(photo.getStorageKey()));
    }

    private ProgressPhoto find(UUID userId, UUID photoId) {
        return photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada"));
    }
}
