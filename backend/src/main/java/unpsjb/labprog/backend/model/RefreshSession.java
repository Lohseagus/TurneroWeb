package unpsjb.labprog.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un refresh token emitido. Se guarda el identificador (jti), nunca el token.
 *
 * Cada login abre una familia. Al renovar, el token usado queda marcado como
 * rotado y se emite otro de la misma familia. Si un token rotado vuelve a
 * aparecer pasado el margen de gracia, se borra la familia entera: alguien
 * está usando una copia.
 */
@Entity
@Table(name = "refresh_sessions", indexes = {
        @Index(name = "idx_refresh_sessions_family", columnList = "family_id"),
        @Index(name = "idx_refresh_sessions_user", columnList = "user_id"),
        @Index(name = "idx_refresh_sessions_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RefreshSession {

    @Id
    @Column(length = 36)
    private String jti;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    public RefreshSession(String jti, String familyId, Long userId, Instant expiresAt) {
        this.jti = jti;
        this.familyId = familyId;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
