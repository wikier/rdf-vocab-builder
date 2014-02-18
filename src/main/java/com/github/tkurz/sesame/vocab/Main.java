package com.github.tkurz.sesame.vocab;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * ...
 * <p/>
 * Author: Thomas Kurz (tkurz@apache.org)
 */
public class Main {

    public static void main(String [] args) {
        try {
            String file = "ldp.ttl"; //"src/main/resources/ldp.ttl";
            String type = "application/rdf+xml";

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("*** RDF Namespace Constants Constructor ***");

            if(args.length > 0 && args[0] != null) {
                file = args[0];
                System.out.println("filepath : "+file);
            } else {
                System.out.println("insert filepath [" + file + "] : ");
                String _file = reader.readLine();if(!_file.equals(""))file=_file;
            }

            if(args.length > 1 && args[1] != null) {
                type = args[1];
                System.out.println("mimetype : "+type);
            } else {
                if(file.contains(".")) type = getMimeType(file.substring(file.lastIndexOf(".")));
                System.out.println("insert file mimetype [" + type + "] : ");
                String _type=reader.readLine();if(!_type.equals(""))type=_type;
            }

            //parse data and get url prefix
            VocabBuilder e = new VocabBuilder(file,type);
            System.out.println("insert url-prefix [" + e.prefix + "] : ");
            String _prefix=reader.readLine();if(!_prefix.equals(""))e.prefix=_prefix;

            System.out.println("insert class name [" + e.name + "] : ");
            String _name=reader.readLine();if(!_name.equals(""))e.name=_name;

            System.out.println("insert package name ["+e.packageName+"] : ");
            String _pname=reader.readLine();if(!_pname.equals(""))e.packageName=_pname;

            System.out.println("insert output folder ["+e.outputFolder+"] : ");
            String _outputFolder=reader.readLine();if(!_outputFolder.equals(""))e.outputFolder=_outputFolder;

            e.run();
            System.out.println("*** file created: '"+e.outputFolder+"/"+e.name+".java' ***");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static String getMimeType(String file_extension) {
        if(file_extension.equals(".ttl")) return "text/turtle";
        if(file_extension.equals(".n3")) return "text/n3";
        return "application/rdf+xml";

    }


}