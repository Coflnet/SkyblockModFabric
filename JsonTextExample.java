// Example: How to test the new JSON text widget functionality
// This shows how the DescriptionHandler.DescModification should be formatted

public class JsonTextExample {
    
    public static void main(String[] args) {
        // Example 1: Simple text with hover
        String example1 = "[{\"text\":\"Sample Text\",\"hover\":\"Text displayed on hovering over sample text\"}]";
        
        // Example 2: Text with click action and hover
        String example2 = "[{\"text\":\"Click to run command\",\"onClick\":\"/cofl echo hi\",\"hover\":\"Click to run\"}]";
        
        // Example 3: Multiple elements in one line
        String example3 = "[" +
            "{\"text\":\"Welcome to CoflSky! \",\"hover\":\"CoflSky is a Skyblock auction house mod\"}," +
            "{\"text\":\"[Help]\",\"onClick\":\"/cofl help\",\"hover\":\"Click to get help with CoflSky commands\"}," +
            "{\"text\":\" | \"}," +
            "{\"text\":\"[Website]\",\"onClick\":\"https://coflnet.com\",\"hover\":\"Visit the CoflSky website\"}" +
            "]";
        
        // Example 4: Different click actions
        String example4 = "[" +
            "{\"text\":\"Run Command: \",\"hover\":\"This will execute a command\"}," +
            "{\"text\":\"[/cofl help]\",\"onClick\":\"/cofl help\",\"hover\":\"Execute /cofl help\"}," +
            "{\"text\":\"\\nSuggest Command: \",\"hover\":\"This will suggest a command\"}," +
            "{\"text\":\"[suggest]\",\"onClick\":\"suggest:/cofl config\",\"hover\":\"Suggest /cofl config\"}," +
            "{\"text\":\"\\nCopy Text: \",\"hover\":\"This will copy text to clipboard\"}," +
            "{\"text\":\"[copy]\",\"onClick\":\"copy:CoflSky is awesome!\",\"hover\":\"Copy text to clipboard\"}," +
            "{\"text\":\"\\nOpen URL: \",\"hover\":\"This will open a website\"}," +
            "{\"text\":\"[website]\",\"onClick\":\"https://coflnet.com\",\"hover\":\"Open CoflNet website\"}" +
            "]";
        
        // To use these in your mod, create a DescriptionHandler.DescModification like this:
        // DescriptionHandler.DescModification modification = new DescriptionHandler.DescModification();
        // modification.type = "APPEND";
        // modification.value = example1; // or any of the examples above
        
        System.out.println("Example 1 (Simple hover):");
        System.out.println(example1);
        System.out.println();
        
        System.out.println("Example 2 (Click action):");
        System.out.println(example2);
        System.out.println();
        
        System.out.println("Example 3 (Multiple elements):");
        System.out.println(example3);
        System.out.println();
        
        System.out.println("Example 4 (All click types):");
        System.out.println(example4);
    }
}
