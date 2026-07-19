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
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false, defaultValue = "false") boolean favorites,
            Pageable pageable) {
        return unifiedStorageService.getUnifiedStorage(search, categories, favorites, pageable);
    }

    @GetMapping("/unified/{type}/{uuid}")
    public UnifiedStorageItemDto getUnifiedStorageItem(
            @PathVariable String type,
            @PathVariable String uuid) {
        return unifiedStorageService.getUnifiedStorageItem(uuid, StorageType.valueOf(type));
    }

    @PutMapping("/unified/{type}/{uuid}/favorite")
    public void addStorageToFavorites(@PathVariable String type, @PathVariable String uuid) {
        unifiedStorageService.addStorageToFavorites(uuid, StorageType.valueOf(type));
    }

    @DeleteMapping("/unified/{type}/{uuid}/favorite")
    public void removeStorageFromFavorites(@PathVariable String type, @PathVariable String uuid) {
        unifiedStorageService.removeStorageFromFavorites(uuid, StorageType.valueOf(type));
    }

    @GetMapping("/unified/server/{serverId}/mounts")
    public List<UnifiedStorageMountItemDto> getUnifiedStorageMountsByServerId(
            @PathVariable Long serverId) {
        return unifiedStorageService.getUnifiedStorageMountsByServerId(serverId);
    }

    @GetMapping("/unified/appservice/{appserviceId}")
    public List<UnifiedStorageItemListDto> getUnifiedStorageByAppserviceId(
            @PathVariable Long appserviceId) {
        return unifiedStorageService.getUnifiedStorageByAppserviceId(appserviceId);
    }

    @GetMapping("/unified/{type}/{uuid}/snapshots")
    public List<UnifiedStorageSnapshotListDto> getUnifiedStorageSnapshots(
            @PathVariable String type,
            @PathVariable String uuid) {
        return unifiedStorageService.getUnifiedStorageSnapshots(uuid, StorageType.valueOf(type));
    }
}
