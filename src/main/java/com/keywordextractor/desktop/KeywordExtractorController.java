package com.keywordextractor.desktop;

import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class KeywordExtractorController {
    private final KeywordExtractionService extractionService;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final ObservableList<String> selectedFileNames = FXCollections.observableArrayList();
    private final List<Path> selectedFiles = new ArrayList<>();

    // Identifies the newest scan so late background results cannot overwrite newer UI state.
    private final AtomicLong scanVersion = new AtomicLong();
    private String copyResultsText = "";

    @FXML
    private TextField keywordField;
    @FXML
    private Button startLookupButton;
    @FXML
    private Button selectFilesButton;
    @FXML
    private Button clearFilesButton;
    @FXML
    private Button copyResultsButton;
    @FXML
    private CheckBox caseSensitiveCheckBox;
    @FXML
    private CheckBox sourceInfoCheckBox;
    @FXML
    private ListView<String> fileListView;
    @FXML
    private TextArea resultArea;
    @FXML
    private Label selectedCountLabel;
    @FXML
    private Label statusBadge;
    @FXML
    private Label summaryLabel;
    @FXML
    private ProgressIndicator progressIndicator;

    public KeywordExtractorController(KeywordExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @FXML
    void initialize() {
        fileListView.setItems(selectedFileNames);
        sourceInfoCheckBox.setSelected(true);
        resultArea.setText("");
        resultArea.setPromptText("Matching lines appear here after you press Start lookup.");
        copyResultsButton.setDisable(true);
        clearFilesButton.setDisable(true);
        progressIndicator.setVisible(false);
        renderIdle("Ready", "Select one or more .txt or .csv files, then type a keyword.");

        keywordField.textProperty().addListener((observable, oldValue, newValue) -> updateLookupState());
        caseSensitiveCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateLookupState());
        sourceInfoCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateLookupState());
        resultArea.textProperty().addListener((observable, oldValue, newValue) -> updateCopyResultsState());
        updateLookupState();
    }

    @FXML
    protected void onSelectFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select text or CSV files");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text and CSV files", "*.txt", "*.csv"),
                new FileChooser.ExtensionFilter("Text files", "*.txt"),
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );

        List<File> files = chooser.showOpenMultipleDialog(currentWindow());
        if (files == null || files.isEmpty()) {
            return;
        }

        for (File file : files) {
            Path path = file.toPath();
            if (!selectedFiles.contains(path)) {
                selectedFiles.add(path);
                selectedFileNames.add(path.getFileName().toString());
            }
        }
        updateFileState();
        updateLookupState();
    }

    @FXML
    protected void onStartLookup() {
        runExtraction();
    }

    @FXML
    protected void onClearFiles() {
        selectedFiles.clear();
        selectedFileNames.clear();
        copyResultsText = "";
        resultArea.clear();
        updateCopyResultsState();
        updateFileState();
        updateLookupState();
        renderIdle("Ready", "Select one or more .txt or .csv files, then type a keyword.");
    }

    @FXML
    protected void onCopyResults() {
        String clipboardText = copyResultsText == null ? "" : copyResultsText;
        if (clipboardText.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(clipboardText);
        Clipboard.getSystemClipboard().setContent(content);
        renderIdle("Copied", "Line content copied to the clipboard.");
    }

    @PreDestroy
    void close() {
        executorService.shutdownNow();
    }

    private void runExtraction() {
        String keyword = text(keywordField.getText());
        if (selectedFiles.isEmpty()) {
            copyResultsText = "";
            resultArea.clear();
            updateCopyResultsState();
            updateFileState();
            renderIdle("No files", "Choose one or more .txt or .csv files.");
            return;
        }
        if (keyword.isBlank()) {
            copyResultsText = "";
            resultArea.clear();
            updateCopyResultsState();
            renderIdle("No keyword", "Type a keyword to scan the selected files.");
            return;
        }

        long version = scanVersion.incrementAndGet();
        KeywordExtractionService.ExtractionRequest request = new KeywordExtractionService.ExtractionRequest(
                List.copyOf(selectedFiles),
                keyword,
                caseSensitiveCheckBox.isSelected(),
                sourceInfoCheckBox.isSelected()
        );

        beginScan();

        // File IO runs off the JavaFX thread; UI updates are marshalled back with Platform.runLater.
        CompletableFuture
                .supplyAsync(() -> extractionService.extract(request), executorService)
                .whenComplete((result, throwable) -> Platform.runLater(() -> {
                    if (version != scanVersion.get()) {
                        return;
                    }
                    endScan();
                    if (throwable != null) {
                        copyResultsText = "";
                        resultArea.setText("");
                        updateCopyResultsState();
                        renderError("Scan failed", throwable.getMessage());
                        return;
                    }
                    resultArea.setText(result.output());
                    copyResultsText = result.copyOutput();
                    updateCopyResultsState();
                    if (result.matchCount() == 0) {
                        renderIdle("No matches", result.summary());
                    } else {
                        renderSuccess("Matches found", result.summary());
                    }
                }));
    }

    private void beginScan() {
        setActionsDisabled(true);
        copyResultsText = "";
        updateCopyResultsState();
        progressIndicator.setVisible(true);
        statusBadge.setText("Scanning");
        statusBadge.getStyleClass().setAll("status-badge", "status-running");
        summaryLabel.setText("Scanning selected files...");
    }

    private void endScan() {
        setActionsDisabled(false);
        progressIndicator.setVisible(false);
    }

    private void setActionsDisabled(boolean disabled) {
        selectFilesButton.setDisable(disabled);
        startLookupButton.setDisable(disabled || !canLookup());
        clearFilesButton.setDisable(disabled || selectedFiles.isEmpty());
        keywordField.setDisable(disabled);
        caseSensitiveCheckBox.setDisable(disabled);
        sourceInfoCheckBox.setDisable(disabled);
    }

    private void updateFileState() {
        selectedCountLabel.setText(selectedFiles.size() + " file(s) selected");
        clearFilesButton.setDisable(selectedFiles.isEmpty());
    }

    private void updateCopyResultsState() {
        copyResultsButton.setDisable(copyResultsText == null || copyResultsText.isBlank());
    }

    private void updateLookupState() {
        startLookupButton.setDisable(!canLookup());
        if (canLookup()) {
            renderIdle("Ready", "Press Start lookup to scan the selected files.");
        }
    }

    private boolean canLookup() {
        return !selectedFiles.isEmpty() && !text(keywordField.getText()).isBlank();
    }

    private void renderIdle(String title, String message) {
        statusBadge.setText(title);
        statusBadge.getStyleClass().setAll("status-badge", "status-idle");
        summaryLabel.setText(message);
    }

    private void renderSuccess(String title, String message) {
        statusBadge.setText(title);
        statusBadge.getStyleClass().setAll("status-badge", "status-ok");
        summaryLabel.setText(message);
    }

    private void renderError(String title, String message) {
        statusBadge.setText(title);
        statusBadge.getStyleClass().setAll("status-badge", "status-error");
        summaryLabel.setText(message == null || message.isBlank() ? "Unexpected application error." : message);
    }

    private Window currentWindow() {
        return selectFilesButton.getScene() == null ? null : selectFilesButton.getScene().getWindow();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
