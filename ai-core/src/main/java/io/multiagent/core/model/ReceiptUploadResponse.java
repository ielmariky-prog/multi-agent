package io.multiagent.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReceiptUploadResponse {
    private String id;
    private String originalFilename;
    private String binaryHash;
    private String textHash;
    private boolean binaryDuplicate;
    private boolean textDuplicate;
    private String duplicateOfBinary;
    private String duplicateOfText;
    private String ocrText;
    private String extractedExpenseJson;
}
