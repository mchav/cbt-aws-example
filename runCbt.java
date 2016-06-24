import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;


import com.amazonaws.services.lambda.runtime.Context;

public class runCbt {
    public String cbtHandler(String args, Context Context) {
        File dir = new File(".");
        File[] filesList = dir.listFiles();
        for (File file : filesList) {
            File src = file;
            File dest = new File("/tmp/" + file.getName());
            try {    
                copy(src, dest);
            } catch (Exception e) {
                e.printStackTrace();
                return "Copy failed.";
            }
        }

        File file = new File("/tmp");

        try {
            // Convert File to a URL
            URL url = file.toURL();          // file:/c:/myclasses/
            URL[] urls = new URL[]{url};

            // Create a new class loader with the directory
            ClassLoader cl = new URLClassLoader(urls);

            // Load in the class; MyClass.class should be located in
            // the directory file:/c:/myclasses/com/mycompany
            Class cls = cl.loadClass("startCbt");
            cls.getMethod("main", String[].class).invoke((Object) null, (Object) null);
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed.";
        } 
        
        return "It's lit!!!";
    }

    public static void copy(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            destination.mkdir();
            for (File file: source.listFiles()) {
                copy(file, new File(destination.toString() + "/" + file.getName()));
            }
        } else {
            InputStream inStream = new FileInputStream(source);
            OutputStream outStream = new FileOutputStream(destination);
            
            byte[] buffer = new byte[1024];
          
            int length;
            //copy the file content in bytes 
            while ((length = inStream.read(buffer)) > 0){
          
              outStream.write(buffer, 0, length);
         
            } 
            inStream.close();
            outStream.close();
        }
    }
}