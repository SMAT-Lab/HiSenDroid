package edu.anonymous.retarget;

import edu.anonymous.GlobalRef;
import edu.anonymous.utils.InstrumentationUtils;
import heros.solver.Pair;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.callbacks.AbstractCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition;
import soot.jimple.infoflow.android.callbacks.DefaultCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.FastCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.filters.AlienFragmentFilter;
import soot.jimple.infoflow.android.entryPointCreators.AndroidEntryPointCreator;
import soot.jimple.infoflow.android.iccta.Component;
import soot.jimple.infoflow.android.iccta.ICCDummyMainCreator;
import soot.jimple.infoflow.android.iccta.IccInstrumenter;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.LayoutFileParser;
import soot.jimple.infoflow.android.resources.controls.AndroidLayoutControl;
import soot.jimple.infoflow.entryPointCreators.AndroidEntryPointConstants;
import soot.jimple.infoflow.memory.FlowDroidMemoryWatcher;
import soot.jimple.infoflow.memory.FlowDroidTimeoutWatcher;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.infoflow.values.IValueProvider;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.util.Chain;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;


public class DummyMainGenerator extends SceneTransformer {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private String apkFileLocation = null;
	private boolean fullMethodCover = false;
	
	public static final String DUMMY_CLASS_NAME = "dummyMainClass";
	public static final String DUMMY_METHOD_NAME = "main";
	protected String callbackFile = "AndroidCallbacks.txt";
	
	public static Set<String> addtionalDexFiles = new HashSet<String>();

	public AndroidEntryPointCreator entryPointCreator = null;
	public ProcessManifest manifest = null;
	public Set<SootClass> entrypoints = null;
	public MultiMap<SootClass, CallbackDefinition> callbackMethods = new HashMultiMap<>();
	public MultiMap<SootClass, SootClass> fragmentClasses = new HashMultiMap<>();
	public Set<String> callbackClasses = null;
	public InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
	public IValueProvider valueProvider = null;
	public IccInstrumenter iccInstrumenter = null;
	public ARSCFileParser resources = null;
	public SootClass scView = null;

	public DummyMainGenerator(String apkFileLocation)
	{
		this.apkFileLocation = apkFileLocation;
	}

	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		try {
			ProcessManifest processManifest = new ProcessManifest(apkFileLocation);
			Set<String> entryPoints = processManifest.getEntryPointClasses();

			SootMethod mainMethod = generateMain(entryPoints);

			System.out.println(mainMethod.retrieveActiveBody());

		} catch (IOException e) {
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Parses common app resources such as the manifest file
	 *
	 * @throws IOException            Thrown if the given source/sink file could not
	 *                                be read.
	 * @throws XmlPullParserException Thrown if the Android manifest file could not
	 *                                be read.
	 */
	public void parseAppResources() throws IOException, XmlPullParserException {
		final String targetAPK = config.getAnalysisFileConfig().getTargetAPKFile();

		// To look for callbacks, we need to start somewhere. We use the Android
		// lifecycle methods for this purpose.
		this.manifest = new ProcessManifest(targetAPK);
		Set<String> entryPoints = manifest.getEntryPointClasses();
		this.entrypoints = new HashSet<>(entryPoints.size());
		for (String className : entryPoints) {
			SootClass sc = Scene.v().getSootClassUnsafe(className);
			if (sc != null)
				this.entrypoints.add(sc);
		}

		// Parse the resource file
		long beforeARSC = System.nanoTime();
		this.resources = new ARSCFileParser();
		this.resources.parse(targetAPK);
		logger.info("ARSC file parsing took " + (System.nanoTime() - beforeARSC) / 1E9 + " seconds");
	}

	public static Set<String> getComponents(String apkFileLocation)
	{
		Set<String> entrypoints = null;
		try
		{
			ProcessManifest processMan = new ProcessManifest(apkFileLocation);
			entrypoints = processMan.getEntryPointClasses();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}

		return entrypoints;
	}

	public static SootMethod generateDummyMain(String apkFileLocation)
	{
		SootMethod mainMethod = null;

		try
		{
			ProcessManifest processMan = new ProcessManifest(apkFileLocation);
			Set<String> entrypoints = processMan.getEntryPointClasses();

			DummyMainGenerator dmGenerator = new DummyMainGenerator(apkFileLocation);

			mainMethod = dmGenerator.generateMain(entrypoints);

			System.out.println(mainMethod.retrieveActiveBody());
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}

		return mainMethod;
	}

	public SootMethod generateMain(Set<String> components)
	{
		SootMethod mainMethod = new SootMethod(DUMMY_METHOD_NAME,
				Arrays.asList(new Type[] {ArrayType.v(RefType.v("java.lang.String"), 1)}),
    			VoidType.v(),
    			Modifier.PUBLIC | Modifier.STATIC);
    	JimpleBody body = Jimple.v().newBody(mainMethod);
    	mainMethod.setActiveBody(body);

    	SootClass sootClass = new SootClass(DUMMY_CLASS_NAME);
    	sootClass.setSuperclass(Scene.v().getSootClass("java.lang.Object"));
    	//sootClass.setPhantom(false);
    	sootClass.setApplicationClass();
    	sootClass.setInScene(true);

    	sootClass.addMethod(mainMethod);

    	LocalGenerator generator = new LocalGenerator(body);

    	body.insertIdentityStmts();

		for (String str : components)
		{
			SootClass sc = Scene.v().getSootClass(str);
			if (sc.isPhantom())
			{
				continue;
			}

			SootMethod method = ICCDummyMainCreator.v().generateDummyMainMethod(str);
			instrumentDummyMainMethod(method);

			SootClass cls = method.getDeclaringClass();
			SootMethod sootMethod = cls.getMethod("<init>", new ArrayList<Type>());

			if (null == sootMethod)
			{
				throw new RuntimeException("No default constructor for comp " + cls.getName());
			}

			Local al = generator.generateLocal(cls.getType());
			Unit newU = (Unit) Jimple.v().newAssignStmt(al, Jimple.v().newNewExpr(cls.getType()));

			Unit initU = (Unit) Jimple.v().newInvokeStmt(
					Jimple.v().newSpecialInvokeExpr(al, sootMethod.makeRef()));

			Unit callU = (Unit) Jimple.v().newInvokeStmt(
					Jimple.v().newSpecialInvokeExpr(al, method.makeRef()));

			body.getUnits().add(newU);
			body.getUnits().add(initU);
			body.getUnits().add(callU);
		}

		body.getUnits().add(Jimple.v().newReturnVoidStmt());

		if (fullMethodCover)
		{
			mainMethod = appendNonComponents(mainMethod);
		}

		System.out.println(body);

		body.validate();

		return mainMethod;
	}

	public void instrumentDummyMainMethod(SootMethod mainMethod)
	{
		Body body = mainMethod.getActiveBody();

		PatchingChain<Unit> units = body.getUnits();
		for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext(); )
		{
			Stmt stmt = (Stmt) iter.next();

			if (stmt instanceof IdentityStmt)
			{
				continue;
			}

			//For the purpose of confusion dex optimization (because of the strategy of generating dummyMain method)
			AssignStmt aStmt = (AssignStmt) stmt;
			SootMethod fuzzyMe = generateFuzzyMethod(mainMethod.getDeclaringClass());
			InvokeExpr invokeExpr = Jimple.v().newVirtualInvokeExpr(body.getThisLocal(), fuzzyMe.makeRef());
			Unit assignU = Jimple.v().newAssignStmt(aStmt.getLeftOp(), invokeExpr);
			units.insertAfter(assignU, aStmt);

			break;
		}
	}


