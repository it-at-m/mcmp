package de.muenchen.mcmp.errorlog;

import de.muenchen.mcmp.common.OffsetBasedPageRequest;
import de.muenchen.mcmp.security.IsAdmin;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping(value = "/error-logs")
@AllArgsConstructor
@IsAdmin
public class ErrorLogController {

    private final ErrorLogService errorLogService;
    private final ErrorLogMapper errorLogMapper;

    @GetMapping
    public Page<ErrorLogDTO> getErrorLogs(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "reference", required = false) String reference) {
        if (reference != null && !reference.isBlank()) {
            List<ErrorLogDTO> match = errorLogService.findSummaryByReference(reference)
                    .map(errorLogMapper::toDTO)
                    .map(List::of)
                    .orElseGet(List::of);
            return new PageImpl<>(match, Pageable.ofSize(Math.max(limit, 1)), match.size());
        }

        final Pageable pageable = (limit == -1)
                ? Pageable.unpaged(Sort.by(Sort.Direction.DESC, "id"))
                : new OffsetBasedPageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "id"));
        return errorLogService.getErrorLogs(pageable).map(errorLogMapper::toDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ErrorLogDTO> getErrorLogDetail(@PathVariable Long id) {
        return errorLogService.getErrorLogDetail(id)
                .map(errorLogMapper::toDetailDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
