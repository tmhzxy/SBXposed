package tmh.sbxposed;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;

import dalvik.system.DexFile;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class SBXposed implements IXposedHookLoadPackage {

    private static boolean DEBUG = BuildConfig.DEBUG;

    private static ArrayList<String> foundClasses = new ArrayList<String>();

    private static ArrayList<String> getFoundClasses(String sourceDir) {
        if (foundClasses.size() == 0) {
            log("Try to find protect classes...", true);
            try {
                DexFile df = new DexFile(sourceDir);
                for (Enumeration<String> iteration = df.entries(); iteration.hasMoreElements(); ) {
                    String file = iteration.nextElement();
                    if (file.startsWith("com.starfinanz.mobile.android.protect.")) {
                        foundClasses.add(file);
                    }
                }
                df.close();
            } catch (Exception e) {
                log("EXCEPTION - START", true);
                XposedBridge.log(e);
                log("EXCEPTION - END", true);
            } finally {
                log(foundClasses.size() + " classes found.", true);
            }
        }

        return foundClasses;
    }

    private static void log(String message) {
        log(message, false);
    }

    private static void log(String message, boolean debug) {
        if (debug && !DEBUG) {
            return;
        }

        XposedBridge.log(String.format(
                "%s%s %s",
                SBXposed.class.getName(),
                debug ? " [DEBUG] " : ":",
                message
        ));
    }

    private static void logThrowable(Throwable throwable) {
        log("Throwable \"" + throwable.getClass() + "\": " + throwable.getMessage());
        XposedBridge.log(throwable);
    }

    private static void logStacktrace(StackTraceElement[] stackTraceElements) {
        logStacktrace(stackTraceElements, 4);
    }

    private static void logStacktrace(StackTraceElement[] stackTraceElements, int ignore) {
        for (StackTraceElement ste : stackTraceElements) {
            if (ignore == 0) {
                log(ste.getClassName() + "->" + ste.getMethodName() + " : " + ste.getLineNumber(), true);
            } else {
                ignore -= 1;
            }
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam packageParam) throws Throwable {
        if (packageParam.packageName.equals("com.starfinanz.smob.android.sbanking") ||
                packageParam.packageName.equals("com.starfinanz.smob.android.sfinanzstatus") ||
                packageParam.packageName.equals("com.starfinanz.smob.android.sfinanzstatus.tablet")) {
            log("Supported app found: " + packageParam.packageName + " [" + packageParam.processName + "]");

            if (DEBUG) {
                // To find apt class...
                findAndHookMethod("com.starfinanz.mobile.android.base.app.SFApplication", packageParam.classLoader, "getApt", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object apt = param.getResult();
                        log("APT: " + apt.getClass().getPackage() + "." + apt.getClass().getName(), true);
                    }
                });
            }

            boolean hookableMethodFound = false;
            for (String foundClassName : getFoundClasses(packageParam.appInfo.sourceDir)) {
                Class foundClass = findClass(foundClassName, packageParam.classLoader);
                if (foundClass != null) {
                    for (Method method : foundClass.getMethods()) {
                        if (method.getParameterTypes().length == 0 &&
                                method.getReturnType() == boolean.class) {
                            hookableMethodFound = true;
                            log("Hook " + foundClassName + "->" + method.getName());
                            findAndHookMethod(foundClassName, packageParam.classLoader, method.getName(), new XC_MethodReplacement() {
                                @Override
                                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                                    log(methodHookParam.method.getName() + " called" + (DEBUG ? " -> return true" : "."));
                                    return true;
                                }
                            });
                        }
                        if (method.getParameterTypes().length == 0 &&
                                method.getReturnType() == String.class &&
                                !method.getName().equals("toString")) {
                            findAndHookMethod(foundClassName, packageParam.classLoader, method.getName(), new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    String result = param.getResult().toString();
                                    log(param.method.getName() + "called" + (DEBUG ? " -> return \"" + result + "\"" : "."));
                                }
                            });
                        }
                        /*
                        // Maybe needed... jni call with context...
                        if (method.getParameterTypes().length == 1 &&
                            method.getParameterTypes()[0] == Context.class &&
                            method.getReturnType() == void.class) {
                            findAndHookMethod(foundClassName, packageParam.classLoader, method.getName(), Context.class, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    log(param.method.getName() + " called. [CONTEXT]");
                                }
                            });
                        }
                        */
                        if (method.getParameterTypes().length == 1 &&
                                method.getParameterTypes()[0] == String.class &&
                                method.getReturnType() == String.class) {
                            log("Hook " + foundClassName + "->" + method.getName());
                            findAndHookMethod(foundClassName, packageParam.classLoader, method.getName(), String.class, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    String arg = param.args[0].toString();
                                    String result = param.getResult() == null ? "null" : param.getResult().toString();
                                    // Only uncomment if needed! Performance issue...
                                    // log(param.method.getName() + " called" + (DEBUG ? "('" + param.args[0].toString() + "') -> '" + result + "'" : "."), true);
                                    if (param.hasThrowable()) {
                                        logThrowable(param.getThrowable());
                                        param.setThrowable(null);
                                    }
                                    if (result.contains("joa@starfinanz.de")) {
                                        log("     -> new result: null", true);
                                        param.setResult("thisisannonexistentmail@example.com");
                                    }

                                    if (param.getResult() == null) {
                                        log("ALLOW: " + arg);
                                        param.setResult("*");
                                        logStacktrace(Thread.currentThread().getStackTrace(), 0);
                                    }

                                    if (result.equals("Error_780301") ||
                                            result.contains("Error") ||
                                            result.contains("780301") ||
                                            result.contains("Crashlog") ||
                                            result.contains("@starfinanz.de") ||
                                            result.contains("Error in doInBackground") ||
                                            result.equals("su") ||
                                            result.equals("/system/app/Superuser.apk")) {
                                        logStacktrace(Thread.currentThread().getStackTrace());
                                    }
                                }
                            });
                        }
                    }
                    if (!hookableMethodFound) {
                        log("Could not hook any method in class: " + foundClassName, true);
                    }
                } else {
                    log("Could not get class for: " + foundClassName);
                }
            }

            if (!hookableMethodFound) {
                log("Nothing found :( Module would not work...");
            } else {
                log("Patch other classes...", true);
                findAndHookMethod("ijjjijillllji", packageParam.classLoader, "jlilijjjjilil", Context.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        log("Fake root check.", true);
                        param.setResult(true);
                    }
                });

                findAndHookMethod("ijjjjiiljljil", packageParam.classLoader, "ijjjiiiljiiji", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        return null;
                    }
                });

                findAndHookMethod("illlillilllij", packageParam.classLoader, "uncaughtException", Thread.class, Throwable.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        log("Uncaught exception :|");
                        Throwable throwable = (Throwable) param.args[1];

                        Class<?> allowedThrowable = findClass("ijijliilljjlj", packageParam.classLoader);
                        if (allowedThrowable == null) {
                            log("Allowed exception now found. :(  -> Abort all! Some things wont work...");
                            param.setResult(null);
                        } else {
                            logThrowable(throwable);
                            if (throwable.getClass() != allowedThrowable) {
                                log("Non allowed exception (" + throwable.getClass() + ": \"" + throwable.getMessage() + "\"). Abort execution (Privacy issue -> always sends report :( )");
                                param.setResult(null);
                            }
                        }
                    }
                });

                String[] param = new String[0];
                findAndHookMethod("iiiljiljjjiji$ijjlliijlillj", packageParam.classLoader, "ijjlliijlillj", param.getClass(), new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        log("Before AsyncCheck", true);
                        /* Cannot return result here, call is needed for activity... */
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        log("After AsyncCheck", true);
                        param.setResult(true);
                    }
                });
            }
        }
    }
}