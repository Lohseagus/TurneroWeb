package unpsjb.labprog.backend.business.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.RefreshSession;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") String familyId);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
