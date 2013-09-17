/* Copyright 2011-2012 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lesscss;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
/*import org.dynjs.Config;
import org.dynjs.SystemClock;
import org.dynjs.runtime.DynJS;
import org.dynjs.runtime.ExecutionContext;
import org.dynjs.runtime.JSFunction;
import org.dynjs.runtime.Reference;*/
import org.lesscss.logging.LessLogger;
import org.lesscss.logging.LessLoggerFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;

/**
 * The LESS compiler to compile LESS sources to CSS stylesheets.
 * <p>
 * The compiler uses Rhino (JavaScript implementation written in Java), Envjs
 * (simulated browser environment written in JavaScript), and the official LESS
 * JavaScript compiler.<br />
 * Note that the compiler is not a Java implementation of LESS itself, but
 * rather integrates the LESS JavaScript compiler within a Java/JavaScript
 * browser environment provided by Rhino and Envjs.
 * </p>
 * <p>
 * The compiler comes bundled with the Envjs and LESS JavaScript, so there is no
 * need to include them yourself. But if needed they can be overridden.
 * </p>
 * <h4>Basic code example:</h4>
 * <pre>
 * LessCompiler lessCompiler = new LessCompiler();
 * String css = lessCompiler.compile("
 *
 * @color: #4D926F; #header { color:
 * @color; }");
 * </pre>
 *
 * @author Marcel Overdijk
 * @see <a href="http://lesscss.org/">LESS - The Dynamic Stylesheet language</a>
 * @see <a href="http://www.mozilla.org/rhino/">Rhino - JavaScript for Java</a>
 * @see <a href="http://www.envjs.com/">Envjs - Bringing the Browser</a>
 */
public class LessCompiler {

    private static final String COMPILE_STRING = "function doIt(input, compress) { var result; var parser = new less.Parser(); parser.parse(input, function(e, tree) { if (e instanceof Object) { throw e; } ; result = tree.toCSS({compress: compress}); }); return result; }";
    private static final LessLogger logger = LessLoggerFactory.getLogger(LessCompiler.class);
    private URL envJs = LessCompiler.class.getClassLoader().getResource("META-INF/env.rhino.js");
    private URL lessJs = LessCompiler.class.getClassLoader().getResource("META-INF/less.js");
    private List<URL> customJs = Collections.emptyList();
    private boolean compress = false;
    private String encoding = null;
    private Function doIt;
    private Scriptable scope;
    private URL envV8Js = LessCompiler.class.getClassLoader().getResource("META-INF/env.v8.js");
    private ScriptEngine v8;
    //private DynJS dynjs;

    /**
     * Constructs a new
     * <code>LessCompiler</code>.
     */
    public LessCompiler() {
    }

    /**
     * Returns the Envjs JavaScript file used by the compiler.
     *
     * @return The Envjs JavaScript file used by the compiler.
     */
    public URL getEnvJs() {
        return envJs;
    }

    /**
     * Sets the Envjs JavaScript file used by the compiler. Must be set before
     * {@link #init()} is called.
     *
     * @param envJs The Envjs JavaScript file used by the compiler.
     */
    public synchronized void setEnvJs(URL envJs) {
        if (scope != null) {
            throw new IllegalStateException("This method can only be called before init()");
        }
        this.envJs = envJs;
    }

    /**
     * Returns the LESS JavaScript file used by the compiler. COMPILE_STRING
     *
     * @return The LESS JavaScript file used by the compiler.
     */
    public URL getLessJs() {
        return lessJs;
    }

    /**
     * Sets the LESS JavaScript file used by the compiler. Must be set before
     * {@link #init()} is called.
     *
     * @param lessJs LESS JavaScript file used by the compiler.
     */
    public synchronized void setLessJs(URL lessJs) {
        if (scope != null) {
            throw new IllegalStateException("This method can only be called before init()");
        }
        this.lessJs = lessJs;
    }

    /**
     * Returns the custom JavaScript files used by the compiler.
     *
     * @return The custom JavaScript files used by the compiler.
     */
    public List<URL> getCustomJs() {
        return Collections.unmodifiableList(customJs);
    }

