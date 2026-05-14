package com.camera.app.dashboard;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.iam.repository.UserRepository;
import com.camera.app.poc.entity.PocStatus;
import com.camera.app.poc.repository.PocExecutionLogRepository;
import com.camera.app.poc.repository.PocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PocRepository pocRepository;
    private final PocExecutionLogRepository pocExecutionLogRepository;

    @Override
    public DashboardOverviewResponse overview() {
        return DashboardOverviewResponse.builder()
                .userCount(userRepository.count())
                .assetCount(assetRepository.count())
                .onlineAssetCount(assetRepository.countByOnline(true))
                .pocCount(pocRepository.countByStatusNot(PocStatus.DELETED))
                .enabledPocCount(pocRepository.countByEnabledTrueAndStatusNot(PocStatus.DELETED))
                .recentExecutionCount(pocExecutionLogRepository.countByCreatedAtAfter(LocalDateTime.now().minusHours(24)))
                .systemHealthy(true)
                .build();
    }
}
