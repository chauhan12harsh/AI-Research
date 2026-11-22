package com.harsh.research.documnet;

import com.harsh.research.common.ApiResponse;
import com.harsh.research.documnet.dto.DocumentContentResponse;
import com.harsh.research.documnet.dto.DocumentProcessingResponse;
import com.harsh.research.documnet.dto.DocumentResponse;
import com.harsh.research.documnet.dto.PageResponse;
import com.harsh.research.documnet.storage.PresignedUrl;
import com.harsh.research.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentProcessingQueryService processingQueryService;


    @PostMapping({"/api/v1/workspace/{wsId}/documents", "/api/v1/workspaces/{wsId}/documents"})
    public ApiResponse<DocumentResponse> upload(
            @CurrentUser UUID userId,
            @PathVariable UUID wsId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String displayName
            ) throws Exception {

        Document doc = documentService.upload(wsId, userId, file, displayName);

        return ApiResponse.Ok(
                DocumentResponse.from(doc)
        );
    }

    @GetMapping({"/api/v1/workspace/{wsId}/documents", "/api/v1/workspaces/{wsId}/documents"})
    public ApiResponse<PageResponse<DocumentResponse>>  list(
        @CurrentUser UUID userId,
        @PathVariable UUID wsId,
        @RequestParam(required = false) DocumentStatus status,
        @RequestParam(required = false) SourceType sourceType,
        @RequestParam(required = false) String q,
        Pageable pageable
    ) throws Exception {

        var page = documentService.search(
                wsId,userId,status,sourceType,q,pageable
        ).map(DocumentResponse::from);

        return ApiResponse.Ok(
                PageResponse.of(page)
        );
    }

    @GetMapping("/api/v1/documents/{docId}")
    public ApiResponse<DocumentResponse> get(
            @CurrentUser UUID userId,
            @PathVariable UUID docId
    ) throws Exception {

        Document doc = documentService.get(docId, userId);

        return ApiResponse.Ok(
                DocumentResponse.from(doc)
        );
    }

    @GetMapping("/api/v1/documents/{docId}/processing")
    public ApiResponse<DocumentProcessingResponse> processing(
            @CurrentUser UUID userId,
            @PathVariable UUID docId
    ) throws Exception {
        return ApiResponse.Ok(processingQueryService.get(docId, userId));
    }

    @GetMapping("/api/v1/documents/{docId}/download")
    public ApiResponse<PresignedUrl> download(
            @CurrentUser UUID userId,
            @PathVariable UUID docId
    ) throws Exception {

        PresignedUrl url = documentService.presignDownload(docId, userId);

        return ApiResponse.Ok(
                url
        );
    }

    @GetMapping("/api/v1/documents/{docId}/content")
    public ApiResponse<DocumentContentResponse> content(
            @CurrentUser UUID userId,
            @PathVariable UUID docId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20000") int limit
    ) throws Exception {

        return ApiResponse.Ok(
                documentService.getContent(
                        docId, userId,offset,limit
                )
        );
    }

    @PostMapping("/api/v1/documents/{docId}/retry")
    public ApiResponse<DocumentResponse> retry(
            @CurrentUser UUID userId,
            @PathVariable UUID docId
    ) throws Exception {

        Document doc=documentService.retry(docId,userId);
        return ApiResponse.Ok(
                DocumentResponse.from(doc)
        );
    }

    @DeleteMapping("/api/v1/documents/{docId}")
    public ApiResponse <Void> delete(
            @CurrentUser UUID userId,
            @PathVariable UUID docId
    ) throws Exception {

        documentService.delete(docId, userId);

        return ApiResponse.Ok(null);
    }



}
