package com.team10.backend.domain.user.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Tesseract 인식률을 높이기 위한 신분증 이미지 전처리기.
 *
 * <p>처리 순서:
 * <ol>
 *   <li>300 DPI 기준으로 리사이즈 (너무 작은 이미지 확대)</li>
 *   <li>그레이스케일 변환</li>
 *   <li>Otsu 이진화 (배경/텍스트 분리)</li>
 * </ol>
 */
@Slf4j
@Component
public class ImagePreprocessor {

    /** Tesseract 권장 최소 너비 (px) */
    private static final int TARGET_WIDTH = 2000;

    /**
     * 원본 이미지를 전처리하여 새 임시 파일로 저장한다.
     *
     * @param original 원본 이미지 파일
     * @return 전처리된 이미지 임시 파일
     */
    public File preprocess(File original) throws IOException {
        BufferedImage src = ImageIO.read(original);
        if (src == null) {
            throw new IOException("이미지를 읽을 수 없습니다: " + original.getName());
        }

        // 1. 그레이스케일
        BufferedImage gray = toGrayscale(src);

        // 2. 밝은 카드 영역 크롭 (어두운 배경 제거)
        BufferedImage cropped = cropBrightRegion(gray);

        // 3. 리사이즈 (너비가 TARGET_WIDTH 보다 작으면 확대)
        BufferedImage resized = resize(cropped);

        // 4. Otsu 이진화
        BufferedImage binary = binarize(resized);

        File output = File.createTempFile("ocr-preprocessed-", ".png");
        ImageIO.write(binary, "png", output);

        log.debug("[OCR-PREP] 전처리 완료 — {}x{} → {}x{}, output={}",
                src.getWidth(), src.getHeight(),
                binary.getWidth(), binary.getHeight(),
                output.getAbsolutePath());

        return output;
    }

    /**
     * 그레이스케일 이미지에서 밝은 카드 영역을 크롭한다.
     * 신분증은 밝은 배경(평균 밝기 높음)이고 주변은 어두운 경우에 효과적이다.
     * 크롭 범위를 못 찾으면 원본을 그대로 반환한다.
     */
    private BufferedImage cropBrightRegion(BufferedImage gray) {
        int width  = gray.getWidth();
        int height = gray.getHeight();

        // 밝기 임계값: 128 이상인 픽셀을 "카드 영역"으로 간주
        int threshold = 128;
        int margin = (int) (Math.min(width, height) * 0.02); // 2% 여백

        int minX = width, maxX = 0, minY = height, maxY = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = gray.getRaster().getSample(x, y, 0);
                if (pixel > threshold) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // 유효한 크롭 영역이 전체의 30% 이상일 때만 적용
        int cropWidth  = maxX - minX;
        int cropHeight = maxY - minY;
        if (cropWidth < width * 0.3 || cropHeight < height * 0.3) {
            log.debug("[OCR-PREP] 카드 영역 감지 실패, 원본 사용");
            return gray;
        }

        // 여백 추가
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(width,  maxX + margin);
        maxY = Math.min(height, maxY + margin);

        log.debug("[OCR-PREP] 카드 크롭 — ({},{}) ~ ({},{})", minX, minY, maxX, maxY);
        return gray.getSubimage(minX, minY, maxX - minX, maxY - minY);
    }

    private BufferedImage resize(BufferedImage src) {
        if (src.getWidth() >= TARGET_WIDTH) {
            return src;
        }
        double scale = (double) TARGET_WIDTH / src.getWidth();
        int newWidth  = TARGET_WIDTH;
        int newHeight = (int) (src.getHeight() * scale);

        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return result;
    }

    private BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    /**
     * Otsu 알고리즘으로 최적 임계값을 계산하여 이진화한다.
     * 배경을 흰색(255), 텍스트를 검정(0)으로 분리한다.
     */
    private BufferedImage binarize(BufferedImage gray) {
        int width  = gray.getWidth();
        int height = gray.getHeight();

        // 히스토그램 계산
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = gray.getRaster().getSample(x, y, 0);
                histogram[pixel]++;
            }
        }

        // Otsu 임계값 계산
        int threshold = otsuThreshold(histogram, width * height);

        // 이진화
        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = gray.getRaster().getSample(x, y, 0);
                binary.getRaster().setSample(x, y, 0, pixel > threshold ? 255 : 0);
            }
        }
        return binary;
    }

    private int otsuThreshold(int[] histogram, int total) {
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += (double) i * histogram[i];

        double sumB = 0;
        int wB = 0;
        double maxVariance = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += (double) t * histogram[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double variance = (double) wB * wF * (mB - mF) * (mB - mF);

            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = t;
            }
        }
        return threshold;
    }
}
