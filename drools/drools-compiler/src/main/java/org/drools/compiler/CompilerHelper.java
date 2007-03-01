package org.drools.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.jci.compilers.CompilationResult;
import org.apache.commons.jci.compilers.EclipseJavaCompiler;
import org.apache.commons.jci.compilers.EclipseJavaCompilerSettings;
import org.apache.commons.jci.compilers.JavaCompiler;
import org.apache.commons.jci.compilers.JavaCompilerFactory;
import org.apache.commons.jci.problems.CompilationProblem;
import org.apache.commons.jci.readers.MemoryResourceReader;
import org.apache.commons.jci.readers.ResourceReader;
import org.codehaus.jfdi.interpreter.TypeResolver;
import org.drools.RuntimeDroolsException;
import org.drools.compiler.PackageBuilder.ErrorHandler;
import org.drools.compiler.PackageBuilder.FunctionErrorHandler;
import org.drools.compiler.PackageBuilder.RuleErrorHandler;
import org.drools.compiler.PackageBuilder.RuleInvokerErrorHandler;
import org.drools.lang.descr.BaseDescr;
import org.drools.lang.descr.FunctionDescr;
import org.drools.lang.descr.RuleDescr;
import org.drools.rule.LineMappings;
import org.drools.rule.Package;
import org.drools.rule.Rule;
import org.drools.rule.builder.FunctionBuilder;
import org.drools.rule.builder.RuleBuilder;
import org.drools.rule.builder.dialect.java.JavaFunctionBuilder;
import org.drools.rule.builder.dialect.java.PackageStore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

public class CompilerHelper {
    private Package                     pkg;
    private JavaCompiler                compiler;
    private List                        generatedClassList;
    private PackageBuilderConfiguration configuration;

    private MemoryResourceReader        src;
    private PackageStore                packageStoreWrapper;

    private Map                         lineMappings;

    private Map                         errorHandlers;

    private List                        results;

    public CompilerHelper(final Package pkg,
                          PackageBuilderConfiguration configuration) {
        this.pkg = pkg;
        this.configuration = configuration;
        loadCompiler();

        if ( pkg != null ) {
            init( pkg );
        }
    }

    public void init(Package pkg) {
        this.pkg = pkg;
        this.errorHandlers = new HashMap();

        this.src = new MemoryResourceReader();
        if ( pkg != null ) {
            this.packageStoreWrapper = new PackageStore( pkg.getPackageCompilationData() );
            this.lineMappings = pkg.getPackageCompilationData().getLineMappings();
        }

        this.generatedClassList = new ArrayList();

        this.packageStoreWrapper = new PackageStore( pkg.getPackageCompilationData() );
        this.lineMappings = new HashMap();
        pkg.getPackageCompilationData().setLineMappings( this.lineMappings );

        this.results = new ArrayList();
    }

    public void init(RuleDescr ruleDescr) {
        final String ruleClassName = getUniqueLegalName( this.pkg.getName(),
                                                         ruleDescr.getName(),
                                                         "java",
                                                         this.src );
        ruleDescr.setClassName( ucFirst( ruleClassName ) );
    }

    /**
     * This actually triggers the compiling of all the resources.
     * Errors are mapped back to the element that originally generated the semantic
     * code.
     */
    public void compileAll() {
        if ( this.generatedClassList.isEmpty() ) {
            return;
        }
        final String[] classes = new String[this.generatedClassList.size()];
        this.generatedClassList.toArray( classes );

        final CompilationResult result = this.compiler.compile( classes,
                                                                this.src,
                                                                this.packageStoreWrapper,
                                                                this.pkg.getPackageCompilationData().getClassLoader() );

        //this will sort out the errors based on what class/file they happened in
        if ( result.getErrors().length > 0 ) {
            for ( int i = 0; i < result.getErrors().length; i++ ) {
                final CompilationProblem err = result.getErrors()[i];

                final ErrorHandler handler = (ErrorHandler) this.errorHandlers.get( err.getFileName() );
                if ( handler instanceof RuleErrorHandler ) {
                    final RuleErrorHandler rh = (RuleErrorHandler) handler;
                }
                handler.addError( err );
            }

            final Collection errors = this.errorHandlers.values();
            for ( final Iterator iter = errors.iterator(); iter.hasNext(); ) {
                final ErrorHandler handler = (ErrorHandler) iter.next();
                if ( handler.isInError() ) {
                    if ( !(handler instanceof RuleInvokerErrorHandler) ) {
                        this.results.add( handler.getError() );
                    } else {
                        //we don't really want to report invoker errors.
                        //mostly as they can happen when there is a syntax error in the RHS
                        //and otherwise, it is a programmatic error in drools itself.
                        System.err.println( "Warning: An error occurred compiling a semantic invoker. Errors should have been reported elsewhere." );
                    }
                }
            }
        }
    }

