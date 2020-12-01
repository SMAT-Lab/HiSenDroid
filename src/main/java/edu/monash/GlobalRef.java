package edu.monash;

import edu.monash.model.*;
import edu.monash.typeref.ArrayVarValue;
import org.xmlpull.v1.XmlPullParserException;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.util.Chain;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalRef
{
	public static String apkPath;
	public static String pkgName;
	public static String apkVersionName;
	public static int apkVersionCode = -1;
	public static int apkMinSdkVersion;
	public static Set<String> apkPermissions;

	public static String clsPath;

	//Configuration files
	public static final String WORKSPACE = "workspace";
	public static final String SOOTOUTPUT = "sootOutput";
	public static String fieldCallsConfigPath = "res/FieldCalls.txt";
	//public static String coalModelPath = "res/reflection.edu.monash.model";
	public static String coalModelPath = "res/reflection_simple.model";

	public static SootMethod dummyMainMethod = null;
	public static SootClass dummyMainClass = null;
	public static Set<SootClass> dynamicFragment = new HashSet<>();

	/**
	 * Branch Code Map
	 */
	public static List<BranchUnit>  branchUnits = new ArrayList<>();

	public static Map<String, String> pscoutAPIMap = new HashMap();

	public static Set<String> hiddenSensitiveAPIs = new HashSet<>();

	public static Map<String, String> source2sink = new HashMap<>();

	public static Map<String, List<String>> sharePreferenceMap = new HashMap<>();

	public static JimpleBasedInterproceduralCFG iCfg;

}
