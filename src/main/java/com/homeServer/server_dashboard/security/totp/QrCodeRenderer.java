package com.homeServer.server_dashboard.security.totp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * Gera o QR code do enrollment no servidor e devolve um {@code data:} URI, para que a pagina nao
 * precise carregar nenhuma biblioteca de terceiros — e, principalmente, para que o segredo TOTP nao
 * saia da aplicacao rumo a um servico externo de geracao de QR.
 */
@Component
public class QrCodeRenderer {

    private static final int SIZE_IN_PIXELS = 240;
    private static final int QUIET_ZONE_IN_MODULES = 2;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    /**
     * @return {@code data:image/png;base64,...}, pronto para o atributo {@code src} de uma imagem
     */
    public String renderAsDataUri(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    SIZE_IN_PIXELS,
                    SIZE_IN_PIXELS,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, QUIET_ZONE_IN_MODULES));
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(toPngBytes(matrix));
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Falha ao gerar o QR code do enrollment de 2FA", e);
        }
    }

    /**
     * Converte a matriz em PNG sem o modulo {@code zxing-javase}: o {@code MatrixToImageWriter}
     * dele faria exatamente isto, e a dependencia extra nao se paga.
     */
    private static byte[] toPngBytes(BitMatrix matrix) throws IOException {
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", png);
        return png.toByteArray();
    }
}
