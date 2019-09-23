package org.inaturalist.android;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.collections4.CollectionUtils;
import org.tinylog.configuration.Configuration;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class LoggingUtils {

    private static final String LOGS_FILENAME_EXTENSION = "log";
    private static final String LOGS_FILENAME_PREFIX = "debug-";

    private static GlobalExceptionHandler sExceptionHandler;

    // Used for finding all debug log files
    private static FilenameFilter sLogFileFilter = new FilenameFilter() {
        File f;
        public boolean accept(File dir, String name) {
            if (name.endsWith("." + LOGS_FILENAME_EXTENSION) && name.startsWith(LOGS_FILENAME_PREFIX)) return true;
            f = new File(dir.getAbsolutePath() + "/" + name);
            return f.isDirectory();
        }
    };


    public static void initializeLogger(Context context) {
        // Configure the logger

        // First writer - just writes to LogCat
        Configuration.set("writer1", "logcat");
        Configuration.set("writer1.level", "trace");
        Configuration.set("writer1.format", "{message}");

        // Second writer - rotating writer to log files, based on date (one log file per day)
        Configuration.set("writer2", "rolling file");
        Configuration.set("writer2.level", "trace");
        Configuration.set("writer2.format", "{date}\t{class-name}.{method}():{line}: {tag}: {message}");
        String filePath = String.format("%s/%s{date:yyyy-MM-dd}.%s",
                getDebugLogsDirectory(context).getAbsolutePath(),
                LOGS_FILENAME_PREFIX, LOGS_FILENAME_EXTENSION);
        Configuration.set("writer2.file", filePath);
        Configuration.set("writer2.policies", "daily");
        Configuration.set("writer2.buffered", "true");
        Configuration.set("writingthread", "true");

        // This is how we catch global uncaught exceptions and log them to file
        sExceptionHandler = new GlobalExceptionHandler();
    }

    private static File getDebugLogsDirectory(Context context) {
        return context.getExternalCacheDir();
    }

    public static List<File> getAllDebugLogs(Context context, Date startDate, Date endDate) {
        File dir = getDebugLogsDirectory(context);
        File[] files = dir.listFiles(sLogFileFilter);

        if ((startDate == null) && (endDate == null)) return Arrays.asList(files);

        List<File> filteredFiles = new ArrayList<>();

        for (File file : files) {
            String filename = file.getName();
            SimpleDateFormat format = new SimpleDateFormat(String.format("'%s'yyyy-MM-dd'.%s'", LOGS_FILENAME_PREFIX, LOGS_FILENAME_EXTENSION));
            Date fileDate = null;
            try {
                fileDate = format.parse(filename);
                if ((startDate != null) && (fileDate.compareTo(startDate) >= 0) && (endDate == null)) {
                    filteredFiles.add(file);
                } if ((endDate != null) && (fileDate.compareTo(endDate) <= 0)) {
                    filteredFiles.add(file);
                }

            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        return filteredFiles;
    }

    public static void clearAllLogs(Context context) {
        List<File> files = getAllDebugLogs(context, null, null);

        // Remove all debug logs
        for (File file : files) {
            file.delete();
        }
    }

    public static void clearOldLogs(Context context, int dayCount) {
        // Clear old log files (older than the last X days, according to debug settings)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, -dayCount);
        List<File> files = getAllDebugLogs(context, null, cal.getTime());

        for (File file : files) {
            file.delete();
        }
    }
}