    /**
     * Sets a single custom JavaScript file used by the compiler. Must be set
     * before {@link #init()} is called.
     *
     * @param customJs A single custom JavaScript file used by the compiler.
     */
    public synchronized void setCustomJs(URL customJs) {
        if (scope != null) {
            throw new IllegalStateException("This method can only be called before init()");
        }
        this.customJs = Collections.singletonList(customJs);
    }

    /**
     * Sets the custom JavaScript files used by the compiler. Must be set before
     * {@link #init()} is called.
     *
     * @param customJs The custom JavaScript files used by the compiler.
     */
    public synchronized void setCustomJs(List<URL> customJs) {
        if (scope != null) {
            throw new IllegalStateException("This method can only be called before init()");
        }
        // copy the list so there's no way for anyone else who holds a reference to the list to modify it
        this.customJs = new ArrayList<URL>(customJs);
    }

    /**
     * Returns whether the compiler will compress the CSS.
     *
     * @return Whether the compiler will compress the CSS.
     */
    public boolean isCompress() {
        return compress;
    }

    /**
     * Sets the compiler to compress the CSS. Must be set before {@link #init()}
     * is called.
     *
     * @param compress If <code>true</code>, sets the compiler to compress the
     * CSS.
     */
    public synchronized void setCompress(boolean compress) {
        if (scope != null) {
            throw new IllegalStateException("This method can only be called before init()");
        }
        this.compress = compress;
    }

    /**
     * Returns the character encoding used by the compiler when writing the
     * output
     * <code>File</code>.
     *
     * @return The character encoding used by the compiler when writing the
     * output <code>File</code>.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the character encoding used by the compiler when writing the output
     * <code>File</code>. If not set the platform default will be used. Must be
     * set before {@link #init()} is called.
     *
     * @param encoding character encoding used by the compiler when writing the
     * output <code>File</code>.
     */
    public synchronized void setEncoding(String encoding) {
        if (scope != null) {
            throw new IllegalStateException("This method can only be called before init()");
        }
        this.encoding = encoding;
    }

    /**
     * Initializes this
     * <code>LessCompiler</code>.
     * <p>
     * It is not needed to call this method manually, as it is called implicitly
     * by the compile methods if needed.
     * </p>
     */
    public synchronized void init() {
        long start = System.currentTimeMillis();

        if (initV8()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Finished initialization of LESS compiler in " + (System.currentTimeMillis() - start) + " ms.");
            }
            return;
        }
        if (false /*&& initDynJs()*/) {
            if (logger.isDebugEnabled()) {
                logger.debug("Finished initialization of LESS compiler in " + (System.currentTimeMillis() - start) + " ms.");
            }
            return;
        }

        try {
            Context cx = Context.enter();
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_1_7);

            Global global = new Global();
            global.init(cx);

            scope = cx.initStandardObjects(global);
            scope.put("logger", scope, Context.toObject(logger, scope));

            List<URL> jsUrls = new ArrayList<URL>(2 + customJs.size());
            jsUrls.add(envJs);
            jsUrls.add(lessJs);
            jsUrls.addAll(customJs);

