import java.io.*;
import java.nio.file.*;
import java.util.regex.*;
import java.util.*;

public class LocatorRefactorer {
    
    // Define the custom types you want to replace
    private static final Set<String> CUSTOM_TYPES = Set.of(
        "Button", "Link", "Text", "Input", "Dropdown", "Checkbox", 
        "RadioButton", "Image", "Table", "Label", "Div", "Span"
        // Add more custom types as needed
    );
    
    public static void main(String[] args) {
        String folderPath = "src/main/java"; // Change this to your folder path
        
        try {
            replaceInAllFiles(folderPath);
            System.out.println("Replacement completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    public static void replaceInAllFiles(String folderPath) throws IOException {
        Path folder = Paths.get(folderPath);
        
        Files.walk(folder)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    replaceInFile(path);
                    System.out.println("Processed: " + path.getFileName());
                } catch (IOException e) {
                    System.err.println("Error processing file " + path + ": " + e.getMessage());
                }
            });
    }
    
    public static void replaceInFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String modifiedContent = replaceCustomTypes(content);
        
        if (!content.equals(modifiedContent)) {
            Files.writeString(filePath, modifiedContent);
        }
    }
    
    public static String replaceCustomTypes(String content) {
        // Create a regex pattern that matches the custom types
        String customTypesPattern = String.join("|", CUSTOM_TYPES);
        
        // Regex explanation:
        // (^|\s)                    - Start of line or whitespace (captured in group 1)
        // (public|private|protected)? - Optional access modifier (captured in group 2)
        // \s*                       - Optional whitespace
        // (CUSTOM_TYPES)            - One of the custom types (captured in group 3)
        // (\s+\w+\s*;)             - Variable name followed by semicolon (captured in group 4)
        
        String regex = "(^|\\s)(public|private|protected)?\\s*(" + customTypesPattern + ")(\\s+\\w+\\s*;)";
        
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String prefix = matcher.group(1); // Start of line or whitespace
            String accessModifier = matcher.group(2); // Access modifier (can be null)
            String variableDeclaration = matcher.group(4); // Variable name + semicolon
            
            // Build the replacement string
            StringBuilder replacement = new StringBuilder();
            replacement.append(prefix);
            
            if (accessModifier != null) {
                replacement.append(accessModifier).append(" ");
            }
            
            replacement.append("WebElement").append(variableDeclaration);
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    // Alternative method with more flexible custom type detection
    public static String replaceCustomTypesFlexible(String content) {
        // This regex catches any capitalized word that could be a custom type
        // More flexible but might catch unwanted types
        String regex = "(^|\\s)(public|private|protected)?\\s*([A-Z][a-zA-Z0-9_]*)(\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*;)";
        
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String accessModifier = matcher.group(2);
            String customType = matcher.group(3);
            String variableDeclaration = matcher.group(4);
            
            // Skip if it's a known Java type or common class
            if (isJavaBuiltInType(customType)) {
                continue; // Don't replace built-in types
            }
            
            StringBuilder replacement = new StringBuilder();
            replacement.append(prefix);
            
            if (accessModifier != null) {
                replacement.append(accessModifier).append(" ");
            }
            
            replacement.append("WebElement").append(variableDeclaration);
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private static boolean isJavaBuiltInType(String type) {
        Set<String> javaTypes = Set.of(
            "String", "Integer", "Boolean", "Double", "Float", "Long", "Short", "Byte",
            "Character", "Object", "List", "ArrayList", "HashMap", "Map", "Set", "HashSet",
            "Date", "Calendar", "Exception", "Thread", "File", "Path", "Scanner"
            // Add more Java built-in types as needed
        );
        return javaTypes.contains(type);
    }
    
    // Test method to verify the regex works correctly
    public static void testRegex() {
        String testContent = """
            public Button loginBtn;
            Link signInLink;
            private Text mainHeading;
            protected Input userInput;
            public static final String CONSTANT = "test";
            private List<String> items;
            """;
        
        System.out.println("Original content:");
        System.out.println(testContent);
        System.out.println("\nAfter replacement:");
        System.out.println(replaceCustomTypes(testContent));
    }
}
