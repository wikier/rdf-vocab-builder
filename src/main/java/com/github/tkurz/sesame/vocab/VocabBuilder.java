package com.github.tkurz.sesame.vocab;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.openrdf.model.Literal;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.util.GraphUtil;
import org.openrdf.model.util.GraphUtilException;
import org.openrdf.model.vocabulary.DC;
import org.openrdf.model.vocabulary.DCTERMS;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.model.vocabulary.SKOS;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ...
 * <p/>
 * @author Thomas Kurz (tkurz@apache.org)
 * @author Jakob Frank (jakob@apache.org)
 */
public class VocabBuilder {

    private String name = null;
    private String prefix = null;
    private String packageName = null;
    private final Model model;

    /**
     * Create a new VocabularyBuilder, reading the vocab definition from the provided file
     * @param filename the input file to read the vocab from
     * @param format the format of the vocab file, may be {@code null}
     * @throws java.io.IOException if the file could not be read
     * @throws RDFParseException if the format of the vocab could not be detected or is unknown.
     */
    public VocabBuilder(String filename, String format) throws IOException, RDFParseException {
        this(filename, format!=null?Rio.getParserFormatForMIMEType(format):null);
    }

    public VocabBuilder(String filename, RDFFormat format) throws IOException, RDFParseException {
        Path file = Paths.get(filename);
        if(!Files.exists(file)) throw new FileNotFoundException(filename);

        if (format == null) {
            format = Rio.getParserFormatForFileName(filename);
        }

        try(final InputStream inputStream = Files.newInputStream(file)) {
            model = Rio.parse(inputStream, "", format);
        }

        //import
        Set<Resource> owlOntologies = model.filter(null, RDF.TYPE, OWL.ONTOLOGY).subjects();
        if(!owlOntologies.isEmpty()) {
            setPrefix(owlOntologies.iterator().next().stringValue());
        }

        setName(file.getFileName().toString());
        if(getName().contains(".")) setName(getName().substring(0,getName().lastIndexOf(".")));
        setName(Character.toUpperCase(getName().charAt(0)) + getName().substring(1));
    }

    /**
     *
     */
    public void run(Path output) throws IOException, GraphUtilException, GenerationException {

        final String className = output.getFileName().toString().replaceFirst("\\.java$", "");

        if (StringUtils.isBlank(name)) {
            name = className;
        }
        if (StringUtils.isBlank(prefix)) {
            throw new GenerationException("could not detect prefix, please set explicitly");
        }

        Pattern pattern = Pattern.compile(Pattern.quote(getPrefix())+"(.+)");
        HashMap<String,URI> splitUris = new HashMap<>();
        for(Resource nextSubject : model.subjects()) {
            if(nextSubject instanceof URI) {
                Matcher matcher = pattern.matcher(nextSubject.stringValue());
                if(matcher.find()) {
                    String k = cleanKey(matcher.group(1));
                    splitUris.put(k, (URI)nextSubject);
                }
            }
        }

        //print
        try(final PrintWriter out = new PrintWriter(Files.newBufferedWriter(output, Charset.forName("utf8")))) {
            //package is optional
            if (StringUtils.isNotBlank(packageName)) {
                out.printf("package %s;%n%n",getPackageName());
            }
            //imports
            out.println("import org.openrdf.model.URI;");
            out.println("import org.openrdf.model.ValueFactory;");
            out.println("import org.openrdf.model.impl.ValueFactoryImpl;");
            out.println();

            //class JavaDoc
            out.printf("/** %n * Namespace %s%n */%n", name);
            //class Definition
            out.printf("public class %s {%n",className);
            out.println();

            //constants
            out.printf("\t/** {@code %s} **/%n", prefix);
            out.printf("\tpublic static final String NAMESPACE = \"%s\";%n",prefix);
            out.println();
            out.printf("\t/** {@code %s} **/%n", name.toLowerCase());
            out.printf("\tpublic static final String PREFIX = \"%s\";%n",name.toLowerCase());
            out.println();

            //and now the resources
            TreeSet<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            keys.addAll(splitUris.keySet());

            for(String key : keys) {
                Literal comment = GraphUtil.getOptionalObjectLiteral(model, splitUris.get(key), RDFS.COMMENT);
                if(comment == null) {
                    comment = GraphUtil.getOptionalObjectLiteral(model, splitUris.get(key), DCTERMS.DESCRIPTION);
                }
                if(comment == null) {
                    comment = GraphUtil.getOptionalObjectLiteral(model, splitUris.get(key), SKOS.DEFINITION);
                }
                if(comment == null) {
                    comment = GraphUtil.getOptionalObjectLiteral(model, splitUris.get(key), DC.DESCRIPTION);
                }
                if(comment == null) {
                    comment = GraphUtil.getOptionalObjectLiteral(model, splitUris.get(key), RDFS.LABEL);
                }
                if(comment == null) {
                    comment = GraphUtil.getOptionalObjectLiteral(model, splitUris.get(key), DCTERMS.TITLE);
                }
                if(comment == null) {
                    comment = GraphUtil.getOptionalObjectLiteral(model, splitUris.get(key), DC.TITLE);
                }

                out.println("\t/**");
                out.printf("\t * {@code %s}.%n", splitUris.get(key).stringValue());
                if (comment != null) {
                    out.println("\t * <p>");
                    out.printf("\t * %s%n", WordUtils.wrap(comment.getLabel().replaceAll("\\s+", " "), 70, "\n\t * ", false));
                }
                out.println("\t *");
                out.printf("\t * @see <a href=\"%s\">%s</a>%n", splitUris.get(key), key);
                out.println("\t */");
                out.printf("\tpublic static final URI %s;%n", key);
                out.println();
            }

            //static init
            out.println("\tstatic {");
            out.printf("\t\tValueFactory factory = ValueFactoryImpl.getInstance();%n");
            out.println();
            for(String key : keys) {
                out.printf("\t\t%s = factory.createURI(%s, \"%s\");%n",key,className+".NAMESPACE",key);
            }
            out.println("\t}");
            out.println();
            out.println("}");

            //end
            out.flush();
        }
    }

    private String cleanKey(String s) {
        s = s.replaceAll("#","");
        s = s.replaceAll("\\.","_");
        s = s.replaceAll("-","_");
        return s;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

}
