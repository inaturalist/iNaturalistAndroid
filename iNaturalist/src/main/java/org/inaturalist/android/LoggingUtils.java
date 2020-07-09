package org.inaturalist.android;

import android.content.Context;
import android.os.Environment;

import org.tinylog.Logger;
import org.tinylog.configuration.Configuration;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LoggingUtils {

    private static final String LOGS_FILENAME_EXTENSION = "log";
    private static final String COMPRESSED_LOGS_FILENAME_EXTENSION = "zip";
    private static final String LOGS_FILENAME_PREFIX = "debug-";

    private static final int MAX_LOG_FILE_SIZE_MB = 10; // After what size (in MB) should we move to the next log file
    private static final int COMPRESSION_BUFFER_SIZE = 1024;
    private static final String TAG = "LoggingUtils";

    private static GlobalExceptionHandler sExceptionHandler;

    // Used for finding all debug log files
    private static FilenameFilter sLogFileFilter = new FilenameFilter() {
        File f;
        public boolean accept(File dir, String name) {
            return (
                    (name.endsWith("." + LOGS_FILENAME_EXTENSION) || name.endsWith("." + COMPRESSED_LOGS_FILENAME_EXTENSION)) &&
                    name.startsWith(LOGS_FILENAME_PREFIX)
            );
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
        String filePath = String.format(Locale.ENGLISH, "%s/%s{date:yyyy-MM-dd}-{count}.%s",
                getDebugLogsDirectory(context).getAbsolutePath(),
                LOGS_FILENAME_PREFIX, LOGS_FILENAME_EXTENSION);
        Configuration.set("writer2.file", filePath);
        Configuration.set("writer2.policies", String.format(Locale.ENGLISH, "daily, size: %dmb", MAX_LOG_FILE_SIZE_MB));
        Configuration.set("writer2.buffered", "true");
        Configuration.set("writingthread", "true");

        // This is how we catch global uncaught exceptions and log them to file
        sExceptionHandler = new GlobalExceptionHandler();
    }

    private static File getDebugLogsDirectory(Context context) {
        return context.getExternalCacheDir();
    }

    public static void compressDebugLogs(Context context) {
        // Find all uncompressed log files and compresses them into zip files (saves room, and
        // required when sending large log files over email)

        Logger.tag(TAG).debug("compressDebugLogs");

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date today = cal.getTime();

        List<File> files = getAllDebugLogs(context, null, null, false);

        for (File file : files) {
            String filename = file.getName();
            String dateName = filename.substring(LOGS_FILENAME_PREFIX.length(), filename.length() - LOGS_FILENAME_EXTENSION.length() - 1);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date fileDate = null;
            try {
                fileDate = format.parse(dateName);
                if (fileDate.compareTo(today) >= 0) {
                    if (file.length() < MAX_LOG_FILE_SIZE_MB * 1024 * 1024 - 1024) {
                        // A file from today, and less than 10mb (and change) - which means it's still being written to - don't compress it
                        continue;
                    }
                }

                // Compress file
                String fullPath = file.getPath();
                String zipFilename = fullPath.substring(0, fullPath.lastIndexOf(".") + 1) + COMPRESSED_LOGS_FILENAME_EXTENSION;
                boolean success = compressFile(fullPath, zipFilename);

                if (success) {
                    // Delete original, uncompressed file
                    file.delete();
                }

            } catch (ParseException e) {
                e.printStackTrace();
                continue;
            }
        }

        Logger.tag(TAG).debug("compressDebugLogs - end");
    }

    private static boolean compressFile(String inputFilename, String zipFileName) {
        try {
            BufferedInputStream origin = null;
            FileOutputStream dest = new FileOutputStream(zipFileName);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            byte data[] = new byte[COMPRESSION_BUFFER_SIZE];

            FileInputStream fi = new FileInputStream(inputFilename);
            origin = new BufferedInputStream(fi, COMPRESSION_BUFFER_SIZE);

            ZipEntry entry = new ZipEntry(inputFilename.substring(inputFilename.lastIndexOf("/") + 1));
            out.putNextEntry(entry);

            int count;

            while ((count = origin.read(data, 0, COMPRESSION_BUFFER_SIZE)) != -1) {
                out.write(data, 0, count);
            }
            origin.close();

            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static List<File> getAllDebugLogs(Context context, Date startDate, Date endDate, boolean returnCompressedFiles) {
        File dir = getDebugLogsDirectory(context);
        File[] files = dir.listFiles(sLogFileFilter);

        if ((startDate == null) && (endDate == null) && (returnCompressedFiles)) return Arrays.asList(files);

        List<File> filteredFiles = new ArrayList<>();

        for (File file : files) {
            String filename = file.getName();

            if (!returnCompressedFiles && filename.endsWith(COMPRESSED_LOGS_FILENAME_EXTENSION)) continue;

            if ((startDate == null) && (endDate == null)) {
                filteredFiles.add(file);
                continue;
            }

            String dateName = filename.substring(LOGS_FILENAME_PREFIX.length(), filename.length() - LOGS_FILENAME_EXTENSION.length() - 1);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date fileDate = null;
            try {
                fileDate = format.parse(dateName);
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
        List<File> files = getAllDebugLogs(context, null, null, true);

        // Remove all debug logs
        for (File file : files) {
            file.delete();
        }
    }

    public static void clearOldLogs(Context context, int dayCount) {
        Logger.tag(TAG).debug("clearOldLogs");

        // Clear old log files (older than the last X days, according to debug settings)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, -dayCount);
        List<File> files = getAllDebugLogs(context, null, cal.getTime(), true);

        for (File file : files) {
            file.delete();
        }

        Logger.tag(TAG).debug("clearOldLogs - done");
    }
}
