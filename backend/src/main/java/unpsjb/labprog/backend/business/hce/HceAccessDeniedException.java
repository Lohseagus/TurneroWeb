package unpsjb.labprog.backend.business.hce;

public class HceAccessDeniedException extends RuntimeException {

    private final int status;

    public HceAccessDeniedException(String message) {
        this(message, 403);
    }

    public HceAccessDeniedException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
