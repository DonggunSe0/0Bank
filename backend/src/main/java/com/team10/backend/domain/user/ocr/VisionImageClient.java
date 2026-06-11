package com.team10.backend.domain.user.ocr;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Google Cloud Vision API 클라이언트 래퍼.
 *
 * <p>서비스 계정 JSON 키로 인증하고,
 * {@code DOCUMENT_TEXT_DETECTION} 으로 신분증 텍스트를 추출한다.
 * (DOCUMENT_TEXT_DETECTION 은 밀집된 문서 텍스트에 최적화되어
 * 일반 TEXT_DETECTION 보다 한글 인식률이 높다.)
 *
 * <p>키 파일이 없는 환경(로컬 개발, CI)에서는 클라이언트를 초기화하지 않고
 * 경고 로그만 남긴다. OCR 호출 시 {@link IllegalStateException}이 발생한다.
 */
@Slf4j
@Component
public class VisionImageClient {

    @Value("${ocr.google-vision-key-path}")
    private Resource keyResource;

    private ImageAnnotatorClient visionClient;

    @PostConstruct
    public void init() {
        try {
            if (keyResource == null || !keyResource.exists()) {
                log.warn("[Vision] Google Cloud Vision 키 파일이 존재하지 않습니다. OCR 기능이 제한됩니다. 경로={}",
                        keyResource != null ? keyResource.getDescription() : "null");
                return;
            }

            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(keyResource.getInputStream())
                    .createScoped("https://www.googleapis.com/auth/cloud-vision");

            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            this.visionClient = ImageAnnotatorClient.create(settings);
            log.info("[Vision] Google Cloud Vision 클라이언트 초기화 완료");

        } catch (IOException e) {
            log.error("[Vision] Google Cloud Vision 클라이언트 초기화 실패", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (visionClient != null) {
            visionClient.close();
            log.info("[Vision] Google Cloud Vision 클라이언트 종료 완료");
        }
    }

    /**
     * 이미지 바이트에서 텍스트를 추출한다.
     *
     * @param imageBytes 신분증 이미지 바이트
     * @return OCR 추출 전체 텍스트
     * @throws IOException Vision API 호출 실패 시
     * @throws IllegalStateException 클라이언트 미초기화 시 (키 파일 없음)
     */
    public String extractText(byte[] imageBytes) throws IOException {
        if (visionClient == null) {
            throw new IllegalStateException("Vision API 클라이언트가 초기화되지 않았습니다. GCP 키 파일을 확인하세요.");
        }

        ByteString imgBytes = ByteString.copyFrom(imageBytes);
        Image image = Image.newBuilder().setContent(imgBytes).build();

        Feature feature = Feature.newBuilder()
                .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                .build();

        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build();

        BatchAnnotateImagesResponse response = visionClient.batchAnnotateImages(List.of(request));
        AnnotateImageResponse imageResponse = response.getResponses(0);

        if (imageResponse.hasError()) {
            throw new IOException("Vision API 오류: " + imageResponse.getError().getMessage());
        }

        String text = imageResponse.getFullTextAnnotation().getText();
        log.debug("[Vision] 텍스트 추출 완료 — {}자", text.length());
        return text;
    }
}
