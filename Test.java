
import java.util.regex.*;
import java.util.*;

public class Test {
    public static void main(String[] args) {
        String text = "1 Rent received 44,73,825 44,73,825";
        Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)");
        String kw = "Rent received";
        int idx = text.indexOf(kw);
        String sub = text.substring(idx + kw.length());
        Matcher m = AMOUNT_PATTERN.matcher(sub);
        if (m.find()) {
            System.out.println("Found: " + m.group(1));
        } else {
            System.out.println("Not found");
        }
    }
}