    /**
     * This will setup the semantic components of the rule for compiling later on.
     * It will not actually call the compiler
     */
    public void addRuleSemantics(final RuleBuilder builder,
                                 final Rule rule,
                                 final RuleDescr ruleDescr) {
        // return if there is no ruleclass name;       
        if ( builder.getRuleClass() == null ) {
            return;
        }

        // The compilation result is for th entire rule, so difficult to associate with any descr
        addClassCompileTask( this.pkg.getName() + "." + ruleDescr.getClassName(),
                             ruleDescr,
                             builder.getRuleClass(),
                             this.src,
                             new RuleErrorHandler( ruleDescr,
                                                   rule,
                                                   "Rule Compilation error" ) );

        for ( final Iterator it = builder.getInvokers().keySet().iterator(); it.hasNext(); ) {
            final String className = (String) it.next();

            // Check if an invoker - returnvalue, predicate, eval or consequence has been associated
            // If so we add it to the PackageCompilationData as it will get wired up on compilation
            final Object invoker = builder.getInvokerLookups().get( className );
            if ( invoker != null ) {
                this.pkg.getPackageCompilationData().putInvoker( className,
                                                                 invoker );
            }
            final String text = (String) builder.getInvokers().get( className );

            //System.out.println( className + ":\n" + text );
            final BaseDescr descr = (BaseDescr) builder.getDescrLookups().get( className );
            addClassCompileTask( className,
                                 descr,
                                 text,
                                 this.src,
                                 new RuleInvokerErrorHandler( descr,
                                                              rule,
                                                              "Unable to generate rule invoker." ) );

        }

        // setup the line mappins for this rule
        String name = pkg.getName() + "." + ucFirst( ruleDescr.getClassName() );
        LineMappings mapping = new LineMappings( name );
        mapping.setStartLine( ruleDescr.getConsequenceLine() );
        mapping.setOffset( ruleDescr.getConsequenceOffset() );
        this.lineMappings.put( name,
                               mapping );
    }

    public void addFunction(final FunctionDescr functionDescr,
                            TypeResolver typeResolver) {

        String functionClassName = this.pkg.getName() + "." + ucFirst( functionDescr.getName() );
        this.pkg.addStaticImport( functionClassName + "." + functionDescr.getName() );
        functionDescr.setClassName( functionClassName );

        final FunctionBuilder builder = new JavaFunctionBuilder();
        this.pkg.addFunction( functionDescr.getName() );

        addClassCompileTask( functionClassName,
                             functionDescr,
                             builder.build( this.pkg,
                                            functionDescr,
                                            typeResolver,
                                            lineMappings ),
                             this.src,
                             new FunctionErrorHandler( functionDescr,
                                                       "Function Compilation error" ) );

        LineMappings mapping = new LineMappings( functionClassName );
        mapping.setStartLine( functionDescr.getLine() );
        mapping.setOffset( functionDescr.getOffset() );
        this.lineMappings.put( functionClassName,
                               mapping );
    }

    /** 
     * This adds a compile "task" for when the compiler of 
     * semantics (JCI) is called later on with compileAll()\
     * which actually does the compiling.
     * The ErrorHandler is required to map the errors back to the 
     * element that caused it.
     */
    private void addClassCompileTask(final String className,
                                     final BaseDescr descr,
                                     final String text,
                                     final MemoryResourceReader src,
                                     final ErrorHandler handler) {

        final String fileName = className.replace( '.',
                                                   '/' ) + ".java";

        src.add( fileName,
                 text.getBytes() );

        this.errorHandlers.put( fileName,
                                handler );

        addClassName( fileName );
    }

    public void addClassName(String className) {
        this.generatedClassList.add( className );
    }

    private void loadCompiler() {
        switch ( this.configuration.getCompiler() ) {
            case PackageBuilderConfiguration.JANINO : {
                if ( !"1.4".equals( this.configuration.getJavaLanguageLevel() ) ) {
                    throw new RuntimeDroolsException( "Incompatible Java language level with selected compiler" );
                }
                this.compiler = JavaCompilerFactory.getInstance().createCompiler( "janino" );
                break;
            }
            case PackageBuilderConfiguration.ECLIPSE :
            default : {
                final EclipseJavaCompilerSettings eclipseSettings = new EclipseJavaCompilerSettings();
                Map map = eclipseSettings.getMap();
                map.put( CompilerOptions.OPTION_TargetPlatform,
                         this.configuration.getJavaLanguageLevel() );

                // We now default this to 1.5, so we can use static imports.
                map.put( CompilerOptions.OPTION_Source,
                         "1.5" );
                this.compiler = new EclipseJavaCompiler( map );
                break;
            }
        }
    }

    public List getResults() {
        return this.results;
    }

    /**
     * Takes a given name and makes sure that its legal and doesn't already exist. If the file exists it increases counter appender untill it is unique.
     * 
     * @param packageName
     * @param name
     * @param ext
     * @return
     */
    private String getUniqueLegalName(final String packageName,
                                      final String name,
                                      final String ext,
                                      final ResourceReader src) {
        // replaces all non alphanumeric or $ chars with _
        String newName = "Rule_" + name.replaceAll( "[^\\w$]",
                                                    "_" );

        // make sure the class name does not exist, if it does increase the counter
        int counter = -1;
        boolean exists = true;
        while ( exists ) {

            counter++;
            final String fileName = packageName.replaceAll( "\\.",
                                                            "/" ) + newName + "_" + counter + ext;

            exists = src.isAvailable( fileName );
        }
        // we have duplicate file names so append counter
        if ( counter >= 0 ) {
            newName = newName + "_" + counter;
        }

        return newName;
    }

    private String ucFirst(final String name) {
        return name.toUpperCase().charAt( 0 ) + name.substring( 1 );
    }
}
