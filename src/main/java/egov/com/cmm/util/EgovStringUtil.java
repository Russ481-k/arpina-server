package egov.com.cmm.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class EgovStringUtil {
    
    public static String getTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf.format(new Date());
    }
} 