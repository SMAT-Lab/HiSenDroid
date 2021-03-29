package edu.anonymous.utils;

import org.apache.commons.lang3.StringUtils;
import soot.SootClass;

import java.util.List;

public class ApplicationClassFilter {

    private static List<String> classNames;

    /**
     *
     * @param sootClass
     * @return
     */
    public static boolean isApplicationClass(SootClass sootClass){
        return isApplicationClass(sootClass.getPackageName());
    }

    /**
     *
     * @param sootClass
     * @return
     */
    public static boolean isApplicationClass(String clsName) {
        if (StringUtils.isBlank(clsName)) {
            return false;
        }
        if (clsName.startsWith("com.google.")
                || clsName.startsWith("soot.")
                || clsName.startsWith("android.")
                || clsName.startsWith("java.")
                || clsName.startsWith("com.facebook.")
                || clsName.startsWith("org.apache.")
        ) {
            return false;
        }
        return true;
    }

    public static boolean containsClassInSystemPackage(String className) {
        return className.contains("android.") || className.contains("java.") || className.contains("javax.")
                || className.contains("sun.") || className.contains("org.omg.")
                || className.contains("org.w3c.dom.") || className.contains("com.google.")
                || className.contains("com.android.") || className.contains("org.apache.")
                || className.contains("soot.")
                || className.contains("androidx.");
    }

    public static boolean isClassInSystemPackage(String className) {
        return className.startsWith("android.") || className.startsWith("java.") || className.startsWith("javax.")
                || className.startsWith("sun.") || className.startsWith("org.omg.")
                || className.startsWith("org.w3c.dom.") || className.startsWith("com.google.")
                || className.startsWith("com.android.") || className.startsWith("org.apache.")
                || className.startsWith("soot.")
                || className.startsWith("androidx.");
    }

    public static boolean isClassSystemPackage(String className) {
        return className.startsWith("<android.") || className.startsWith("<java.") || className.startsWith("<javax.")
                || className.startsWith("<sun.") || className.startsWith("<org.omg.")
                || className.startsWith("<org.w3c.dom.") || className.startsWith("<com.google.")
                || className.startsWith("<com.android.") || className.startsWith("<org.apache.")
                || className.startsWith("<soot.")
                || className.startsWith("<androidx.");
    }

    public static boolean isAndroidSystemPackage(String className) {
        return className.startsWith("<android.")
                || className.startsWith("<com.android.")
                || className.startsWith("<androidx.");
    }

}