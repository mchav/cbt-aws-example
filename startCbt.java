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
        System.getProperties().setProperty("user.home", "/tmp");
        System.getProperties().setProperty("user.dir", "/tmp");
        System.getProperties().setProperty("zinc.home", "/tmp");
        System.getProperties().setProperty("zinc.dir", "/tmp");
        String[] nailgunArgs = {"0.10", System.getProperty("user.dir"), "usage"};//, "-Dzinc.home=/tmp", "-Ddir=/tmp", "-Dzinc.dir=/tmp"};
        Object[] params ={ nailgunArgs };
        try {
            // Convert File to a URL

            File file = new File("/tmp/nailgun_launcher/target/scala-2.11/classes");
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
}