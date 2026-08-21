package com.learnflow.service;

import com.learnflow.config.ResourceIngestionProperties;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceContentProcessorTest {
    private final ResourceContentProcessor processor = new ResourceContentProcessor(new ResourceIngestionProperties());

    @Test
    void plainTextProducesDeterministicVersionedChunksWithoutPersistingPrompts() throws Exception {
        byte[] source = ("Java 集合框架包括 List、Set 和 Map。\n\n" + "练习应覆盖 ArrayList 与 HashMap。".repeat(120))
                .getBytes(StandardCharsets.UTF_8);

        ResourceContentProcessor.ProcessedContent first = processor.process(source, "text/plain", "lesson.txt");
        ResourceContentProcessor.ProcessedContent second = processor.process(source, "text/plain", "lesson.txt");

        assertThat(first.contentSha256()).isEqualTo(second.contentSha256());
        assertThat(first.chunks()).extracting(ResourceContentProcessor.Chunk::contentHash)
                .containsExactlyElementsOf(second.chunks().stream().map(ResourceContentProcessor.Chunk::contentHash).toList());
        assertThat(first.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.charEnd()).isGreaterThan(chunk.charStart());
        });
        assertThat(ResourceContentProcessor.PARSER_VERSION).isNotBlank();
        assertThat(ResourceContentProcessor.CHUNKER_VERSION).isNotBlank();
    }

    @Test
    void rejectsUnsupportedBinaryAndNonPublicAddresses() throws Exception {
        assertThatThrownBy(() -> processor.process(new byte[]{0, 1, 2, 3}, "application/octet-stream", "payload.exe"))
                .isInstanceOf(ResourceIngestionException.class)
                .extracting(error -> ((ResourceIngestionException) error).getCode())
                .isEqualTo("UNSUPPORTED_CONTENT_TYPE");
        assertThat(ResourceContentProcessor.isPublicAddress(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(ResourceContentProcessor.isPublicAddress(InetAddress.getByName("10.1.2.3"))).isFalse();
        assertThat(ResourceContentProcessor.isPublicAddress(InetAddress.getByName("192.0.2.1"))).isFalse();
    }

    @Test
    void rejectsCredentialsAndNonStandardPortsBeforeNetworkAccess() {
        assertThatThrownBy(() -> ResourceContentProcessor.validatePublicUri("http://user:secret@example.com/a"))
                .isInstanceOf(ResourceIngestionException.class);
        assertThatThrownBy(() -> ResourceContentProcessor.validatePublicUri("https://example.com:8443/a"))
                .isInstanceOf(ResourceIngestionException.class);
    }

    @Test
    void extractsWordDocumentsThroughTheAllowlistedParser() throws Exception {
        byte[] documentBytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("LearnFlow 文档摄取测试：Java Stream API 学习材料");
            document.write(output);
            documentBytes = output.toByteArray();
        }

        ResourceContentProcessor.ProcessedContent content = processor.process(
                documentBytes,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "lesson.docx"
        );

        assertThat(content.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(content.chunks()).extracting(ResourceContentProcessor.Chunk::content)
                .anySatisfy(chunk -> assertThat(chunk).contains("Java Stream API"));
    }

    @Test
    void extractsPdfDocumentsThroughTheAllowlistedParser() throws Exception {
        byte[] documentBytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText("LearnFlow PDF ingestion test for Java Stream API");
                content.endText();
            }
            document.save(output);
            documentBytes = output.toByteArray();
        }

        ResourceContentProcessor.ProcessedContent content = processor.process(documentBytes, "application/pdf", "lesson.pdf");
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.chunks()).extracting(ResourceContentProcessor.Chunk::content)
                .anySatisfy(chunk -> assertThat(chunk).contains("Java Stream API"));
    }
}