	public SootMethod appendNonComponents(SootMethod mainMethod)
	{
		Set<String> coveredMethods = new HashSet<String>();

		CallGraph cg = Scene.v().getCallGraph();

		for (Iterator<Edge> iter = cg.iterator(); iter.hasNext(); )
		{
			Edge edge = iter.next();

			coveredMethods.add(edge.src().getSignature());
			coveredMethods.add(edge.tgt().getSignature());
		}

		Chain<SootClass> sootClasses = Scene.v().getApplicationClasses();

		for (Iterator<SootClass> iter = sootClasses.iterator(); iter.hasNext();)
		{
			SootClass sc = iter.next();

			List<SootMethod> methodList = sc.getMethods();
			for (SootMethod sm : methodList)
			{
				if (sm.getDeclaringClass().getName().startsWith("android.support"))
				{
					continue;
				}

				if (sc.isPhantom() || ! sm.isConcrete())
				{
					continue;
				}

				if (sm.getName().equals("<init>") || sm.getName().equals("<clinit>"))
				{
					continue;
				}



				if (coveredMethods.contains(sm.getSignature()))
				{
					//Already covered.
					continue;
				}

				mainMethod = addMethod(mainMethod, sm.getSignature());
			}
		}

		return mainMethod;
	}

	public SootMethod addMethod(SootMethod mainMethod, String methodSignature)
	{
		Body body = mainMethod.getActiveBody();

		Stmt returnStmt = null;

    	PatchingChain<Unit> units = body.getUnits();
    	for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext(); )
    	{
    		Stmt stmt = (Stmt) iter.next();

    		if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt)
    		{
    			returnStmt = stmt;
    		}
    	}

    	SootMethod sm = Scene.v().getMethod(methodSignature);

    	List<Type> paramTypes = sm.getParameterTypes();
    	List<Value> paramValues = new ArrayList<Value>();
    	for (int i = 0; i < paramTypes.size(); i++)
    	{
    		paramValues.add(InstrumentationUtils.toDefaultSootTypeValue(paramTypes.get(i)));
    	}


    	if (sm.isStatic())    //No need to construct its obj ref
    	{
    		InvokeExpr expr = Jimple.v().newStaticInvokeExpr(sm.makeRef(), paramValues);
    		Unit callU = Jimple.v().newInvokeStmt(expr);
    		units.insertBefore(callU, returnStmt);
    	}
    	else
    	{
    		//new obj first and then call the method

    		SootClass sc = sm.getDeclaringClass();
    		List<SootMethod> methods = sc.getMethods();

    		SootMethod init = null;
    		SootMethod clinit = null;

    		for (SootMethod method : methods)
    		{
    			if (method.getName().equals("<clinit>"))
    			{
    				clinit = method;
    			}

    			if (method.getName().equals("<init>"))
    			{
    				init = method;
    			}
    		}

    		LocalGenerator localGenerator = new LocalGenerator(body);

    		Local obj = localGenerator.generateLocal(sc.getType());

    		Unit newU = Jimple.v().newAssignStmt(obj, Jimple.v().newNewExpr(sc.getType()));
    		units.insertBefore(newU, returnStmt);

    		if (null != clinit)
    		{
    			Unit clinitCallU = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(clinit.makeRef()));
    			units.insertBefore(clinitCallU, returnStmt);
    		}

    		if (null != init)
    		{
    			List<Type> initParamTypes = init.getParameterTypes();
    	    	List<Value> initParamValues = new ArrayList<Value>();
    	    	for (int i = 0; i < initParamTypes.size(); i++)
    	    	{
    	    		initParamValues.add(InstrumentationUtils.toDefaultSootTypeValue(initParamTypes.get(i)));
    	    	}

    	    	Unit initCallU = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(obj, init.makeRef(), initParamValues));
    	    	units.insertBefore(initCallU, returnStmt);
    		}
    		else
    		{
    			throw new RuntimeException("Is it possible that a class does not contain an <init> method?");
    		}
    	}

    	System.out.println(body);
    	body.validate();

    	return mainMethod;
	}

	public SootMethod generateFuzzyMethod(SootClass sootClass)
	{
    	String name = "fuzzyMe";
	    List<Type> parameters = new ArrayList<Type>();
	    Type returnType = IntType.v();
	    int modifiers = Modifier.PUBLIC;
	    SootMethod fuzzyMeMethod = new SootMethod(name, parameters, returnType, modifiers);
	    sootClass.addMethod(fuzzyMeMethod);

	    {
	    	Body b = Jimple.v().newBody(fuzzyMeMethod);
	    	fuzzyMeMethod.setActiveBody(b);
	    	LocalGenerator lg = new LocalGenerator(b);
	        Local thisLocal = lg.generateLocal(sootClass.getType());
	        Unit thisU = Jimple.v().newIdentityStmt(thisLocal,
	                Jimple.v().newThisRef(sootClass.getType()));
	        Unit returnU = Jimple.v().newReturnStmt(IntConstant.v(1));
	        b.getUnits().add(thisU);
	        b.getUnits().add(returnU);
	    }

	    return fuzzyMeMethod;
	}

	/**
	 * Creates the {@link AndroidEntryPointCreator} instance which will later create
	 * the dummy main method for the analysis
	 *
	 * @param component The single component to include in the dummy main method.
	 *                  Pass null to include all components in the dummy main
	 *                  method.
	 * @return The {@link AndroidEntryPointCreator} responsible for generating the
	 *         dummy main method
	 */
	private AndroidEntryPointCreator createEntryPointCreator(SootClass component) {
		Set<SootClass> components = getComponentsToAnalyze(component);

		// If we we already have an entry point creator, we make sure to clean up our
		// leftovers from previous runs
		if (entryPointCreator == null)
			entryPointCreator = new AndroidEntryPointCreator(manifest, components);
		else {
			entryPointCreator.removeGeneratedMethods(false);
			entryPointCreator.reset();
		}

		MultiMap<SootClass, SootMethod> callbackMethodSigs = new HashMultiMap<>();
		if (component == null) {
			// Get all callbacks for all components
			for (SootClass sc : this.callbackMethods.keySet()) {
				Set<CallbackDefinition> callbackDefs = this.callbackMethods.get(sc);
				if (callbackDefs != null)
					for (CallbackDefinition cd : callbackDefs)
						callbackMethodSigs.put(sc, cd.getTargetMethod());
			}
		} else {
			// Get the callbacks for the current component only
			for (SootClass sc : components) {
				Set<CallbackDefinition> callbackDefs = this.callbackMethods.get(sc);
				if (callbackDefs != null)
					for (CallbackDefinition cd : callbackDefs)
						callbackMethodSigs.put(sc, cd.getTargetMethod());
			}
		}
		entryPointCreator.setCallbackFunctions(callbackMethodSigs);
		entryPointCreator.setFragments(fragmentClasses);
		entryPointCreator.setComponents(components);
		return entryPointCreator;
	}

	/**
	 * Gets the components to analyze. If the given component is not null, we assume
	 * that only this component and the application class (if any) shall be
	 * analyzed. Otherwise, all components are to be analyzed.
	 *
	 * @param component A component class name to only analyze this class and the
	 *                  application class (if any), or null to analyze all classes.
	 * @return The set of classes to analyze
	 */
	private Set<SootClass> getComponentsToAnalyze(SootClass component) {
		if (component == null)
			return this.entrypoints;
		else {
			// We always analyze the application class together with each
			// component
			// as there might be interactions between the two
			Set<SootClass> components = new HashSet<>(2);
			components.add(component);

			String applicationName = manifest.getApplicationName();
			if (applicationName != null && !applicationName.isEmpty())
				components.add(Scene.v().getSootClassUnsafe(applicationName));
			return components;
		}
	}

	/**
	 * Calculates the set of callback methods declared in the XML resource files or
	 * the app's source code
	 *
	 * @param lfp       The layout file parser to be used for analyzing UI controls
	 * @param component The Android component for which to compute the callbacks.
	 *                  Pass null to compute callbacks for all components.
	 * @throws IOException Thrown if a required configuration cannot be read
	 */
	public void calculateCallbackMethods(LayoutFileParser lfp, SootClass component) throws IOException {
		final InfoflowAndroidConfiguration.CallbackConfiguration callbackConfig = new InfoflowAndroidConfiguration.CallbackConfiguration();

		// Make sure that we don't have any leftovers from previous runs
		PackManager.v().getPack("wjtp").remove("wjtp.lfp");
		PackManager.v().getPack("wjtp").remove("wjtp.ajc");

		// Get the classes for which to find callbacks
		Set<SootClass> entryPointClasses = getComponentsToAnalyze(component);

		// Collect the callback interfaces implemented in the app's
		// source code. Note that the filters should know all components to
		// filter out callbacks even if the respective component is only
		// analyzed later.
		AbstractCallbackAnalyzer jimpleClass = callbackClasses == null
				? new DefaultCallbackAnalyzer(config, entryPointClasses, callbackFile)
				: new DefaultCallbackAnalyzer(config, entryPointClasses, callbackClasses);
		if (valueProvider != null)
			jimpleClass.setValueProvider(valueProvider);
//		jimpleClass.addCallbackFilter(new AlienHostComponentFilter(entrypoints));
//		jimpleClass.addCallbackFilter(new ApplicationCallbackFilter(entrypoints));
//		jimpleClass.addCallbackFilter(new UnreachableConstructorFilter());
		jimpleClass.collectCallbackMethods();

		// Find the user-defined sources in the layout XML files. This
		// only needs to be done once, but is a Soot phase.
		lfp.parseLayoutFile(config.getAnalysisFileConfig().getTargetAPKFile());

		// Watch the callback collection algorithm's memory consumption
		FlowDroidMemoryWatcher memoryWatcher = null;
		FlowDroidTimeoutWatcher timeoutWatcher = null;
		if (jimpleClass instanceof IMemoryBoundedSolver) {
			memoryWatcher = new FlowDroidMemoryWatcher(config.getMemoryThreshold());
			memoryWatcher.addSolver((IMemoryBoundedSolver) jimpleClass);

			// Make sure that we don't spend too much time in the callback
			// analysis
			if (callbackConfig.getCallbackAnalysisTimeout() > 0) {
				timeoutWatcher = new FlowDroidTimeoutWatcher(callbackConfig.getCallbackAnalysisTimeout());
				timeoutWatcher.addSolver((IMemoryBoundedSolver) jimpleClass);
				timeoutWatcher.start();
			}
		}

		try {
			int depthIdx = 0;
			boolean hasChanged = true;
			boolean isInitial = true;
			while (hasChanged) {
				hasChanged = false;

				// Check whether the solver has been aborted in the meantime
				if (jimpleClass instanceof IMemoryBoundedSolver) {
					if (((IMemoryBoundedSolver) jimpleClass).isKilled())
						break;
				}

				// Create the new iteration of the main method
				createMainMethod(component);

				// Since the gerenation of the main method can take some time,
				// we check again whether we need to stop.
				if (jimpleClass instanceof IMemoryBoundedSolver) {
					if (((IMemoryBoundedSolver) jimpleClass).isKilled())
						break;
				}

				if (!isInitial) {
					// Reset the callgraph
					releaseCallgraph();

					// We only want to parse the layout files once
					PackManager.v().getPack("wjtp").remove("wjtp.lfp");
				}
				isInitial = false;

				// Run the soot-based operations
				constructCallgraphInternal();
				if (!Scene.v().hasCallGraph())
					throw new RuntimeException("No callgraph in Scene even after creating one. That's very sad "
							+ "and should never happen.");
				PackManager.v().getPack("wjtp").apply();

				// Creating all callgraph takes time and memory. Check whether
				// the solver has been aborted in the meantime
				if (jimpleClass instanceof IMemoryBoundedSolver) {
					if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
						logger.warn("Aborted callback collection because of low memory");
						break;
					}
				}

				MultiMap<SootClass, CallbackDefinition>  callbackDefinitionMultiMap = jimpleClass.getCallbackMethods();
				for(SootClass sc : callbackDefinitionMultiMap.keySet()){
					List<String> targetMethodSigList = getCallbackFunctions(sc);
					for(String methodSig : targetMethodSigList){
						Set<CallbackDefinition> callbackDefinitions = callbackDefinitionMultiMap.get(sc);
						boolean exist = false;
						for(CallbackDefinition callbackDefinition : callbackDefinitions){
							if(callbackDefinition.getTargetMethod().equals(methodSig)){
								exist = true;
							}
						}
						if(!exist){
							CallbackDefinition newCallbackDefinition = new CallbackDefinition(Scene.v().getMethod(methodSig), null, CallbackDefinition.CallbackType.Default);
							callbackDefinitions.add(newCallbackDefinition);
						}
					}
				}

				// Collect the results of the soot-based phases
				if (this.callbackMethods.putAll(jimpleClass.getCallbackMethods()))
					hasChanged = true;

				if (entrypoints.addAll(jimpleClass.getDynamicManifestComponents()))
					hasChanged = true;

				// Collect the XML-based callback methods
				if (collectXmlBasedCallbackMethods(lfp, jimpleClass))
					hasChanged = true;

				// Avoid callback overruns. If we are beyond the callback limit
				// for one entry point, we may not collect any further callbacks
				// for that entry point.
//				if (callbackConfig.getMaxCallbacksPerComponent() > 0) {
//					for (Iterator<SootClass> componentIt = this.callbackMethods.keySet().iterator(); componentIt
//							.hasNext();) {
//						SootClass callbackComponent = componentIt.next();
//						if (this.callbackMethods.get(callbackComponent).size() > callbackConfig
//								.getMaxCallbacksPerComponent()) {
//							componentIt.remove();
//							jimpleClass.excludeEntryPoint(callbackComponent);
//						}
//					}
//				}

				// Check depth limiting
				depthIdx++;
				if (callbackConfig.getMaxAnalysisCallbackDepth() > 0
						&& depthIdx >= callbackConfig.getMaxAnalysisCallbackDepth())
					break;

				// If we work with an existing callgraph, the callgraph never
				// changes and thus it doesn't make any sense to go multiple
				// rounds
				if (config.getSootIntegrationMode() == InfoflowAndroidConfiguration.SootIntegrationMode.UseExistingCallgraph)
					break;
			}
		} catch (Exception ex) {
			logger.error("Could not calculate callback methods", ex);
			throw ex;
		} finally {
			// Shut down the watchers
			if (timeoutWatcher != null)
				timeoutWatcher.stop();
			if (memoryWatcher != null)
				memoryWatcher.close();
		}

		// Filter out callbacks that belong to fragments that are not used by
		// the host activity
		AlienFragmentFilter fragmentFilter = new AlienFragmentFilter(invertMap(fragmentClasses));
		fragmentFilter.reset();
		for (Iterator<Pair<SootClass, CallbackDefinition>> cbIt = this.callbackMethods.iterator(); cbIt.hasNext();) {
			Pair<SootClass, CallbackDefinition> pair = cbIt.next();

			// Check whether the filter accepts the given mapping
			if (!fragmentFilter.accepts(pair.getO1(), pair.getO2().getTargetMethod()))
				cbIt.remove();
			else if (!fragmentFilter.accepts(pair.getO1(), pair.getO2().getTargetMethod().getDeclaringClass())) {
				cbIt.remove();
			}
		}

		// Avoid callback overruns
