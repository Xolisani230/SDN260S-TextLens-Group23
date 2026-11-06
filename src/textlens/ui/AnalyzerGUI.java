package textlens.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import textlens.controller.AnalyzerController;
import textlens.model.AnalysisResult;
import textlens.output.ActivityLogger;

/**
 * The Swing window for TextLens. It shows the same results as the console
 * menu, as cards, a bar chart and a table, and talks only to the
 * AnalyzerController, so no analysis logic is repeated here. Anything that
 * can take a while runs on a background thread (SwingWorker) so the window
 * never freezes.
 */
public class AnalyzerGUI {

    // ---- The look: change these values to restyle the whole window ----
    private static final Color BACKGROUND = new Color(0xF4F6FA);
    private static final Color CARD = Color.WHITE;
    private static final Color PANEL = new Color(0xF9FAFB);
    private static final Color ACCENT = new Color(0x4F46E5);
    private static final Color ACCENT_END = new Color(0x7C3AED);
    private static final Color ACCENT_HOVER = new Color(0x4338CA);
    private static final Color TEXT = new Color(0x1F2937);
    private static final Color MUTED = new Color(0x6B7280);
    private static final Color BORDER = new Color(0xE5E7EB);
    private static final Color GOOD = new Color(0x16A34A);
    private static final Color WARNING = new Color(0xD97706);
    private static final Color BAD = new Color(0xDC2626);
    private static final String FONT_FAMILY = pickFont();

    /** The results to show, together with a suggestion for each misspelled word. */
    private static class Display {
        final List<AnalysisResult> results;
        final Map<String, String> suggestions;

        Display(List<AnalysisResult> results, Map<String, String> suggestions) {
            this.results = results;
            this.suggestions = suggestions;
        }
    }

    private final AnalyzerController controller;
    private final ActivityLogger logger;
    private final List<JButton> buttons = new ArrayList<>();

    private JFrame frame;
    private HintTextArea inputArea;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private CardLayout resultCards;
    private JPanel resultCardPanel;
    private JLabel emptyTitle;
    private JLabel emptyHint;
    private JLabel sourceLabel;
    private JLabel totalsLabel;
    private JComboBox<String> resultSelector;
    private StatCard wordsCard;
    private StatCard mistakesCard;
    private StatCard sentimentCard;
    private BarChartPanel chart;
    private DefaultTableModel mistakesModel;
    private Display current;
    private boolean updatingSelector;

    public AnalyzerGUI(AnalyzerController controller, ActivityLogger logger) {
        this.controller = controller;
        this.logger = logger;
    }

