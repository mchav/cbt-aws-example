import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

public class startCbt {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        // because AWS doesn't let you use any other directory
        // plus I'm not sure this -D stuff works
        //System.getProperties().setProperty("user.home", "/tmp");
        //System.getProperties().setProperty("user.dir", "/tmp");
        System.getProperties().setProperty("zinc.home", "/tmp");
        System.getProperties().setProperty("zinc.dir", "/tmp");
        
        Map<String, String> env = new HashMap<String, String>();
        env.put("CBT_HOME", "/tmp");
        env.put("NAILGUN", "/tmp/nailgun_launcher/");
        env.put("TARGET", "target/scala-2.11/classes/");
        setEnv(env);
        String[] nailgunArgs = new String[args.length + 2];
        nailgunArgs[0] = "0.10";
        nailgunArgs[1] = System.getProperty("user.dir");
        for (int i = 2; i < nailgunArgs.length; i++) {
            nailgunArgs[i] = args[i - 2];
        }

        Object[] params = { nailgunArgs };
        try {
            // Convert File to a URL

            File file = new File("/tmp/nailgun_launcher/target/scala-2.11/classes/");
            URL url = file.toURI().toURL(); 
            URL[] urls = new URL[]{url};
            ClassLoader cl = new URLClassLoader(urls);
            
            Class<?> cls = cl.loadClass("cbt.NailgunLauncher");
            cls.getMethod("main", String[].class).invoke((Object) null, params);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        } 
    }

    @SuppressWarnings("unchecked")
    protected static void setEnv(Map<String, String> newenv) {
      try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>)     theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        }
        catch (NoSuchFieldException e) {
          try {
            Class<?>[] classes = Collections.class.getDeclaredClasses();
            Map<String, String> env = System.getenv();
            for(Class<?> cl : classes) {
                if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
          } catch (Exception e2) {
            e2.printStackTrace();
          }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}