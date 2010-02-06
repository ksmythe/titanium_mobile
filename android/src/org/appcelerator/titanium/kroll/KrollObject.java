/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.kroll;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiDict;
import org.appcelerator.titanium.TiProxy;
import org.appcelerator.titanium.kroll.KrollMethod.KrollMethodType;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class KrollObject extends ScriptableObject
{
	private static final String LCAT = "KrollObject";
	private static final boolean DBG = TiConfig.LOGD && false; //TODO remove force false

	private static final long serialVersionUID = 1L;

	protected WeakReference<KrollContext> weakKrollContext;
	protected WeakReference<TiApplication> weakApplication;
	protected Object target;

	public KrollObject(KrollContext kroll) {
		this(kroll, null);
	}

	public KrollObject(KrollContext kroll, Object target)
	{
		super(kroll.getScope(), null);
		weakKrollContext = new WeakReference<KrollContext>(kroll);
		this.target = target;
		initApplication(kroll);
	}

	public KrollObject(KrollObject parent, Object target)
	{
		super(parent, null);
		weakKrollContext = new WeakReference<KrollContext>(parent.getKrollContext());
		this.target = target;
		initApplication(parent.getKrollContext());
	}

	private void initApplication(KrollContext kroll) {
		if (kroll != null) {
			TiContext tiContext = kroll.getTiContext();
			if (tiContext != null) {
				weakApplication = new WeakReference<TiApplication>((TiApplication) tiContext.getActivity().getApplication());
			}
		}
	}

	@Override
	public String getClassName() {
		return "KrollObject";
	}

	public Object getTarget() {
		return target;
	}

	public KrollContext getKrollContext() {
		return weakKrollContext.get();
	}

	protected String getModulePath() {
		KrollContext kroll = weakKrollContext.get();
		Scriptable scope = kroll.getScope();

		Log.e(LCAT, "getModulePath(): " + getClassName());
		if (target != null) {
			Log.e(LCAT, "targetPath: " + target.getClass().getPackage().getName());
			return target.getClass().getPackage().getName() + ".";
		}

		if (scope.getParentScope() == null) {
			return "ti.modules." + getClassName().toLowerCase() + ".";
		} else {
			return ((KrollObject) scope.getParentScope()).getModulePath() +
				getClassName().toLowerCase() + ".";
		}
	}

	@Override
	public Object get(String name, Scriptable start)
	{
		if (this.has(name, start)) {
			return super.get(name, start);
		}
		if (target instanceof TiProxy) {
			TiDict constants = ((TiProxy) target).getConstants();
			if (constants != null) {
				if (constants.containsKey(name)) {
					Object value = constants.get(name);
					super.putConst(name, start, value);
					return value;
				}
			}
		}

		if (DBG) {
			Log.d(LCAT, "get name: " + name);
		}
		Object o = NOT_FOUND;

		// If starts with Capital letter see if there is a module for it.
		if (name.matches("^[A-Z].*") || name.matches("iPhone")) {
			Object p = loadModule(name);
			if (p != null) {
				o = new KrollObject(this, p);
				put(name, this, o);
			}
		} else {
			if (DBG) {
				Log.d(LCAT, "Start: " + start.getClassName() + " looking for method:" + name);
			}
			o = handleMethodOrProperty(name, start, true, null);
		}

		return o;
	}

	@Override
	public Object getDefaultValue(Class<?> typeHint) {
		//return super.getDefaultValue(typeHint);
		if (typeHint == null || typeHint == String.class) {
			if (target != null) {
				return target.toString();
			}
		} else if (typeHint == Number.class) {
			if (target != null) {
				return (Number) target;
			}
		} else if (typeHint == Boolean.class) {
			if (target != null) {
				return (Boolean) target;
			}
		} else if (typeHint == Scriptable.class) {
			return toString();
		}

		return super.getDefaultValue(typeHint);
	}

	private Object handleMethodOrProperty(String name, Scriptable start, boolean retrieveValue, Object value)
	{
		Object o = NOT_FOUND;

		String pname = name;

//		if (name.equals("valueOf") || name.equals("toString")) {
//			KrollMethod km = new KrollMethod(this, value, null, KrollMethodType.KrollMethodInvoke);
//			put(name, this, km);
//			o = value;
//		}

		if(name.startsWith("get") || name.startsWith("set")) {
			pname = name.substring(3,4);
			pname = pname.toLowerCase();
			if (name.length() > 4) {
				pname += name.substring(4);
			}
		}

		Method getMethod = (Method) loadMethod(target.getClass(), buildMethodName("get", pname));
		Method setMethod = (Method) loadMethod(target.getClass(), buildMethodName("set", pname));
		boolean isGetter = getMethod != null && getMethod.getParameterTypes().length == 0;
		boolean isSetter = setMethod != null && setMethod.getParameterTypes().length == 1;

		if (isGetter || isSetter) {
			Log.i(LCAT, "Treating as property: " + pname);
			if (getMethod != null) {
				// add getter
				KrollMethod km = null;

				Method propertyMethod = (Method) loadMethod(target.getClass(), pname);
				if (propertyMethod != null) {
					km = new KrollMethod(this, target, propertyMethod, KrollMethodType.KrollMethodGetter);
					put(pname, this, km);
					retrieveValue = false;
					o = km;
				} else {
					km  = new KrollMethod(this, target, getMethod, KrollMethodType.KrollMethodPropertyGetter);
					setGetterOrSetter(pname, 0, km, false);
				}

				// add get method
				km = new KrollMethod(this, target, getMethod, KrollMethodType.KrollMethodGetter);
				put(buildMethodName("get", pname), this, km);

				if(retrieveValue && pname.equals(name)) {
					try {
						// get value from native
						o = getMethod.invoke(target, new Object[0]);
					} catch (InvocationTargetException e) {
						Log.e(LCAT, "Error getting property: " + e.getMessage(), e);
						Context.throwAsScriptRuntimeEx(e);
					}catch (IllegalAccessException e) {
						Log.e(LCAT, "Error getting property: " + e.getMessage(), e);
						Context.throwAsScriptRuntimeEx(e);
					}
				} else {
					o = km;
				}
			}

			if (setMethod != null) {
				// add setter
				KrollMethod km  = new KrollMethod(this, target, setMethod, KrollMethodType.KrollMethodPropertySetter);
				setGetterOrSetter(pname, 0, km, true);

				// add set method
				km = new KrollMethod(this, target, setMethod, KrollMethodType.KrollMethodSetter);
				put(buildMethodName("set", pname), this, km);

				// pass value through to native
				if (!retrieveValue) {
					Object[] args = new Object[1];
					args[0] = value;

					try {
						setMethod.invoke(target, argsForMethod(setMethod, args));
					} catch (InvocationTargetException e) {
						Log.e(LCAT, "Error setting property: " + e.getMessage(), e);
						Context.throwAsScriptRuntimeEx(e);
					}catch (IllegalAccessException e) {
						Log.e(LCAT, "Error setting property: " + e.getMessage(), e);
						Context.throwAsScriptRuntimeEx(e);
					}
				}
			}
		} else {
			// See if the method exists
			Method m = (Method) loadMethod(target.getClass(), name);
			if (m != null) {
				o = new KrollMethod(this, target, m, KrollMethodType.KrollMethodInvoke);
				put(name, this, o);
			} else if (name.startsWith("create")) {
				// Check for dynamic proxy.
				m = (Method) loadMethod(target.getClass(), "createProxy");
				if (m != null) {
					o = new KrollMethod(this, target, m, KrollMethodType.KrollMethodFactory, name);
					put(name, this, o);
				}
			} else {
				o = handleDynamicProperty(name, pname, start, retrieveValue, value);
			}
		}

		return o;
	}

	private Object handleDynamicProperty(String name, String pname, Scriptable start, boolean retrieveValue, Object value)
	{
		// dynamic property
		Object o = null;

		Method getMethod = (Method) loadMethod(target.getClass(), "getDynamicValue");
		Method setMethod = (Method) loadMethod(target.getClass(), "setDynamicValue");

		Log.i(LCAT, "Treating as dynamic property: " + name);
		// add getter
		KrollMethod getterKm  = new KrollMethod(this, target, getMethod, KrollMethodType.KrollMethodDynamic, pname);
		setGetterOrSetter(pname, 0, getterKm, false);
		put(buildMethodName("get", pname), this, getterKm);

		if(name.equals(pname) && retrieveValue) {
			try {
				// get value from native
				Object[] args = new Object[1];
				args[0] = name;

				o = getMethod.invoke(target, args);
			} catch (InvocationTargetException e) {
				Log.e(LCAT, "Error getting property: " + e.getMessage(), e);
				Context.throwAsScriptRuntimeEx(e);
			}catch (IllegalAccessException e) {
				Log.e(LCAT, "Error getting property: " + e.getMessage(), e);
				Context.throwAsScriptRuntimeEx(e);
			}
		}

		// add setter
		KrollMethod setterKm  = new KrollMethod(this, target, setMethod, KrollMethodType.KrollMethodDynamic, pname);
		setGetterOrSetter(pname, 0, setterKm, true);
		put(buildMethodName("set", pname), this, setterKm);

		// pass value through to native
		if (name.equals(pname) && !retrieveValue) {
			Object[] args = new Object[2];
			args[0] = name;
			args[1] = value;

			try {
				setMethod.invoke(target, argsForMethod(setMethod, args));
			} catch (InvocationTargetException e) {
				Log.e(LCAT, "Error setting property: " + e.getMessage(), e);
				Context.throwAsScriptRuntimeEx(e);
			}catch (IllegalAccessException e) {
				Log.e(LCAT, "Error setting property: " + e.getMessage(), e);
				Context.throwAsScriptRuntimeEx(e);
			}
		}

		if (o == null) {
			if (name.startsWith("get")) {
				o = getterKm;
			} else if (name.startsWith("set")) {
				o = setterKm;
			}
		}

		return o;
	}

	@Override
	public void put(int arg0, Scriptable arg1, Object arg2) {
		// TODO Auto-generated method stub
		super.put(arg0, arg1, arg2);
		Log.w(LCAT, "Put[]");
	}

	public void superPut(String name, Scriptable start, Object value) {
		super.put(name, start, value);
	}

	@Override
	public void put(String name, Scriptable start, Object value) {
		if (has(name, start) || (value != null && value instanceof KrollObject)) {
			super.put(name, start, value);
		} else {
			handleMethodOrProperty(name, start, false, value);
		}
	}

	@Override
	protected boolean isGetterOrSetter(String arg0, int arg1, boolean arg2) {
		return super.isGetterOrSetter(arg0, arg1, arg2);
	}

	// Module Support
	private String createModuleName(String name) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(getModulePath());
		if (!name.toLowerCase().equals("titanium")) {
			sb.append(name.toLowerCase())
			.append(".");
		}
		sb.append(name)
			.append("Module");

		return sb.toString();
	}

	protected Object loadModule(String name)
	{
		Object p = null;

		String moduleName = createModuleName(name);
		Log.d(LCAT, "Module: " + moduleName);

		try {
			Class<?> c = Class.forName(moduleName);
			if (c != null) {

				Constructor<?>[] ctors = c.getConstructors();
				if (ctors.length == 1) {
					Constructor<?> ctor = ctors[0];
					Class<?>[] types = ctor.getParameterTypes();
					if (types.length == 0) {
						p = ctor.newInstance();
					} else if (types.length == 1) {
						p = ctor.newInstance(weakKrollContext.get().getTiContext());
					} else {
						Log.e(LCAT, "No valid constructor found.");
					}
				} else {
					Log.w(LCAT, "Modules currently requires only one contructor in a module.");
				}
			}
		} catch (Exception e) {
			Log.e(LCAT, "No Module for name " + name + " expected " + moduleName);
		}

		return p;
	}

	// Method support

	private Object loadMethod(Class<?> source, String name)
	{
		Object o = null;
		TiApplication tiApp = weakApplication.get();
		if(tiApp != null) {
				o = tiApp.methodFor(source, name);
		}

		return o;
	}

	private String buildMethodName(String prefix, String name) {
		String pname = prefix + name.substring(0, 1).toUpperCase();

		if (name.length() > 1) {
			pname += name.substring(1);
		}

		return pname;
	}

	// Type Conversion support

	protected Object[] argsForMethod(Method method, Object[] args) {
		Class<?>[] types = method.getParameterTypes();
		Object []newArgs = null;
		Object []varArgs = null;
		boolean varargs = false;

		if (DBG) {
			Log.d(LCAT, "Method: " + method.getName() + " Types: " + types.length + " Args: " + (args != null ? args.length : 0) + " varargs: " + varargs);
		}

		if (args != null && types.length != 0 && args.length >= types.length) {
			if (types[types.length - 1].isArray()) {
				Object o = args[types.length-1];
				if (!(o instanceof Scriptable) || (o instanceof Scriptable && !isArrayLike((Scriptable) o))) {
					varargs = true;
					varArgs = new Object[args.length - types.length + 1];
				}
			}
		}

		if (DBG) {
			Log.d(LCAT, "Method: " + method.getName() + " varargs: " + varargs);
		}

		newArgs = new Object[types.length];

		if (varargs) {
			int len = types.length - 1;
			for (int i = 0; i < len; i++) {
				newArgs[i] = toNative(args[i], types[i]);
			}
			newArgs[len] = varArgs;
			for (int i = len; i < args.length; i++) {
				varArgs[i-len] = toNative(args[i], Object.class);
			}
		} else {
			for (int i = 0; i < types.length; i++) {
				//TODO type coercion based on method requirements.

				if (i < args.length) {
					newArgs[i] = toNative(args[i], types[i]);
					if (DBG) {
						Log.d(LCAT, "Source type: " + (args[i] != null ? args[i].getClass().getName() : "null") + " Dest : " + types[i].getName());
					}
				} else {
					newArgs[i] = null;
					if (DBG) {
						Log.d(LCAT, "Source type: missing" + " Dest : " + types[i].getName());
					}
				}
			}
		}
		return newArgs;
	}

	protected Object toNative(Object value, Class<?> target)
	{
		Object o = null;

		if (value instanceof String) {
			o = Context.jsToJava(value, target);
		} else if (value instanceof Double || value instanceof Integer) {
			o = Context.jsToJava(value, target);
		} else if (value instanceof Boolean) {
			o = Context.jsToJava(value, target);
		} else if (value instanceof Function) {
			if (DBG) {
				Log.i(LCAT, "Is a Function");
			}
			o = new KrollCallback(weakKrollContext.get(), this, (Function) value);
		} else if (value == null) {
			o = null;
		} else if (value instanceof Scriptable) {
			Scriptable svalue = (Scriptable) value;
			if (isArrayLike(svalue)) {
				o = toArray(svalue);
			} else if (value instanceof KrollObject) {
				o = ((KrollObject) value).target;
			} else if (svalue.getClassName().equals("Date")) {
				double time = (Double) ScriptableObject.callMethod(svalue, "getTime", new Object[0]);
				o = new Date((long)time);
			} else {
				TiDict args = new TiDict();
				o = args;

				Scriptable so = (Scriptable) value;
				for(Object key : so.getIds()) {
					Object v = so.get((String) key, so);
					v = toNative(v, Object.class);
//					if (v instanceof Scriptable && isArrayLike((Scriptable) v)) {
//						v = toArray((Scriptable) v);
//					}
					if (DBG) {
						Log.i(LCAT, "Key: " + key + " value: " + v + " type: " + v.getClass().getName());
					}
					args.put((String) key, v);
				}
				//Log.w(LCAT, "Unhandled type conversion of Scriptable: value: " + value.toString() + " type: " + value.getClass().getName());
			}
		} else {
			if (value.getClass().isArray()) {
				Object[] values = (Object[]) value;
				Object[] newValues = new Object[values.length];
				for(int i = 0; i < values.length; i++) {
					newValues[i] = toNative(values[i], Object.class);
				}
				o = newValues;
			} else {
				Log.w(LCAT, "Unhandled type conversion: value: " + value.toString() + " type: " + value.getClass().getName());
			}
		}

		return o;
	}

	private boolean isArrayLike(Scriptable svalue) {
		return svalue.has("length", svalue);
	}

	private Object[] toArray(Scriptable svalue)
	{
		int len = (Integer) Context.jsToJava(svalue.get("length", this), Integer.class);
		Object[] a = new Object[len];
		for(int i = 0; i < len; i++) {
			Object v = svalue.get(i, svalue);
			Log.i(LCAT, "Index: " + i + " value: " + v + " type: " + v.getClass().getName());
			a[i] = v;
		}
		return a;
	}

	@SuppressWarnings("serial")
	public static Object fromNative(Object value, KrollContext kroll)
	{
		Object o = value;

		if (DBG) {
			if (value != null) {
				Log.d(LCAT, "Incoming type is " + value.getClass().getCanonicalName());
			}
		}
		if (value == null || value instanceof String ||
				value instanceof Double ||
				value instanceof Boolean ||
				value instanceof Integer ||
				value instanceof Long ||
				value instanceof Float ||
				value instanceof Scriptable || value instanceof Function)
		{
			o = Context.javaToJS(value, kroll.getScope());
		} else if (value instanceof TiDict) {
			TiDict d = (TiDict) value;
			ScriptableObject so = new ScriptableObject(kroll.getScope(), ScriptableObject.getObjectPrototype(kroll.getScope())) {
				@Override
				public String getClassName() {
					return "Object";
				}

				public String toString()
				{
					StringBuilder sb = new StringBuilder();
					sb.append("{ ");

					String [] ids = (String[]) getIds();
					String sep = "";

					if (ids != null) {
						for(String id : ids) {
							sb.append(" '").append(id).append("' : ");
							Object o = get(id, this);
							if (o == null) {
								sb.append("null");
							} else if (o instanceof String) {
								sb.append(" '").append((String)o).append("' ");
							} else if (o instanceof Number) {
								sb.append(o);
							} else if (o instanceof ScriptableObject) {
								sb.append( " {").append(o).append("} ");
							} else {
								sb.append(o);
							}

							sb.append("sep");
							sep = ",";
						}
					}

					sb.append(" }");

					return sb.toString();
				}
			};
			for(String key : d.keySet()) {
				so.put(key, so, fromNative(d.get(key), kroll));
			}
			o = so;
		} else if (value instanceof Date) {
			Date date = (Date) value;
			o = Context.getCurrentContext().newObject(kroll.getScope(), "Date", new Object[] { date.getTime() });
		} else {
			o = new KrollObject(kroll, value);
		}

		return o;
	}
}