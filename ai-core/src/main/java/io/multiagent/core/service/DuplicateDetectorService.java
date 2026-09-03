package io.multiagent.core.service;

import io.multiagent.core.model.ReceiptDuplicateInfo;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DuplicateDetectorService {

    private final Map<String, String> binaryIndex = new ConcurrentHashMap<>();
    private final Map<String, String> textIndex = new ConcurrentHashMap<>();

    public ReceiptDuplicateInfo track(String id, String binaryHash, String textHash) {
        String binaryOf = binaryIndex.putIfAbsent(binaryHash, id);
        String textOf = textIndex.putIfAbsent(textHash, id);
        return new ReceiptDuplicateInfo(binaryOf, textOf);
    }
}
