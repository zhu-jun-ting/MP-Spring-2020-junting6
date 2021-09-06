package edu.illinois.cs.cs125.spring2020.mp.shadows;

import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import edu.illinois.cs.cs125.robolectricsecurity.Trusted;

@Trusted
@Implements(Log.class)
@SuppressWarnings("unused")
public class ShadowLog {

    private static final int DISPLAYED = 0;
    private static final int IGNORED = -1;
    private static final int INTERNAL = -2;

    private static LogConfig config = new LogConfig();
    private static Map<String, Boolean> internalTagsCache = new HashMap<>();

    public static void loadConfig(String filename) {
        try {
            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            config = objectMapper.readValue(new File(filename), LogConfig.class);
        } catch (FileNotFoundException e) {
            // Use the defaults (no logging)
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static boolean isInternal(String tag) {
        if (internalTagsCache.containsKey(tag)) return internalTagsCache.get(tag);
        boolean foundLog = false;
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if (ste.getClassName().equals(Log.class.getName())) {
                foundLog = true;
            } else if (foundLog) {
                boolean internal = !ste.getClassName().startsWith("edu.illinois.cs.cs125.spring2020.mp.");
                internalTagsCache.put(tag, internal);
                return internal;
            }
        }
        throw new IllegalStateException("isInternal called without Log on the call stack");
    }

    @SuppressWarnings("ConstantConditions")
    private static int tryLog(String level, String tag, String message, Throwable throwable) {
        if (!config.enabled) return IGNORED;
        if (!config.levels.getOrDefault(level, false)) return IGNORED;
        if (isInternal(tag)) return INTERNAL;
        String line = level.toUpperCase() + "/" + tag + ": " + message;
        if ("vdi".contains(level)) {
            System.out.println(line);
            if (throwable != null) throwable.printStackTrace(System.out);
        } else {
            System.err.println(line);
            if (throwable != null) throwable.printStackTrace();
        }
        return DISPLAYED;
    }

    @Implementation
    public static int v(String tag, String message, Throwable throwable) {
        return tryLog("v", tag, message, throwable);
    }

    @Implementation
    public static int v(String tag, String message) {
        return v(tag, message, null);
    }

    @Implementation
    public static int d(String tag, String message, Throwable throwable) {
        return tryLog("d", tag, message, throwable);
    }

    @Implementation
    public static int d(String tag, String message) {
        return d(tag, message, null);
    }

    @Implementation
    public static int i(String tag, String message, Throwable throwable) {
        return tryLog("i", tag, message, throwable);
    }

    @Implementation
    public static int i(String tag, String message) {
        return i(tag, message, null);
    }

    @Implementation
    public static int w(String tag, String message, Throwable throwable) {
        return tryLog("w", tag, message, throwable);
    }

    @Implementation
    public static int w(String tag, String message) {
        return w(tag, message, null);
    }

    @Implementation
    public static int e(String tag, String message, Throwable throwable) {
        return tryLog("e", tag, message, throwable);
    }

    @Implementation
    public static int e(String tag, String message) {
        return e(tag, message, null);
    }

    @Implementation
    public static int wtf(String tag, String message, Throwable throwable) {
        System.err.println("WTF/" + tag + ": " + message);
        if (throwable != null) throwable.printStackTrace();
        return DISPLAYED;
    }

    @Implementation
    public static int wtf(String tag, String message) {
        return wtf(tag, message, null);
    }

    @Implementation
    public static int println(int priority, String tag, String message) {
        System.out.println(tag + ": " + message);
        return DISPLAYED;
    }

    private static final class LogConfig {

        public boolean enabled;
        public Map<String, Boolean> levels = new HashMap<>();

    }

}
