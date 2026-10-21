package com.kvs.academic.repo;

import com.kvs.academic.entity.VideoWatchProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoWatchProgressRepository extends JpaRepository<VideoWatchProgress, UUID> {
    Optional<VideoWatchProgress> findByAssetIdAndStudentId(UUID assetId, UUID studentId);
    List<VideoWatchProgress> findByAssetId(UUID assetId);
    List<VideoWatchProgress> findByStudentIdIn(Collection<UUID> studentIds);
}
