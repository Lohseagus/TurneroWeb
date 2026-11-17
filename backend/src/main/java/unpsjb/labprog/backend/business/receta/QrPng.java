package unpsjb.labprog.backend.business.receta;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class QrPng {

    private QrPng() {
    }

    public static byte[] de(String texto, int tamanio) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamanio, tamanio);
            BufferedImage image = new BufferedImage(tamanio, tamanio, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < tamanio; x++) {
                for (int y = 0; y < tamanio; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF1C2430 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo armar el código QR", e);
        }
    }
}
