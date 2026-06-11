package de.muenchen.mcmp.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class UnifiedStorageController {

    private final UnifiedStorageService unifiedStorageService;

    @GetMapping("/unified")
    public Page<UnifiedStorageItemListDto> getUnifiedStorage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> types,
            Pageable pageable) {
        return unifiedStorageService.getUnifiedStorage(search, types, pageable);
    }

    @GetMapping("/unified/{type}/{uuid}")
    public UnifiedStorageItemDto getUnifiedStorageItem(
            @PathVariable String type,
            @PathVariable String uuid) {
        return unifiedStorageService.getUnifiedStorageItem(uuid, StorageType.valueOf(type));
    }

    @GetMapping("/unified/server/{serverId}/mounts")
    public List<UnifiedStorageMountItemDto> getUnifiedStorageMountsByServerId(
            @PathVariable Long serverId) {
        return unifiedStorageService.getUnifiedStorageMountsByServerId(serverId);
    }

    @GetMapping("/unified/{type}/{uuid}/snapshots")
    public List<UnifiedStorageSnapshotListDto> getUnifiedStorageSnapshots(
            @PathVariable String type,
            @PathVariable String uuid) {
        return unifiedStorageService.getUnifiedStorageSnapshots(uuid, StorageType.valueOf(type));
    }
}
