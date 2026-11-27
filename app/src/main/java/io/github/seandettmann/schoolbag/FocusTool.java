package io.github.seandettmann.schoolbag;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Gives the app on top of one screen Android's focus, and with it the
 * controller, the way a tap on that screen does, but without touching
 * anything. Never runs inside the app: {@link HandBack} has the Thor's system
 * helper start it as the system, from this APK:
 *
 *     CLASSPATH=<this apk> app_process /system/bin io.github.seandettmann.schoolbag.FocusTool <display>
 *
 * Prints one line, "OK ..." or "ERR ...".
 */
public final class FocusTool {
    private FocusTool() {}

    public static void main(String[] args) {
        String out;
        try {
            int display = Integer.parseInt(args[0]);
            Object atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            out = focus(atm, display);
        } catch (Throwable t) {
            out = "ERR " + why(t);
        }
        System.out.println(out);
        System.out.flush();
        System.exit(0);
    }

    private static String focus(Object atm, int display) throws Exception {
        // Android 12 and later: the display's top app that can take focus, in one call
        Method top = method(atm, "focusTopTask", int.class);
        if (top != null) {
            top.invoke(atm, display);
            return "OK top app on " + display;
        }
        // before that: the display's top visible task, by its number
        Method list = method(atm, "getAllRootTaskInfosOnDisplay", int.class);
        if (list == null) list = method(atm, "getAllStackInfosOnDisplay", int.class);
        if (list == null) return "ERR no way to list tasks";
        List<?> tasks = (List<?>) list.invoke(atm, display);
        Integer task = null;
        if (tasks != null) {
            for (Object info : tasks) {
                if (info == null) continue;
                Integer id = intField(info, "taskId", "stackId");
                if (id == null) continue;
                if (task == null) task = id; // the topmost, if none says it's visible
                if (Boolean.TRUE.equals(boolField(info, "visible", "isVisible"))) { task = id; break; }
            }
        }
        if (task == null) return "ERR nothing on " + display;
        for (String name : new String[]{"setFocusedRootTask", "setFocusedStack", "setFocusedTask"}) {
            Method m = method(atm, name, int.class);
            if (m == null) continue;
            m.invoke(atm, task);
            return "OK task " + task + " (" + name + ")";
        }
        return "ERR no way to focus " + task;
    }

    private static Method method(Object o, String name, Class<?>... args) {
        try {
            return o.getClass().getMethod(name, args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Integer intField(Object o, String... names) {
        for (String n : names) {
            try {
                return o.getClass().getField(n).getInt(o);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try the next name
            }
        }
        return null;
    }

    private static Boolean boolField(Object o, String... names) {
        for (String n : names) {
            try {
                return o.getClass().getField(n).getBoolean(o);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try the next name
            }
        }
        return null;
    }

    private static String why(Throwable t) {
        Throwable c = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
        String m = c.getMessage();
        return c.getClass().getSimpleName() + (m == null ? "" : ": " + (m.length() > 60 ? m.substring(0, 60) : m));
    }
}
