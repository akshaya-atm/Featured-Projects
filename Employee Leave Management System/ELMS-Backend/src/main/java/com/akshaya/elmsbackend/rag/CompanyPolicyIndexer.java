package com.akshaya.elmsbackend.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
public class CompanyPolicyIndexer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CompanyPolicyIndexer.class);

    private static final String POLICY_KEY = "abc-corp-leave-policy";

    // Increment this if the chunking rules change.
    private static final String INDEX_FORMAT_VERSION = "1";

    private final VectorStore vectorStore;
    private final Resource policyResource;

    public CompanyPolicyIndexer(
            VectorStore vectorStore,
            @Value("classpath:policy/ABC_Corp_Employee_Leave_Policy.md")
            Resource policyResource) {

        this.vectorStore = vectorStore;
        this.policyResource = policyResource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexPolicy() {
        TextReader reader = new TextReader(policyResource);
        List<Document> sourceDocuments = reader.get();

        if (sourceDocuments.isEmpty()) {
            throw new IllegalStateException(
                    "Company leave policy document is empty"
            );
        }

        String policyText = sourceDocuments.get(0).getText();
        String policyContentHash = calculateContentHash(policyText);

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(350)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(20)
                .withMaxNumChunks(100)
                .withKeepSeparator(true)
                .build();

        List<Document> splitDocuments =
                splitter.apply(sourceDocuments);

        if (splitDocuments.isEmpty()) {
            throw new IllegalStateException(
                    "Company leave policy produced no indexable chunks"
            );
        }

        List<Document> policyChunks = IntStream
                .range(0, splitDocuments.size())
                .mapToObj(index -> createPolicyChunk(
                        splitDocuments.get(index),
                        index,
                        policyContentHash
                ))
                .toList();

        vectorStore.add(policyChunks);

        vectorStore.delete(
                "policyKey == '" + POLICY_KEY
                        + "' && policyContentHash != '"
                        + policyContentHash + "'"
        );

        LOGGER.info(
                "Indexed {} company policy chunks with content hash {}",
                policyChunks.size(),
                policyContentHash
        );
    }

    private Document createPolicyChunk(
            Document chunk,
            int index,
            String policyContentHash) {

        String documentId = UUID.nameUUIDFromBytes(
                (POLICY_KEY + ":" + policyContentHash + ":" + index)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();

        return Document.builder()
                .id(documentId)
                .text(chunk.getText())
                .metadata(chunk.getMetadata())
                .metadata("policyKey", POLICY_KEY)
                .metadata("policyContentHash", policyContentHash)
                .metadata("chunkNumber", index + 1)
                .build();
    }

    private String calculateContentHash(String policyText) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    (INDEX_FORMAT_VERSION + "\n" + policyText)
                            .getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }
}
