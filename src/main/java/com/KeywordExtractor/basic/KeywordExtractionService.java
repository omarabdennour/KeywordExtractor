package com.KeywordExtractor.basic;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class KeywordExtractionService {
    public ExtractionResult extract(ExtractionRequest request) {
        List<Match> matches = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int scannedLines = 0;

        String keyword = request.caseSensitive()
                ? request.keyword()
                : request.keyword().toLowerCase(Locale.ROOT);

        for (Path file : request.files()) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    scannedLines++;
                    String searchableLine = request.caseSensitive() ? line : line.toLowerCase(Locale.ROOT);
                    if (searchableLine.contains(keyword)) {
                        matches.add(formatMatch(file, lineNumber, line, request.includeSourceInfo()));
                    }
                }
            } catch (IOException ex) {
                errors.add(file.getFileName() + ": " + ex.getMessage());
            }
        }

        return new ExtractionResult(request.files().size(), scannedLines, matches.size(), matches, errors);
    }

    private Match formatMatch(Path file, int lineNumber, String line, boolean includeSourceInfo) {
        if (!includeSourceInfo) {
            return new Match(line, line);
        }
        return new Match(file.getFileName() + ":" + lineNumber + ": " + line, line);
    }

    public record ExtractionRequest(List<Path> files, String keyword, boolean caseSensitive,
                                    boolean includeSourceInfo) {
    }

    public record Match(String displayText, String lineContent) {
    }

    public record ExtractionResult(int fileCount, int scannedLineCount, int matchCount, List<Match> matches,
                                   List<String> errors) {
        public String output() {
            StringBuilder builder = new StringBuilder();
            if (!matches.isEmpty()) {
                builder.append(String.join(System.lineSeparator(), matches.stream()
                        .map(Match::displayText)
                        .toList()));
            }
            if (!errors.isEmpty()) {
                if (!builder.isEmpty()) {
                    builder.append(System.lineSeparator()).append(System.lineSeparator());
                }
                builder.append("Read errors:").append(System.lineSeparator());
                builder.append(String.join(System.lineSeparator(), errors));
            }
            return builder.toString();
        }

        public String copyOutput() {
            if (matches.isEmpty()) {
                return "";
            }
            return String.join(System.lineSeparator(), matches.stream()
                    .map(Match::lineContent)
                    .toList());
        }

        public String summary() {
            String suffix = errors.isEmpty() ? "" : " with " + errors.size() + " read error(s)";
            return matchCount + " matching line(s) across " + fileCount + " file(s), " + scannedLineCount
                    + " line(s) scanned" + suffix + ".";
        }
    }
}
