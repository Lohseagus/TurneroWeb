package unpsjb.labprog.backend.saas;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Guarda el cuerpo para poder leer el centro y, después, dejárselo al controller.
 */
final class CuerpoPedidoCacheado extends HttpServletRequestWrapper {

    private final byte[] cuerpo;

    CuerpoPedidoCacheado(HttpServletRequest request) throws IOException {
        super(request);
        this.cuerpo = request.getInputStream().readAllBytes();
    }

    byte[] cuerpo() {
        return cuerpo;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream entrada = new ByteArrayInputStream(cuerpo);
        return new ServletInputStream() {
            @Override
            public int read() {
                return entrada.read();
            }

            @Override
            public boolean isFinished() {
                return entrada.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // El cuerpo ya está en memoria.
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = StandardCharsets.UTF_8;
        String encoding = getCharacterEncoding();
        if (encoding != null && !encoding.isBlank()) {
            charset = Charset.forName(encoding);
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
        return cuerpo.length;
    }

    @Override
    public long getContentLengthLong() {
        return cuerpo.length;
    }
}
