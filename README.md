## TextLens - Text Analysis Tool

SDN260S Software Design II
Group 23 – Topic 10
Cape Peninsula University of Technology (CPUT)

## 1.Overview

TextLens is a Java-based text analysis application that helps users analyse text quickly and efficiently. The system can analyse text from files, folders, or direct user input and provides information about word usage, spelling errors, and document tone.

The application supports both a Graphical User Interface (GUI) and a Command-Line Interface (CLI).

## 2.Features

## 2.1Core Features

Analyse text files (.txt).

Analyse Microsoft Word files (.docx).

Analyse entire folders recursively.

Analyse pasted text.

Count total words.

Display the most frequently used words.

Detect spelling mistakes.

Suggest spelling corrections.

Determine document tone (Positive, Neutral, Negative).

Maintain an activity log.

## 2.2 Advanced Features

Multithreaded folder analysis.

Speed comparison between single-threaded and multithreaded processing.

Save and load sessions using serialization.

Export reports to TXT and CSV files.

Word document support using Apache POI.

## 3.System Requirements

Java 17 or newer.

Visual Studio Code or another Java IDE.

Apache POI libraries.

## 4.Running the Application

## 4.1 GUI Version

Open the project in Visual Studio Code.

Navigate to:

src/textlens/ui/AnalyzerGUI.java

Click Run to launch the application.

## 4.2 Console Version (CLI)

Open the project in Visual Studio Code.

Navigate to:

src/textlens/MainCLI.java

Click Run to launch the application in the console.

## 5.User Guide

## 5.1Analyse a File

Click Open File.

Select a supported file (.txt or .docx).

Click Analyse.

View the results.

## 5.2Analyse a Folder

Click Open Folder.

Select a folder.

TextLens will recursively analyse all supported files within the folder and its subfolders.

View the analysis results.

## 5.3Analyse Pasted Text

Type or paste text into the text area.

Click Analyse Text.

View the analysis results.

## 6. Understanding the Results

## 6.1 Word Count

Displays the total number of words found in the document.

## 6.2 Most Used Words

Shows the most frequently occurring words ranked from highest to lowest frequency.

## 6.3 Spelling Mistakes

Displays words that are not found in the dictionary together with suggested corrections.

## 6.4Tone Analysis

Classifies the overall tone of the analysed text into three categories:

Positive

Neutral

Negative

## 7.Saving Results

## 7.1 Save Report

Click Save Report.

Reports are saved in the reports/ folder.

Available formats:

TXT

CSV

## 7.2 Save Session

Click Save Session.

Sessions are stored in the saved/ folder.

## 7.3 Open Session

Click Open Session.

Select a previously saved session file.

The application restores the saved analysis results.

## 8. Project Folders

The project uses the following folders:

Folder

Purpose

data/

Dictionaries and sample files

reports/

Saved reports

saved/

Saved sessions

logs/

Activity logs

src/

Source code

## 9. Logging

All application activity is recorded in:

logs/activity.log

The log includes:

Program startup.

File loading.

Folder loading.

Analysis operations.

Report generation.

Session saving.

Errors and exceptions.

## 10. Supported File Types

File Type

TXT (.txt) - Yes

DOCX (.docx) - Yes

PDF (.pdf) - No

DOC (.doc) - No


Note: PDF and older Microsoft Word (.doc) files must be converted to .txt or .docx before analysis.

## 11. Algorithms and Data Structures

## 11.1 Word Counting

Uses a HashMap to count word occurrences efficiently.

Time Complexity: O(n)

## 11.2 Dictionary Lookup

Uses a HashSet for fast word validation.

Average Time Complexity: O(1)

## 11.3 Word Ranking

Uses a recursive Merge Sort algorithm to rank words by frequency.

Time Complexity: O(m log m)

## 11.4 Spelling Suggestions

Uses Edit Distance  to find the closest matching words based on the number of character insertions, deletions, and substitutions required.

## 12. Software Design

TextLens follows Object-Oriented Design principles.

GUI and CLI share a single controller.

All analysers implement the generic Analyzer<T> interface.

New analysis modules can be added without modifying the controller.

The modular architecture improves maintainability and scalability.

## 13. Known Limitations

The dictionary contains only commonly used words.

Tone analysis does not recognise negation.

For example, "Not good" may still be classified as positive.

PDF files are not currently supported.

Tone analysis is keyword-based and not context-aware.