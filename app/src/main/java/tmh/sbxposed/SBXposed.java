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
            XposedBridge.log("SBXposed: Try to find protect classes...");
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
                XposedBridge.log(e);
            } finally {
                XposedBridge.log("SBXposed: " + foundClasses.size() + " classes found.");
            }
        }

        return foundClasses;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam packageParam) throws Throwable {
        if (packageParam.packageName.equals("com.starfinanz.smob.android.sbanking") ||
            packageParam.packageName.equals("com.starfinanz.smob.android.sfinanzstatus")) {
            for (String foundClassName : getFoundClasses(packageParam.appInfo.sourceDir)) {
                Class foundClass = findClass(foundClassName, packageParam.classLoader);
                if (foundClass != null) {
                    boolean hookableMethodFound = false;
                    for (Method method : foundClass.getMethods()) {
                        if (method.getParameterTypes().length == 0 &&
                            method.getReturnType() == boolean.class) {
                            hookableMethodFound = true;
                            findAndHookMethod(foundClassName, packageParam.classLoader, method.getName(), new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    param.setResult(true);
                                }
                            });
                        }
                    }
                    if (!hookableMethodFound) {
                        XposedBridge.log("SBXposed: Could not hook any method in class: " + foundClassName);
                    }
                } else {
                    XposedBridge.log("SBXposed: Could not get class for: " + foundClassName);
                }
            }
        }
    }
}