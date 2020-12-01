package edu.monash.utils;

import edu.monash.GlobalRef;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

public class ExtractApkInfoUtil {

    public static void extractApkInfo(String apkPath)
    {
        GlobalRef.apkPath = apkPath;

        try
        {
            ProcessManifest manifest = new ProcessManifest(apkPath);

            GlobalRef.pkgName = manifest.getPackageName();
            GlobalRef.apkVersionCode = manifest.getVersionCode();
            GlobalRef.apkVersionName = manifest.getVersionName();
            GlobalRef.apkMinSdkVersion = manifest.getMinSdkVersion();
            GlobalRef.apkPermissions = manifest.getPermissions();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}