//		if (callbackConfig.getMaxCallbacksPerComponent() > 0) {
//			for (Iterator<SootClass> componentIt = this.callbackMethods.keySet().iterator(); componentIt.hasNext();) {
//				SootClass callbackComponent = componentIt.next();
//				if (this.callbackMethods.get(callbackComponent).size() > callbackConfig.getMaxCallbacksPerComponent())
//					componentIt.remove();
//			}
//		}

		// Make sure that we don't retain any weird Soot phases
		PackManager.v().getPack("wjtp").remove("wjtp.lfp");
		PackManager.v().getPack("wjtp").remove("wjtp.ajc");

		// Warn the user if we had to abort the callback analysis early
		boolean abortedEarly = false;
		if (jimpleClass instanceof IMemoryBoundedSolver) {
			if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
				logger.warn("Callback analysis aborted early due to time or memory exhaustion");
				abortedEarly = true;
			}
		}
		if (!abortedEarly)
			logger.info("Callback analysis terminated normally");
	}

	private List<String> getCallbackFunctions(SootClass sc) {
		List<String> callbacks = new ArrayList<String>();

		for (SootMethod sm : sc.getMethods()) {

			if (sm.getName().contains("init>")) {
				continue;
			}

			Component.ComponentType compType = Component.getComponentType(sc);
			switch (compType) {
				case Activity:
					if (AndroidEntryPointConstants.getActivityLifecycleMethods().contains(sm.getName())) {
						continue;
					}
					break;
				case Service:
					if (AndroidEntryPointConstants.getServiceLifecycleMethods().contains(sm.getName())) {
						continue;
					}
					break;
				case BroadcastReceiver:
					if (AndroidEntryPointConstants.getBroadcastLifecycleMethods().contains(sm.getName())) {
						continue;
					}
					break;
				case ContentProvider:
					if (AndroidEntryPointConstants.getContentproviderLifecycleMethods().contains(sm.getName())) {
						continue;
					}
					break;
			}
			if (!isPotentialCallbackMethod(sc, sm.getName())) {
				continue;
			}

			callbacks.add(sm.getSignature());
		}

		callbacks.addAll(getAnonymousCallbacks(sc));

		return callbacks;
	}

	private List<String> getAnonymousCallbacks(SootClass sootClass) {
		List<String> rtVal = new ArrayList<String>();

		try {
			String clsName = sootClass.getName();

			if (clsName.contains("$"))
			{
				return rtVal;
			}

			clsName = String.valueOf(clsName) + "$";

			Chain<SootClass> scs = Scene.v().getClasses();

			for (SootClass sc : scs) {

				if (sc.getName().startsWith(clsName)) {

					List<SootMethod> sms = sc.getMethods();

					for (SootMethod sm : sms) {

						if (sm.getName().contains("<init>")) {
							continue;
						}

						if (isPotentialCallbackMethod(sc, sm.getName()))
						{
							//System.out.println("--------------------------->" + sc.getName() + ":" + sm.getName());
							rtVal.add(sm.getSignature());
						}

					}
				}
			}
		} catch (Exception ex) {

			ex.printStackTrace();
		}

		return rtVal;
	}

	private boolean isPotentialCallbackMethod(SootClass currentClass, String methodName) {
		boolean existCurrentMethod = false;
		List<SootMethod> currentMethods = currentClass.getMethods();
		for (SootMethod method : currentMethods) {

			if (method.getName().equals(methodName)) {

				existCurrentMethod = true;

				break;
			}
		}
		if (!existCurrentMethod)
		{
			throw new RuntimeException(String.valueOf(methodName) + " is not belong to class " + currentClass.getName());
		}

		List<SootClass> extendedClasses = Scene.v().getActiveHierarchy().getSuperclassesOf(currentClass);
		for (SootClass sc : extendedClasses) {

			List<SootMethod> methods = sc.getMethods();
			for (SootMethod method : methods) {

				if (method.getName().equals(methodName))
				{
					return true;
				}
			}
		}

		Chain<SootClass> interfaces = currentClass.getInterfaces();
		for (SootClass i : interfaces) {

			List<SootMethod> methods = i.getMethods();
			for (SootMethod method : methods) {

				if (method.getName().equals(methodName))
				{
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Calculates the set of callback methods declared in the XML resource files or
	 * the app's source code. This method prefers performance over precision and
	 * scans the code including unreachable methods.
	 *
	 * @param lfp        The layout file parser to be used for analyzing UI controls
	 * @param entryPoint The entry point for which to calculate the callbacks. Pass
	 *                   null to calculate callbacks for all entry points.
	 * @throws IOException Thrown if a required configuration cannot be read
	 */
	public void calculateCallbackMethodsFast(LayoutFileParser lfp, SootClass component) throws IOException {
		// Construct the current callgraph
		releaseCallgraph();
		createMainMethod(component);
		constructCallgraphInternal();

		// Get the classes for which to find callbacks
		Set<SootClass> entryPointClasses = getComponentsToAnalyze(component);

		// Collect the callback interfaces implemented in the app's
		// source code
		AbstractCallbackAnalyzer jimpleClass = callbackClasses == null
				? new FastCallbackAnalyzer(config, entryPointClasses, callbackFile)
				: new FastCallbackAnalyzer(config, entryPointClasses, callbackClasses);
		if (valueProvider != null)
			jimpleClass.setValueProvider(valueProvider);
		jimpleClass.collectCallbackMethods();

		// Collect the results
		this.callbackMethods.putAll(jimpleClass.getCallbackMethods());
		this.entrypoints.addAll(jimpleClass.getDynamicManifestComponents());

		// Find the user-defined sources in the layout XML files. This
		// only needs to be done once, but is a Soot phase.
		lfp.parseLayoutFileDirect(config.getAnalysisFileConfig().getTargetAPKFile());

		// Collect the XML-based callback methods
		collectXmlBasedCallbackMethods(lfp, jimpleClass);

		// Construct the final callgraph
		releaseCallgraph();
		createMainMethod(component);
		constructCallgraphInternal();
	}

	/**
	 * Creates the main method based on the current callback information, injects it
	 * into the Soot scene.
	 *
	 * @param component
	 */
	public void createMainMethod(SootClass component) {
		// There is no need to create a main method if we don't want to generate
		// a callgraph
		if (config.getSootIntegrationMode() == InfoflowAndroidConfiguration.SootIntegrationMode.UseExistingCallgraph)
			return;

		// Always update the entry point creator to reflect the newest set
		// of callback methods
		entryPointCreator = createEntryPointCreator(component);
		SootMethod dummyMainMethod = entryPointCreator.createDummyMain();
		Scene.v().setEntryPoints(Collections.singletonList(dummyMainMethod));
		if (!dummyMainMethod.getDeclaringClass().isInScene())
			Scene.v().addClass(dummyMainMethod.getDeclaringClass());

		// addClass() declares the given class as a library class. We need to
		// fix this.
		dummyMainMethod.getDeclaringClass().setApplicationClass();
	}

	/**
	 * Releases the callgraph and all intermediate objects associated with it
	 */
	protected void releaseCallgraph() {
		// If we are configured to use an existing callgraph, we may not release
		// it
		if (config.getSootIntegrationMode() == InfoflowAndroidConfiguration.SootIntegrationMode.UseExistingCallgraph)
			return;

		Scene.v().releaseCallGraph();
		Scene.v().releasePointsToAnalysis();
		Scene.v().releaseReachableMethods();
		G.v().resetSpark();
	}

	/**
	 * Triggers the callgraph construction in Soot
	 */
	public void constructCallgraphInternal() {
		// If we are configured to use an existing callgraph, we may not replace
		// it. However, we must make sure that there really is one.
		if (config.getSootIntegrationMode() == InfoflowAndroidConfiguration.SootIntegrationMode.UseExistingCallgraph) {
			if (!Scene.v().hasCallGraph())
				throw new RuntimeException("FlowDroid is configured to use an existing callgraph, but there is none");
			return;
		}

		// Do we need ICC instrumentation?
		if (config.getIccConfig().isIccEnabled()) {
			if (iccInstrumenter == null)
				iccInstrumenter = createIccInstrumenter();
			iccInstrumenter.onBeforeCallgraphConstruction();
		}

//		// Run the preprocessors
//		for (PreAnalysisHandler handler : this.preprocessors)
//			handler.onBeforeCallgraphConstruction();

		// Make sure that we don't have any weird leftovers
		releaseCallgraph();

		// If we didn't create the Soot instance, we can't be sure what its callgraph
		// configuration is
		if (config.getSootIntegrationMode() == InfoflowAndroidConfiguration.SootIntegrationMode.UseExistingInstance)
			configureCallgraph();

		// Construct the actual callgraph
		logger.info("Constructing the callgraph...");
		PackManager.v().getPack("cg").apply();

		// ICC instrumentation
		if (iccInstrumenter != null)
			iccInstrumenter.onAfterCallgraphConstruction();

//		// Run the preprocessors
//		for (PreAnalysisHandler handler : this.preprocessors)
//			handler.onAfterCallgraphConstruction();

		// Make sure that we have a hierarchy
		Scene.v().getOrMakeFastHierarchy();
	}

	/**
	 * Creates the ICC instrumentation class
	 *
	 * @return An instance of the class for instrumenting the app's code for
	 *         inter-component communication
	 */
	protected IccInstrumenter createIccInstrumenter() {
		IccInstrumenter iccInstrumenter;
		iccInstrumenter = new IccInstrumenter(config.getIccConfig().getIccModel(),
				entryPointCreator.getGeneratedMainMethod().getDeclaringClass(),
				entryPointCreator.getComponentToEntryPointInfo());
		return iccInstrumenter;
	}

	/**
	 * Configures the callgraph options for Soot according to FlowDroid's settings
	 */
	protected void configureCallgraph() {
		// Configure the callgraph algorithm
		switch (config.getCallgraphAlgorithm()) {
			case AutomaticSelection:
			case SPARK:
				Options.v().setPhaseOption("cg.spark", "on");
				break;
			case GEOM:
				Options.v().setPhaseOption("cg.spark", "on");
				AbstractInfoflow.setGeomPtaSpecificOptions();
				break;
			case CHA:
				Options.v().setPhaseOption("cg.cha", "on");
				break;
			case RTA:
				Options.v().setPhaseOption("cg.spark", "on");
				Options.v().setPhaseOption("cg.spark", "rta:true");
				Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
				break;
			case VTA:
				Options.v().setPhaseOption("cg.spark", "on");
				Options.v().setPhaseOption("cg.spark", "vta:true");
				break;
			default:
				throw new RuntimeException("Invalid callgraph algorithm");
		}
		if (config.getEnableReflection())
			Options.v().setPhaseOption("cg", "types-for-invoke:true");
	}

	/**
	 * Collects the XML-based callback methods, e.g., Button.onClick() declared in
	 * layout XML files
	 *
	 * @param lfp         The layout file parser
	 * @param jimpleClass The analysis class that gives us a mapping between layout
	 *                    IDs and components
	 * @return True if at least one new callback method has been added, otherwise
	 *         false
	 */
	private boolean collectXmlBasedCallbackMethods(LayoutFileParser lfp, AbstractCallbackAnalyzer jimpleClass) {
		SootMethod smViewOnClick = Scene.v()
				.grabMethod("<android.view.View$OnClickListener: void onClick(android.view.View)>");

		// Collect the XML-based callback methods
		boolean hasNewCallback = false;
		for (final SootClass callbackClass : jimpleClass.getLayoutClasses().keySet()) {
			if (jimpleClass.isExcludedEntryPoint(callbackClass))
				continue;

			Set<Integer> classIds = jimpleClass.getLayoutClasses().get(callbackClass);
			for (Integer classId : classIds) {
				ARSCFileParser.AbstractResource resource = this.resources.findResource(classId);
				if (resource instanceof ARSCFileParser.StringResource) {
					final String layoutFileName = ((ARSCFileParser.StringResource) resource).getValue();

					// Add the callback methods for the given class
					Set<String> callbackMethods = lfp.getCallbackMethods().get(layoutFileName);
					if (callbackMethods != null) {
						for (String methodName : callbackMethods) {
							final String subSig = "void " + methodName + "(android.view.View)";

							// The callback may be declared directly in the
							// class or in one of the superclasses
							SootClass currentClass = callbackClass;
							while (true) {
								SootMethod callbackMethod = currentClass.getMethodUnsafe(subSig);
								if (callbackMethod != null) {
									if (this.callbackMethods.put(callbackClass,
											new CallbackDefinition(callbackMethod, smViewOnClick, CallbackDefinition.CallbackType.Widget)))
										hasNewCallback = true;
									break;
								}
								SootClass sclass = currentClass.getSuperclassUnsafe();
								if (sclass == null) {
									logger.error(String.format("Callback method %s not found in class %s", methodName,
											callbackClass.getName()));
									break;
								}
								currentClass = sclass;
							}
						}
					}

					// Add the fragments for this class
					Set<SootClass> fragments = lfp.getFragments().get(layoutFileName);

					if(CollectionUtils.isNotEmpty(GlobalRef.dynamicFragment)){
						if(fragments == null){
							fragments = GlobalRef.dynamicFragment;
						}else{
							Set<SootClass> newFragments = new HashSet<>();
							newFragments.addAll(fragments);
							newFragments.addAll(GlobalRef.dynamicFragment);
							fragments = newFragments;
						}
					}

//					if (fragments != null){
//						System.out.println("This App Has Fragment, Fragment size="+fragments.size());
//					}
					if (fragments != null)
						for (SootClass fragment : fragments)
							if (fragmentClasses.put(callbackClass, fragment))
								hasNewCallback = true;

					// For user-defined views, we need to emulate their
					// callbacks
					Set<AndroidLayoutControl> controls = lfp.getUserControls().get(layoutFileName);
					if (controls != null) {
						for (AndroidLayoutControl lc : controls)
							if (!SystemClassHandler.isClassInSystemPackage(lc.getViewClass().getName()))
								registerCallbackMethodsForView(callbackClass, lc);
					}
				} else
					logger.error("Unexpected resource type for layout class");
			}
		}

		// Collect the fragments, merge the fragments created in the code with
		// those declared in Xml files
		if (fragmentClasses.putAll(jimpleClass.getFragmentClasses())) // Fragments
			// declared
			// in
			// code
			hasNewCallback = true;

		return hasNewCallback;
	}

	/**
	 * Registers the callback methods in the given layout control so that they are
	 * included in the dummy main method
	 *
	 * @param callbackClass The class with which to associate the layout callbacks
	 * @param lc            The layout control whose callbacks are to be associated
	 *                      with the given class
	 */
	private void registerCallbackMethodsForView(SootClass callbackClass, AndroidLayoutControl lc) {
		// Ignore system classes
		if (SystemClassHandler.isClassInSystemPackage(callbackClass.getName()))
			return;

		// Get common Android classes
		if (scView == null)
			scView = Scene.v().getSootClass("android.view.View");

		// Check whether the current class is actually a view
		if (!Scene.v().getOrMakeFastHierarchy().canStoreType(lc.getViewClass().getType(), scView.getType()))
			return;

		// There are also some classes that implement interesting callback
		// methods.
		// We edu.monash.model this as follows: Whenever the user overwrites a method in an
		// Android OS class, we treat it as a potential callback.
		SootClass sc = lc.getViewClass();
		Map<String, SootMethod> systemMethods = new HashMap<>(10000);
		for (SootClass parentClass : Scene.v().getActiveHierarchy().getSuperclassesOf(sc)) {
			if (parentClass.getName().startsWith("android."))
				for (SootMethod sm : parentClass.getMethods())
					if (!sm.isConstructor())
						systemMethods.put(sm.getSubSignature(), sm);
		}

		// Scan for methods that overwrite parent class methods
		for (SootMethod sm : sc.getMethods()) {
			if (!sm.isConstructor()) {
				SootMethod parentMethod = systemMethods.get(sm.getSubSignature());
				if (parentMethod != null)
					// This is a real callback method
					this.callbackMethods.put(callbackClass,
							new CallbackDefinition(sm, parentMethod, CallbackDefinition.CallbackType.Widget));
			}
		}
	}

	/**
	 * Inverts the given {@link MultiMap}. The keys become values and vice versa
	 *
	 * @param original The map to invert
	 * @return An inverted copy of the given map
	 */
	private <K, V> MultiMap<K, V> invertMap(MultiMap<V, K> original) {
		MultiMap<K, V> newTag = new HashMultiMap<>();
		for (V key : original.keySet())
			for (K value : original.get(key))
				newTag.put(value, key);
		return newTag;
	}

	/**
	 * Creates a new layout file parser. Derived classes can override this method to
	 * supply their own parser.
	 *
	 * @return The newly created layout file parser.
	 */
	public LayoutFileParser createLayoutFileParser() {
		return new LayoutFileParser(this.manifest.getPackageName(), this.resources);
	}

}