            for (URL url : jsUrls) {
                String fileContent = IOUtils.toString(url.openStream(), Charset.forName("UTF-8"));
                cx.evaluateString(scope, fileContent, url.toString(), 1, null);
            }
            doIt = cx.compileFunction(scope, COMPILE_STRING, "doIt.js", 1, null);
        } catch (Exception e) {
            String message = "Failed to initialize LESS compiler.";
            logger.error(message, e);
            throw new IllegalStateException(message, e);
        } finally {
            Context.exit();
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Finished initialization of LESS compiler in " + (System.currentTimeMillis() - start) + " ms.");
        }
    }

    public boolean initV8() {
        ScriptEngine v8eng;
        try {
            ScriptEngineManager factory = new ScriptEngineManager();
            v8eng = factory.getEngineByName("jav8");
        } catch (Throwable ex) {
            logger.info("could not init v8 engine, fallback to rhino <" + ex + ">");
            return false;
        }
        if (v8eng == null) {
            logger.info("could not find v8 engine, fallback to rhino.");
            return false;
        }
        try {
            List<URL> jsUrls = new ArrayList<URL>(2 + customJs.size());
            jsUrls.add(envV8Js);
            jsUrls.add(lessJs);
            jsUrls.addAll(customJs);
            for (URL url : jsUrls) {
                String fileContent = IOUtils.toString(url.openStream(), Charset.forName("UTF-8"));
                v8eng.eval(fileContent);
            }
            v8eng.eval(COMPILE_STRING);
        } catch (Exception e) {
            String message = "Failed to initialize LESS (v8 based) compiler.";
            logger.error(message, e);
            return false;
        }
        this.v8 = v8eng;
        logger.info("successfull init v8 engine!");
        return true;
    }

    /*
    private boolean initDynJs() {
        Config config = new Config();
        config.setDebug(false);
        //config.setClock(SystemClock.INSTANCE);
        //config.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        //config.setLocale(Locale.US);
        DynJS runtime = new DynJS(config);

        try {
            List<URL> jsUrls = new ArrayList<URL>(2 + customJs.size());
            //jsUrls.add(envV8Js);
            jsUrls.add(envV8Js);
            jsUrls.add(lessJs);
            jsUrls.addAll(customJs);
            for (URL url : jsUrls) {
                String fileContent = IOUtils.toString(url.openStream(), Charset.forName("UTF-8"));
                runtime.evaluate(fileContent);
            }
            runtime.evaluate(COMPILE_STRING);
        } catch (Exception e) {
            String message = "Failed to initialize LESS (dynjs based) compiler.";
            logger.error(message, e);
            return false;
        }
        this.dynjs = runtime;
        logger.info("successfull init dynjs engine!");
        return true;
    }*/

    /**
     * Compiles the LESS input
     * <code>String</code> to CSS.
     *
     * @param input The LESS input <code>String</code> to compile.
     * @return The CSS.
     */
    public synchronized String compile(String input) throws LessException {
        if (input == null || input.length() == 0) {
            return "";
        }

        if (v8 == null && scope == null/* && dynjs == null*/) {
            init();
        }

        long start = System.currentTimeMillis();
        Context cx = null;
        try {
            Object result;
            if (v8 != null) {
                Invocable invocable = (Invocable) v8;
                // V8 is not thread safe?
                result = invocable.invokeFunction("doItSave", input, compress);
                String css = result.toString();
                String errorPrefix = "error:";
                if (css.startsWith(errorPrefix)) {
                    throw new IllegalStateException(css.substring(errorPrefix.length()));
                }
            }/* else if (dynjs != null) {
                ExecutionContext context = dynjs.getExecutionContext();
                Reference doItRef = context.resolve("doItSave");
                JSFunction doItSave = (JSFunction) doItRef.getValue(context);
                result = context.call(doItRef, doItSave, (Object) null, input, compress);
            }*/ else {
                // rhino
                cx = Context.enter();
                result = doIt.call(cx, scope, null, new Object[]{input, compress});

                if (logger.isDebugEnabled()) {
                    logger.debug("Finished compilation of LESS source in " + (System.currentTimeMillis() - start) + " ms.");
                }
            }
            return result.toString();
        } catch (Exception e) {
            logErrorDetail(input, e);

            if (e instanceof JavaScriptException) {
                Scriptable value = (Scriptable) ((JavaScriptException) e).getValue();
                if (value != null) {
                    String msg = extractErrorPointerMessage(value);
                    throw new LessException(msg, e);
                }
            }
            if (e instanceof IllegalStateException) {
                throw new LessException(e.getMessage(), e);
            }
            throw new LessException(e);
        } finally {
            if (cx != null) {
                Context.exit();
            }
        }
    }

    /**
     * Compiles the LESS input
     * <code>File</code> to CSS.
     *
     * @param input The LESS input <code>File</code> to compile.
     * @return The CSS.
     * @throws IOException If the LESS file cannot be read.
     */
    public String compile(File input) throws IOException, LessException {
        LessSource lessSource = new LessSource(new FileResource(input));
        return compile(lessSource);
    }

    /**
     * Compiles the LESS input
     * <code>File</code> to CSS and writes it to the specified output
     * <code>File</code>.
     *
     * @param input The LESS input <code>File</code> to compile.
     * @param output The output <code>File</code> to write the CSS to.
     * @throws IOException If the LESS file cannot be read or the output file
     * cannot be written.
     */
    public void compile(File input, File output) throws IOException, LessException {
        this.compile(input, output, true);
    }

    /**
     * Compiles the LESS input
     * <code>File</code> to CSS and writes it to the specified output
     * <code>File</code>.
     *
     * @param input The LESS input <code>File</code> to compile.
     * @param output The output <code>File</code> to write the CSS to.
     * @param force 'false' to only compile the LESS input file in case the LESS
     * source has been modified (including imports) or the output file does not
     * exists.
     * @throws IOException If the LESS file cannot be read or the output file
     * cannot be written.
     */
    public void compile(File input, File output, boolean force) throws IOException, LessException {
        LessSource lessSource = new LessSource(new FileResource(input));
        compile(lessSource, output, force);
    }

    /**
     * Compiles the input
     * <code>LessSource</code> to CSS.
     *
     * @param input The input <code>LessSource</code> to compile.
     * @return The CSS.
     */
    public String compile(LessSource input) throws LessException {
        return compile(input.getNormalizedContent());
    }

    /**
     * Compiles the input
     * <code>LessSource</code> to CSS and writes it to the specified output
     * <code>File</code>.
     *
     * @param input The input <code>LessSource</code> to compile.
     * @param output The output <code>File</code> to write the CSS to.
     * @throws IOException If the LESS file cannot be read or the output file
     * cannot be written.
     */
    public void compile(LessSource input, File output) throws IOException, LessException {
        compile(input, output, true);
    }

    /**
     * Compiles the input
     * <code>LessSource</code> to CSS and writes it to the specified output
     * <code>File</code>.
     *
     * @param input The input <code>LessSource</code> to compile.
     * @param output The output <code>File</code> to write the CSS to.
     * @param force 'false' to only compile the input <code>LessSource</code> in
     * case the LESS source has been modified (including imports) or the output
     * file does not exists.
     * @throws IOException If the LESS file cannot be read or the output file
     * cannot be written.
     */
    public void compile(LessSource input, File output, boolean force) throws IOException, LessException {
        if (force || !output.exists() || output.lastModified() < input.getLastModifiedIncludingImports()) {
            String data = compile(input);
            FileUtils.writeStringToFile(output, data, encoding);
        }
    }

    private void logErrorDetail(String input, Exception e) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.ENGLISH);
        nf.setMinimumIntegerDigits(3);
        StringBuilder sb = new StringBuilder(input.length());
        sb.append("Error compiling content content:\n");
        String[] lines = input.split("\\r?\\n");
        for (int i = 0, l = lines.length; i < l; ++i) {
            sb.append(nf.format(i + 1)).append(":").append(lines[i]).append("\n");
        }
        sb.append("ERROR:");
        sb.append(e.getMessage());
        logger.error(sb.toString(), e);
    }

    private String extractErrorPointerMessage(Scriptable value) {
        /* e.g.:
         * message=variable @linkColor is undefined;
         * line=1.0
         * column=11.0
         * index=11.0;
         */
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(ScriptableObject.getProperty(value, "message")));
        sb.append("(line:");
        sb.append(removeFractions(String.valueOf(ScriptableObject.getProperty(value, "line"))));
        sb.append(",column:");
        sb.append(removeFractions(String.valueOf(ScriptableObject.getProperty(value, "column"))));
        sb.append(",index:");
        sb.append(removeFractions(String.valueOf(ScriptableObject.getProperty(value, "index"))));
        sb.append(")");
        String msg = sb.toString();
        return msg;
    }

    private String removeFractions(String in) {
        if (in == null) {
            return "";
        }
        int i = in.indexOf(".");
        if (i < 0) {
            return in;
        }
        return in.substring(0, i);
    }
}
