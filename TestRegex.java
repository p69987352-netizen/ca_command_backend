
import java.util.regex.*;
public class TestRegex {
    public static void main(String[] args) {
        String text = "Name of the Assessee AMAR PORWAL";
        Pattern p = Pattern.compile("name of the assessee\\\\s+([A-Za-z ]+)");
        Matcher m = p.matcher(text.toLowerCase());
        if(m.find()) System.out.println(m.group(1));
    }
}

