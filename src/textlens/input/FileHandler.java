package textlens.input;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import textlens.model.AnalysisResult;
import textlens.model.TextDocument;

/**
 * Reads text files and Word (.docx) documents into TextDocument objects, and
 * saves and loads analysis results using object serialization.
 *
 * Files that are not text (such as PDFs and pictures) are rejected with a
 * clear message instead of being analyzed as meaningless characters.
 */
public class FileHandler {

    // A .docx file is a zip archive; the text is inside this XML file.
    private static final String DOCX_TEXT_ENTRY = "word/document.xml";
    private static final long MAX_DOCX_TEXT_BYTES = 100L * 1024 * 1024;

    /**
     * Tells whether TextLens can read a file with this name. The folder
     * scanner uses this, so the supported types are listed in one place.
     */
    public static boolean isSupported(String fileName) {
        String name = fileName.toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".docx");
    }

    /**
     * Reads a text (.txt) or Word (.docx) file.
     *
     * @param path the file path; surrounding quotes and spaces are removed
     * @return the file's text as a TextDocument
     * @throws IOException with a clear message if the file cannot be read
     */
    public TextDocument readText(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            throw new IOException("No file path was given.");
        }
        String cleaned = path.trim().replace("\"", "");

        Path file;
        try {
            file = Paths.get(cleaned);
        } catch (InvalidPathException e) {
            throw new IOException("Invalid file path: " + cleaned);
        }
        if (!Files.exists(file)) {
            throw new IOException("File not found: " + cleaned);
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("That is a folder, not a file: " + cleaned);
        }

        String name = file.getFileName().toString().toLowerCase();
        String text;
        if (name.endsWith(".docx")) {
            text = readDocx(file);
        } else if (name.endsWith(".pdf")) {
            throw new IOException("PDF files are not supported yet. "
                    + "Save the document as .docx or .txt and open that instead.");
        } else if (name.endsWith(".doc")) {
            throw new IOException("Old Word (.doc) files are not supported. "
                    + "Save the document as .docx or .txt and open that instead.");
        } else {
            text = readPlainText(file);
        }
        return new TextDocument(text);
    }

    // Reads a plain text file, working out the encoding, and refuses files
    // that are clearly not text.
    private String readPlainText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);

        // Windows Notepad's "Unicode" format starts with a byte-order mark.
        if (bytes.length >= 2) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if ((first == 0xFF && second == 0xFE) || (first == 0xFE && second == 0xFF)) {
                return new String(bytes, StandardCharsets.UTF_16);
            }
        }
        if (looksBinary(bytes)) {
            throw new IOException("This file does not look like a text file, "
                    + "so it cannot be analyzed: " + file.getFileName());
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            // Some Windows files are not UTF-8, so try a simpler encoding.
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    // Looks at the start of the file: real text has no zero bytes and very
    // few control characters, while PDFs, pictures and programs have many.
    private boolean looksBinary(byte[] bytes) {
        int length = Math.min(bytes.length, 4096);
        int odd = 0;
        for (int i = 0; i < length; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0) {
                return true;
            }
            if (b < 32 && b != '\n' && b != '\r' && b != '\t' && b != '\f') {
                odd++;
            }
        }
        return length > 0 && odd * 10 > length;
    }

    // A .docx file is a zip archive. The words are in word/document.xml,
    // inside <w:t> elements, grouped into paragraphs (<w:p>). We read that
    // file with Java's built-in XML reader and keep only the words.
    private String readDocx(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry(DOCX_TEXT_ENTRY);
            if (entry == null) {
                throw new IOException("This is not a valid Word (.docx) document: "
                        + file.getFileName());
            }
            if (entry.getSize() > MAX_DOCX_TEXT_BYTES) {
                throw new IOException("This Word document is too large to analyze: "
                        + file.getFileName());
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return extractDocxText(in);
            }
        } catch (ZipException e) {
            throw new IOException("This is not a valid Word (.docx) document: "
                    + file.getFileName());
        } catch (XMLStreamException e) {
            throw new IOException("This Word document could not be read (it may be damaged): "
                    + file.getFileName(), e);
        }
    }

    private String extractDocxText(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        // Safety: never follow external references inside the document.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(in);

        StringBuilder text = new StringBuilder();
        boolean insideText = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = reader.getLocalName();
                if (tag.equals("t")) {
                    insideText = true;                 // a piece of text
                } else if (tag.equals("tab")) {
                    text.append(' ');
                } else if (tag.equals("br") || tag.equals("cr")) {
                    text.append('\n');
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String tag = reader.getLocalName();
                if (tag.equals("t")) {
                    insideText = false;
                } else if (tag.equals("p")) {
                    text.append('\n');                 // end of a paragraph
                }
            } else if (event == XMLStreamConstants.CHARACTERS && insideText) {
                text.append(reader.getText());
            }
        }
        reader.close();
        return text.toString();
    }

    /**
     * Saves results to a file as Java objects (serialization), so they can
     * be loaded again later without re-analyzing the text.
     */
    public void saveResults(List<AnalysisResult> results, String path) throws IOException {
        Path file = Paths.get(path);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeObject(new ArrayList<>(results));
        }
    }

    /**
     * Loads results that were saved with saveResults.
     *
     * @throws IOException if the file is missing or is not a TextLens save file
     */
    public List<AnalysisResult> loadResults(String path) throws IOException {
        Path file = Paths.get(path);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Saved analysis not found: " + path);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            // Safety: only allow our own result classes and basic Java classes.
            in.setObjectInputFilter(
                    ObjectInputFilter.Config.createFilter("java.base/*;textlens.model.*;!*"));
            Object data = in.readObject();
            if (!(data instanceof List)) {
                throw new IOException("This is not a TextLens saved analysis: " + path);
            }
            List<AnalysisResult> results = new ArrayList<>();
            for (Object item : (List<?>) data) {
                if (!(item instanceof AnalysisResult)) {
                    throw new IOException("This is not a TextLens saved analysis: " + path);
                }
                results.add((AnalysisResult) item);
            }
            return results;
        } catch (ClassNotFoundException | ObjectStreamException e) {
            throw new IOException("This is not a valid TextLens saved analysis: " + path, e);
        }
    }
}