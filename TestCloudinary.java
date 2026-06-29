import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class TestCloudinary {
    public static void main(String[] args) throws Exception {
        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "dlxenypig",
                "api_key", "349619621614885",
                "api_secret", "kT4cXetEE3dg2YQYzC2dwC_PfOg",
                "secure", true));
        
        byte[] dummyPdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".getBytes();
        
        Map uploadResult = cloudinary.uploader().upload(dummyPdf, ObjectUtils.asMap(
                "public_id", "test_pdf_" + System.currentTimeMillis() + ".pdf",
                "resource_type", "raw"
        ));
        
        String urlStr = (String) uploadResult.get("secure_url");
        System.out.println("Uploaded RAW URL: " + urlStr);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        System.out.println("HTTP Response for RAW: " + conn.getResponseCode());
        
        // Also test "image" type
        Map uploadResult2 = cloudinary.uploader().upload(dummyPdf, ObjectUtils.asMap(
                "public_id", "test_pdf_img_" + System.currentTimeMillis() + ".pdf",
                "resource_type", "image"
        ));
        
        String urlStr2 = (String) uploadResult2.get("secure_url");
        System.out.println("Uploaded IMAGE URL: " + urlStr2);
        
        HttpURLConnection conn2 = (HttpURLConnection) new URL(urlStr2).openConnection();
        System.out.println("HTTP Response for IMAGE: " + conn2.getResponseCode());
    }
}
