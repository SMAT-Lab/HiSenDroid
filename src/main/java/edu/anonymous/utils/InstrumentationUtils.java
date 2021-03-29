package edu.anonymous.utils;

import edu.anonymous.model.ReflectionProfile;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstrumentationUtils 
{
	public static void main(String[] args)
	{
		String str = "<com.allen.cc.contact.OutgoingContactList: void <init>()>()";
		
		System.out.println(toMethodName(str));
	}
	
	/**
	 * This not only works for method.invoke() but also works for field accessing.
	 * 
	 * @param stmt
	 * @return
	 */
	public static boolean isStaticReflectiveInvocation(Stmt stmt)
	{
		if (! stmt.containsInvokeExpr())
		{
			return false;
		}
		
		if (stmt.getInvokeExpr().getArgCount() > 0)
		{
			Value arg0 = stmt.getInvokeExpr().getArg(0);
			
			if (null == arg0 || "null".equals(arg0.toString()))
			{
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Return all @sootClass's methods that call @name
	 * If there is no method exist, do not return null but an empty @List.
	 * 
	 * @param sootClass
	 * @param name
	 * @return
	 */
	public static List<SootMethod> getMethodByName(SootClass sootClass, String name)
	{
		List<SootMethod> sootMethods = new ArrayList<SootMethod>();
		
		for (SootMethod sootMethod : sootClass.getMethods())
		{
			if (name.equals(sootMethod.getName()))
			{
				sootMethods.add(sootMethod);
			}
		}
		
		return sootMethods;
	}
	
	public static Type toPrimitiveWrapperType(Type sootType)
	{
		Type rtType = null;
		
		String type = sootType.toString();
		
		if ("boolean".equals(type))
		{
			rtType = RefType.v("java.lang.Boolean");
		}
		else if ("byte".equals(type))
		{
			rtType = RefType.v("java.lang.Byte");
		}
		else if ("char".equals(type))
		{
			rtType = RefType.v("java.lang.Char");
		}
		else if ("short".equals(type))
		{
			rtType = RefType.v("java.lang.Short");
		}
		else if ("int".equals(type))
		{
			rtType = RefType.v("java.lang.Integer");
		}
		else if ("long".equals(type))
		{
			rtType = RefType.v("java.lang.Long");
		}
		else if ("float".equals(type))
		{
			rtType = RefType.v("java.lang.Float");
		}
		else if ("double".equals(type))
		{
			rtType = RefType.v("java.lang.Double");
		}
		else
		{
			rtType = RefType.v("java.lang.Object");
		}
		
		return rtType;
	}
	
	public static Value toDefaultSootTypeValue(Type sootType)
	{
		String type = sootType.toString();
		
		if ("boolean".equals(type))
		{
			IntConstant.v(0);
		}
		else if ("byte".equals(type))
		{
			return IntConstant.v(0);
		}
		else if ("char".equals(type))
		{
			return IntConstant.v(0);
		}
		else if ("short".equals(type))
		{
			return IntConstant.v(0);
		}
		else if ("int".equals(type))
		{
			return IntConstant.v(0);
		}
		else if ("long".equals(type))
		{
			return LongConstant.v(0);
		}
		else if ("float".equals(type))
		{
			return FloatConstant.v(0);
		}
		else if ("double".equals(type))
		{
			return DoubleConstant.v(0);
		}

		return NullConstant.v();
	}
	
	public static Type toSootTypeByName(String type)
	{
		if ("boolean".equals(type))
		{
			return BooleanType.v();
		}
		else if ("byte".equals(type))
		{
			return ByteType.v();
		}
		else if ("char".equals(type))
		{
			return CharType.v();
		}
		else if ("short".equals(type))
		{
			return ShortType.v();
		}
		else if ("int".equals(type))
		{
			return IntType.v();
		}
		else if ("long".equals(type))
		{
			return LongType.v();
		}
		else if ("float".equals(type))
		{
			return FloatType.v();
		}
		else if ("double".equals(type))
		{
			return DoubleType.v();
		}

		return RefType.v(type);
	}
	
	public static String toMethodName(String methodSignature)
	{
		int startPos = methodSignature.indexOf(":") + 1;
		int endPos = methodSignature.indexOf("(");
		
		String tmp = methodSignature.substring(startPos, endPos).trim();
		
		return tmp.split(" ")[1];
	}
	
	
	public static void mockSootClasses(Map<String, ReflectionProfile.RClass> rClasses)
	{
		for (Map.Entry<String, ReflectionProfile.RClass> entry : rClasses.entrySet())
		{
			String clsName = entry.getKey();
			ReflectionProfile.RClass rClass = entry.getValue();
			
			if (! rClass.exist)
			{
				Mocker.mockSootClass(clsName);
			}
		}
	}
}
