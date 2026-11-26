package com.gymwork.api.assessment;

import com.gymwork.api.storage.FileCleanup;
import com.gymwork.api.user.UserDataDeletionHandler;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Exclusão de conta: apaga avaliações, medidas e fotos (arquivos depois do commit). */
@Component
@Order(20)
class AssessmentDataDeletionHandler implements UserDataDeletionHandler {

    private final BodyAssessmentRepository assessmentRepository;
    private final ProgressPhotoRepository photoRepository;
    private final FileCleanup fileCleanup;

    AssessmentDataDeletionHandler(
            BodyAssessmentRepository assessmentRepository,
            ProgressPhotoRepository photoRepository,
            FileCleanup fileCleanup) {
        this.assessmentRepository = assessmentRepository;
        this.photoRepository = photoRepository;
        this.fileCleanup = fileCleanup;
    }

    @Override
    public void deleteUserData(UUID userId) {
        fileCleanup.deleteAfterCommit(photoRepository.findStorageKeysByUserId(userId));
        assessmentRepository.deleteAllByUserId(userId);
    }
}
