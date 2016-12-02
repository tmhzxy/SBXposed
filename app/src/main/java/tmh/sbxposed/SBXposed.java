package tmh.sbxposed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;

import dalvik.system.DexFile;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class SBXposed implements IXposedHookLoadPackage {

    private static ArrayList<String> foundClasses = new ArrayList<String>();

    private static ArrayList<String> getFoundClasses(String sourceDir) {
        if (foundClasses.size() == 0) {
            log("Try to find protect classes...");
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
                log("EXCEPTION - START");
                XposedBridge.log(e);
                log("EXCEPTION - END");
            } finally {
                log(foundClasses.size() + " classes found.");
            }
        }

        return foundClasses;
    }

    private static void log(String message) {
        log(message, false);
    }

    private static void log(String message, boolean debug) {
        if (debug && !BuildConfig.DEBUG) {
            return;
        }

        XposedBridge.log("SBXposed: " + message);
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam packageParam) throws Throwable {
        if (packageParam.packageName.equals("com.starfinanz.smob.android.sbanking") ||
            packageParam.packageName.equals("com.starfinanz.smob.android.sfinanzstatus") ||
            packageParam.packageName.equals("com.starfinanz.smob.android.sfinanzstatus.tablet")) {
            log("Supported app found: " + packageParam.packageName + " [" + packageParam.processName + "]");
            for (String foundClassName : getFoundClasses(packageParam.appInfo.sourceDir)) {
                Class foundClass = findClass(foundClassName, packageParam.classLoader);
                if (foundClass != null) {
                    boolean hookableMethodFound = false;
                    for (Method method : foundClass.getMethods()) {
                        if (method.getParameterTypes().length == 0 &&
                            method.getReturnType() == boolean.class) {
                            hookableMethodFound = true;
                            log("Hook " + foundClassName + "->" + method.getName(), true);
                            findAndHookMethod(foundClassName, packageParam.classLoader, method.getName(), new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    log(param.method.getName() + " called.", true);
                                    param.setResult(true);
                                }
                            });
                        }
                    }
                    if (!hookableMethodFound) {
                        log("Could not hook any method in class: " + foundClassName);
                    }
                } else {
                    log("Could not get class for: " + foundClassName);
                }
            }
        }
    }
}