    /** Starts the window. Swing windows must be created on the event thread. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(AnalyzerGUI::start);
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // The default look is fine if the system look is not available.
        }
        ActivityLogger logger = new ActivityLogger();
        logger.info("TextLens GUI started");
        try {
            AnalyzerController controller = new AnalyzerController(logger);
            new AnalyzerGUI(controller, logger).buildUI();
        } catch (IOException e) {
            logger.error("Could not load word lists", e);
            JOptionPane.showMessageDialog(null,
                    "Could not load the word lists: " + e.getMessage()
                            + "\nCheck that data/dictionary.txt, data/positive.txt and "
                            + "data/negative.txt exist.",
                    "TextLens", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------
    // Building the window
    // ---------------------------------------------------------------

    /** Builds and shows the window. */
    public void buildUI() {
        frame = new JFrame("TextLens - Text Analyzer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logger.info("TextLens GUI closed");
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

         frame.setContentPane(root);
        // Never make the window bigger than the usable screen (taskbar excluded).
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(1120, screen.width - 40);
        int height = Math.min(780, screen.height - 40);
        frame.setMinimumSize(new Dimension(Math.min(940, width), Math.min(680, height)));
        frame.setPreferredSize(new Dimension(width, height));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, ACCENT, getWidth(), 0, ACCENT_END));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setBorder(pad(16, 24, 16, 24));

        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(label("TextLens", font(Font.BOLD, 26), Color.WHITE));
        texts.add(Box.createVerticalStrut(2));
        texts.add(label("Check spelling, find the most used words and read the tone of any text.",
                font(Font.PLAIN, 13), new Color(0xE0E7FF)));
        header.add(texts, BorderLayout.WEST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(16, 16));
        body.setOpaque(false);
        body.setBorder(pad(16, 16, 8, 16));
        body.add(buildSidebar(), BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout(0, 16));
        main.setOpaque(false);
        main.add(buildInputCard(), BorderLayout.NORTH);
        main.add(buildResultsCard(), BorderLayout.CENTER);
        body.add(main, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildSidebar() {
        RoundedPanel side = new RoundedPanel(null, 16);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(pad(18, 14, 18, 14));
        side.setPreferredSize(new Dimension(210, 0));

        side.add(sectionLabel("ANALYZE"));
        side.add(sidebarButton("Open file", true, e -> browseFile()));
        side.add(Box.createVerticalStrut(8));
        side.add(sidebarButton("Open folder", true, e -> browseFolder()));
        side.add(Box.createVerticalStrut(24));

        side.add(sectionLabel("SAVE AND REOPEN"));
        side.add(sidebarButton("Save report", false, e -> saveReport()));
        side.add(Box.createVerticalStrut(8));
        side.add(sidebarButton("Save session", false, e -> saveSession()));
        side.add(Box.createVerticalStrut(8));
        side.add(sidebarButton("Open session", false, e -> openSession()));
        side.add(Box.createVerticalStrut(24));

        side.add(sectionLabel("PERFORMANCE"));
        side.add(sidebarButton("Speed test", false, e -> speedTest()));
        side.add(Box.createVerticalGlue());
        return side;
    }

    private JPanel buildInputCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10), 16);
        card.setBorder(pad(14, 16, 14, 16));
        card.add(label("Paste or type text", font(Font.BOLD, 15), TEXT), BorderLayout.NORTH);

        inputArea = new HintTextArea("Paste an essay, an email or a review here, then press Analyze text");
        inputArea.setRows(4);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(font(Font.PLAIN, 14));
        inputArea.setForeground(TEXT);
        inputArea.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(inputArea);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        card.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(makeButton("Clear", false, e -> inputArea.setText("")));
        actions.add(makeButton("Analyze text", true, e -> onAnalyze()));
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildResultsCard() {
        resultCards = new CardLayout();
        resultCardPanel = new RoundedPanel(resultCards, 16);
        resultCardPanel.add(buildEmptyPanel(), "empty");
        resultCardPanel.add(buildResultsPanel(), "results");
        resultCards.show(resultCardPanel, "empty");
        return resultCardPanel;
    }

    // What is shown before anything has been analyzed.
    private JPanel buildEmptyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        emptyTitle = label("No results yet", font(Font.BOLD, 20), TEXT);
        emptyHint = label("Open a file or folder, or paste some text and press Analyze text.",
                font(Font.PLAIN, 14), MUTED);
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(emptyTitle);
        box.add(Box.createVerticalStrut(6));
        box.add(emptyHint);
        panel.add(box);
        return panel;
    }

    // What is shown after an analysis: cards, a bar chart and a table.
    private JPanel buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.setBorder(pad(14, 16, 14, 16));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        sourceLabel = label("", font(Font.BOLD, 14), TEXT);
        resultSelector = new JComboBox<>();
        resultSelector.setFont(font(Font.PLAIN, 13));
        resultSelector.addActionListener(e -> {
            if (!updatingSelector && current != null && resultSelector.getSelectedIndex() >= 0) {
                showResult(resultSelector.getSelectedIndex());
            }
        });
        top.add(sourceLabel, BorderLayout.CENTER);
        top.add(resultSelector, BorderLayout.EAST);

        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        stats.setOpaque(false);
        wordsCard = new StatCard("WORDS");
        mistakesCard = new StatCard("SPELLING MISTAKES");
        sentimentCard = new StatCard("OVERALL TONE");
        stats.add(wordsCard);
        stats.add(mistakesCard);
        stats.add(sentimentCard);

        chart = new BarChartPanel();
        JScrollPane chartScroll = new JScrollPane(chart);
        chartScroll.setBorder(BorderFactory.createEmptyBorder());
        chartScroll.setOpaque(false);
        chartScroll.getViewport().setOpaque(false);
        chartScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel details = new JPanel(new GridLayout(1, 2, 12, 0));
        details.setOpaque(false);
        details.add(titledCard("Most used words", chartScroll));
        details.add(titledCard("Spelling suggestions", buildMistakesTable()));

        JPanel centre = new JPanel(new BorderLayout(0, 12));
        centre.setOpaque(false);
        centre.add(stats, BorderLayout.NORTH);
        centre.add(details, BorderLayout.CENTER);

        totalsLabel = label("", font(Font.PLAIN, 13), MUTED);
        panel.add(top, BorderLayout.NORTH);
        panel.add(centre, BorderLayout.CENTER);
        panel.add(totalsLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JScrollPane buildMistakesTable() {
        mistakesModel = new DefaultTableModel(new String[] {"Word", "Suggestion"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(mistakesModel);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(font(Font.PLAIN, 13));
        table.setForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(font(Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean selected, boolean focused, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        t, value, selected, false, row, column);
                setBorder(pad(0, 8, 0, 8));
                return c;
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        bar.setBorder(pad(4, 20, 10, 20));
        statusLabel = label("Ready. Open a file or folder, or paste some text.",
                font(Font.PLAIN, 12), MUTED);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(140, 12));
        progressBar.setVisible(false);
        bar.add(statusLabel, BorderLayout.CENTER);
        bar.add(progressBar, BorderLayout.EAST);
        return bar;
    }

    // ---------------------------------------------------------------
    // Button actions
    // ---------------------------------------------------------------

    /** Analyzes the text in the input box. */
    public void onAnalyze() {
        String text = inputArea.getText();
        if (text.trim().isEmpty()) {
            showInfo("Type or paste some text in the box first.");
            return;
        }
        runInBackground("Analyzing pasted text...",
                () -> prepare(List.of(controller.analyzeText(text))), this::showResults);
    }

    /** Lets the user pick a text file and analyzes it. */
    private void browseFile() {
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setDialogTitle("Choose a text or Word file");
        chooser.setFileFilter(new FileNameExtensionFilter("Text and Word files (*.txt, *.docx)", "txt", "docx"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getPath();
            runInBackground("Analyzing " + path + "...",
                    () -> prepare(List.of(controller.analyzeFile(path))), this::showResults);
        }
    }

    /** Lets the user pick a folder and analyzes every .txt file in it. */
    private void browseFolder() {
        String path = chooseFolder("Choose a folder of text files");
        if (path != null) {
            runInBackground("Analyzing folder " + path + "...",
                    () -> prepare(controller.analyzeFolder(path)), this::showResults);
        }
    }

    private void saveReport() {
        String name = JOptionPane.showInputDialog(frame, "Report name:", "report");
        if (name == null) {
            return;
        }
        try {
            String base = controller.saveReport(name);
            setStatus("Report saved: " + base + ".txt and " + base + ".csv");
            showInfo("Report saved:\n" + base + ".txt\n" + base + ".csv");
        } catch (IOException e) {
            reportError(e);
        }
    }

    private void saveSession() {
        String name = JOptionPane.showInputDialog(frame, "Name for this analysis:", "session");
        if (name == null) {
            return;
        }
        try {
            String path = controller.saveSession(name);
            setStatus("Saved: " + path);
            showInfo("Saved:\n" + path);
        } catch (IOException e) {
            reportError(e);
        }
    }

    private void openSession() {
        String name = JOptionPane.showInputDialog(frame, "Name of the saved analysis:", "session");
        if (name == null) {
            return;
        }
        runInBackground("Opening saved analysis...",
                () -> prepare(controller.loadSession(name)), this::showResults);
    }

    private void speedTest() {
        String path = chooseFolder("Choose a folder to time");
        if (path == null) {
            return;
        }
        runInBackground("Timing... this may take a few seconds.",
                () -> controller.compareSpeed(path), speed -> {
                    String verdict = speed.getSpeedUp() >= 1.0
                            ? String.format("Several threads were %.1f times faster.", speed.getSpeedUp())
                            : "One thread was faster here: the files are too small to repay "
                                    + "the cost of starting threads.";
                    String message = "Files analyzed: " + speed.getFiles()
                            + "\nThreads used: " + speed.getThreads()
                            + "\nOne thread: " + speed.getSequentialMs() + " ms"
                            + "\nSeveral threads: " + speed.getParallelMs() + " ms"
                            + "\n" + verdict;
                    setStatus("Speed test finished.");
                    showInfo(message);
                });
    }

    // ---------------------------------------------------------------
    // Showing results
    // ---------------------------------------------------------------

    // Runs on the background thread: looks up a suggested correction for every
    // misspelled word, so the window thread never has to wait for it.
    private Display prepare(List<AnalysisResult> results) {
        Map<String, String> suggestions = new HashMap<>();
        for (AnalysisResult result : results) {
            for (String word : result.getMisspelled()) {
                suggestions.computeIfAbsent(word, controller::suggest);
            }
        }
        return new Display(results, suggestions);
    }

    /** Shows a set of results: a picker for several files, then the first one. */
    private void showResults(Display display) {
        current = display;
        if (display.results.isEmpty()) {
            emptyTitle.setText("No .txt files were analyzed");
            emptyHint.setText("Choose a folder that contains .txt files.");
            resultCards.show(resultCardPanel, "empty");
            setStatus("Nothing was analyzed.");
            return;
        }
        boolean several = display.results.size() > 1;
        updatingSelector = true;
        resultSelector.removeAllItems();
        for (AnalysisResult result : display.results) {
            resultSelector.addItem(shorten(result.getSource(), 60));
        }
        resultSelector.setSelectedIndex(0);
        updatingSelector = false;
        resultSelector.setVisible(several);
        totalsLabel.setText(several ? totals(display.results) : "");
        totalsLabel.setVisible(several);

        showResult(0);
        resultCards.show(resultCardPanel, "results");
        setStatus("Showing " + display.results.size() + " result(s).");
    }

    // Fills the cards, chart and table for one result.
    private void showResult(int index) {
        AnalysisResult result = current.results.get(index);
        sourceLabel.setText("Source: " + shorten(result.getSource(), 70));
        wordsCard.setValue(String.valueOf(result.getWordCount()), TEXT);

        int mistakes = result.getMisspelled().size();
        mistakesCard.setValue(String.valueOf(mistakes), mistakes == 0 ? GOOD : WARNING);

        String tone = result.getSentiment().name();
        Color toneColor = result.getSentiment().name().equals("POSITIVE") ? GOOD
                : result.getSentiment().name().equals("NEGATIVE") ? BAD : MUTED;
        sentimentCard.setValue(tone.charAt(0) + tone.substring(1).toLowerCase(), toneColor);

        chart.setData(result.getTopWords());

        mistakesModel.setRowCount(0);
        if (mistakes == 0) {
            mistakesModel.addRow(new Object[] {"No spelling mistakes found", ""});
        }
        for (String word : result.getMisspelled()) {
            String suggestion = current.suggestions.getOrDefault(word, "");
            mistakesModel.addRow(new Object[] {word, suggestion.isEmpty() ? "(no suggestion)" : suggestion});
        }
    }

    // A one-line summary across all the analyzed files.
    private String totals(List<AnalysisResult> results) {
        int words = 0;
        int mistakes = 0;
        int positive = 0;
        int neutral = 0;
        int negative = 0;
        for (AnalysisResult r : results) {
            words += r.getWordCount();
            mistakes += r.getMisspelled().size();
            switch (r.getSentiment()) {
                case POSITIVE:
                    positive++;
                    break;
                case NEGATIVE:
                    negative++;
                    break;
                default:
                    neutral++;
            }
        }
        return results.size() + " files  |  " + words + " words  |  " + mistakes
                + " possible spelling mistakes  |  " + positive + " positive, "
                + neutral + " neutral, " + negative + " negative";
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Runs slow work on a background thread, then shows its result on the
     * window thread. While it runs the buttons are switched off and a
     * progress bar is shown.
     */
    private <T> void runInBackground(String status, Callable<T> work, Consumer<T> onSuccess) {
        setStatus(status);
        setBusy(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    reportError(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setStatus("Cancelled.");
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private String chooseFolder(String title) {
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getPath();
        }
        return null;
    }

    private void setBusy(boolean busy) {
        for (JButton button : buttons) {
            button.setEnabled(!busy);
        }
        progressBar.setVisible(busy);
        frame.setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(frame, message, "TextLens", JOptionPane.INFORMATION_MESSAGE);
    }

    private void reportError(Throwable error) {
        String message = (error == null || error.getMessage() == null)
                ? "Something went wrong." : error.getMessage();
        if (error instanceof Exception) {
            logger.error("GUI action failed", (Exception) error);
        }
        setStatus("Error: " + message);
        JOptionPane.showMessageDialog(frame, message, "TextLens", JOptionPane.ERROR_MESSAGE);
    }

    private JButton makeButton(String text, boolean primary, ActionListener action) {
        FlatButton button = new FlatButton(text, primary);
        button.addActionListener(action);
        buttons.add(button);
        return button;
    }

    // A full-width button for the sidebar.
    private JButton sidebarButton(String text, boolean primary, ActionListener action) {
        JButton button = makeButton(text, primary, action);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        return button;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = label(text, font(Font.BOLD, 11), MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(pad(0, 2, 8, 0));
        return label;
    }

    // A grey panel with a title, used for the chart and the table.
    private static JPanel titledCard(String title, Component content) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8), 12);
        card.setBackground(PANEL);
        card.setBorder(pad(12, 12, 12, 12));
        card.add(label(title, font(Font.BOLD, 14), TEXT), BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : "..." + text.substring(text.length() - (max - 3));
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    // Uses Segoe UI (the Windows font) when it is installed.
    private static String pickFont() {
        List<String> installed = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        return installed.contains("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
    }

    private static Font font(int style, int size) {
        return new Font(FONT_FAMILY, style, size);
    }

    // Smooth edges and text for everything we draw ourselves.
    private static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof Map) {
            g2.addRenderingHints((Map<?, ?>) hints);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    // ---------------------------------------------------------------
    // Small custom components
    // ---------------------------------------------------------------

    /** A panel with rounded corners and a thin border. */
    private static class RoundedPanel extends JPanel {
        private final int arc;

        RoundedPanel(LayoutManager layout, int arc) {
            super(layout);
            this.arc = arc;
            setOpaque(false);
            setBackground(CARD);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A flat, rounded button that changes colour when the mouse is over it. */
    private static class FlatButton extends JButton {
        private final boolean primary;
        private boolean hover;

        FlatButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(font(Font.BOLD, 13));
            setBorder(pad(10, 18, 10, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            boolean on = isEnabled();
            Color fill;
            Color textColor;
            if (primary) {
                fill = !on ? new Color(0xC7C9F5) : hover ? ACCENT_HOVER : ACCENT;
                textColor = Color.WHITE;
            } else {
                fill = !on ? new Color(0xF3F4F6) : hover ? new Color(0xEEF2FF) : Color.WHITE;
                textColor = !on ? new Color(0x9CA3AF) : ACCENT;
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            if (!primary) {
                g2.setColor(on ? new Color(0xC7D2FE) : BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(textColor);
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    /** A text box that shows a grey hint while it is empty. */
    private static class HintTextArea extends JTextArea {
        private final String hint;

        HintTextArea(String hint) {
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                smooth(g2);
                g2.setColor(new Color(0x9CA3AF));
                g2.setFont(getFont());
                Insets in = getInsets();
                g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent());
                g2.dispose();
            }
        }
    }

    /** A card showing one number (or word) with a small title above it. */
    private static class StatCard extends RoundedPanel {
        private final JLabel value = label("-", font(Font.BOLD, 28), TEXT);

        StatCard(String title) {
            super(new BorderLayout(0, 4), 12);
            setBackground(PANEL);
            setBorder(pad(12, 14, 12, 14));
            add(label(title, font(Font.BOLD, 11), MUTED), BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
        }

        void setValue(String text, Color color) {
            value.setText(text);
            value.setForeground(color);
        }
    }

    /** Draws the most used words as horizontal bars. */
    private static class BarChartPanel extends JPanel implements Scrollable {
        private static final int ROW_HEIGHT = 30;

        private List<Map.Entry<String, Integer>> data = new ArrayList<>();

        BarChartPanel() {
            setOpaque(false);
        }

        void setData(List<Map.Entry<String, Integer>> newData) {
            data = new ArrayList<>(newData);
            revalidate();                                    // the list may be longer or shorter now
            repaint();
            if (getParent() instanceof JViewport) {          // start again from the top
                ((JViewport) getParent()).setViewPosition(new Point(0, 0));
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, Math.max(60, data.size() * ROW_HEIGHT));
        }

        // ---- Scrollable: tells the scroll pane how this panel should scroll ----

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(200, 5 * ROW_HEIGHT);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(ROW_HEIGHT, visible.height - ROW_HEIGHT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;        // the bars always use the full width
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // Fill the box when there are few words; scroll when there are many.
            return getParent() instanceof JViewport
                    && getParent().getHeight() > getPreferredSize().height;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setFont(font(Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();

            if (data.isEmpty()) {
                g2.setColor(MUTED);
                String message = "No words to show";
                g2.drawString(message, (getWidth() - fm.stringWidth(message)) / 2, getHeight() / 2);
                g2.dispose();
                return;
            }

            int max = 1;
            int labelWidth = 0;
            for (Map.Entry<String, Integer> e : data) {
                max = Math.max(max, e.getValue());
                labelWidth = Math.max(labelWidth, fm.stringWidth(e.getKey()));
            }
            labelWidth = Math.min(labelWidth, 120);
            int countWidth = fm.stringWidth(String.valueOf(max)) + 8;
            int barLeft = labelWidth + 14;
            int barMax = Math.max(10, getWidth() - barLeft - countWidth - 6);

            int y = 0;
            for (Map.Entry<String, Integer> e : data) {
                int baseline = y + (ROW_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(TEXT);
                g2.drawString(clip(e.getKey(), fm, labelWidth), 0, baseline);

                int width = Math.max(6, (int) (barMax * (double) e.getValue() / max));
                g2.setColor(ACCENT);
                g2.fillRoundRect(barLeft, y + (ROW_HEIGHT - 14) / 2, width, 14, 8, 8);

                g2.setColor(MUTED);
                g2.drawString(String.valueOf(e.getValue()), barLeft + width + 6, baseline);
                y += ROW_HEIGHT;
            }
            g2.dispose();
        }

        // Cuts a word with "..." if it is wider than the space for it.
        private String clip(String word, FontMetrics fm, int width) {
            if (fm.stringWidth(word) <= width) {
                return word;
            }
            String cut = word;
            while (cut.length() > 1 && fm.stringWidth(cut + "...") > width) {
                cut = cut.substring(0, cut.length() - 1);
            }
            return cut + "...";
        }
    }
}