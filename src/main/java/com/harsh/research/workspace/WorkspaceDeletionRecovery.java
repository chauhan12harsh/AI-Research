package com.harsh.research.workspace;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkspaceDeletionRecovery {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;

    @Scheduled(fixedDelayString = "${app.deletion.workspace-reconcile-interval-ms:120000}")
    public void retryIncompleteWorkspaceDeletions() {
        for (Workspace workspace : workspaceRepository.findPendingCleanup(
                PageRequest.of(0, 20)
        )) {
            try {
                workspaceService.delete(workspace.getId(), workspace.getOwnerId());
            } catch (Exception ignored) {
                // Keep the workspace soft-deleted; the next pass resumes cleanup.
            }
        }
    }
}
