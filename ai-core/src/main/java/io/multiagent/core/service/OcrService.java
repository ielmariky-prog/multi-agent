package io.multiagent.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    @Value("${ai-core.ocr.tesseract-command:tesseract}")
    private String tesseractCmd;

    @Value("${ai-core.ocr.pdftoppm-command:pdftoppm}")
    private String pdftoppmCmd;

    @Value("${ai-core.ocr.lang:eng+fra}")
    private String lang;

    @Value("${ai-core.ocr.dpi:300}")
    private int dpi;

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    public String extractText(Path file) {
        String lower = file.getFileName().toString().toLowerCase();
        Path imagePath = file;
        List<Path> temp = new ArrayList<>();

        try {
            if (lower.endsWith(".pdf")) {
                Path base = Files.createTempFile("receipt-ocr-", "");
                Files.deleteIfExists(base); // pdftoppm ajoute l'extension
                String baseNoExt = base.toString();
                runCommand(List.of(
                        pdftoppmCmd,
                        "-singlefile",
                        "-png",
                        file.toString(),
                        baseNoExt
                ));
                imagePath = Path.of(baseNoExt + ".png");
                temp.add(imagePath);
            }

            Path outBase = Files.createTempFile("receipt-ocr-out-", "");
            Files.deleteIfExists(outBase);
            Path txtPath = Path.of(outBase.toString() + ".txt");
            temp.add(outBase);
            temp.add(txtPath);
            runCommand(List.of(
                    tesseractCmd,
                    imagePath.toString(),
                    outBase.toString(),
                    "-l", lang,
                    "--dpi", String.valueOf(dpi),
                    "--psm", "3"
            ));

            if (!Files.exists(txtPath)) {
                throw new IllegalStateException("Tesseract n'a pas généré de sortie");
            }

            return Files.readString(txtPath);

        } catch (Exception e) {
            throw new IllegalStateException("Échec OCR: " + e.getMessage(), e);
        } finally {
            temp.forEach(this::safeDelete);
        }
    }

    private void runCommand(List<String> command) throws Exception {
        log.debug("OCR exec: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();
        boolean finished = p.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IllegalStateException("Commande OCR timeout: " + String.join(" ", command));
        }
        if (p.exitValue() != 0) {
            String err = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))
                    .lines()
                    .reduce("", (a, b) -> a + b + "\n");
            throw new IllegalStateException("Commande OCR échec (" + p.exitValue() + "): " + err);
        }
    }

    private void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
