package com.harsh.research.ai.embed;

import com.harsh.research.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/test")
public class TestController {

    private final EmbeddingModel embeddingModel;

    @GetMapping("/embedding")
    public ApiResponse<?> createEmbedding(@RequestParam(required = true) String text){

        return ApiResponse.Ok(embeddingModel.embed(text));
    }
}
