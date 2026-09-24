package com.akshaya.elmsbackend.rag;

import com.akshaya.elmsbackend.common.exception.AppException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanyPolicyRagService {

    private static final int RESULT_LIMIT = 4;
    private static final double SIMILARITY_THRESHOLD = 0.65;

    private final VectorStore vectorStore;

    public CompanyPolicyRagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> searchPolicy(String question) {
        if (question == null || question.isBlank()) {
            throw new AppException(
                    RagErrorCode.EMPTY_POLICY_QUERY,
                    "Policy search question must not be empty"
            );
        }

        SearchRequest request = SearchRequest.builder()
                .query(question.trim())
                .topK(RESULT_LIMIT)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();

        return vectorStore.similaritySearch(request);
    }
}
