import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Inventaría declaraciones Java y la presencia de JavaDoc mediante el AST del JDK.
 * No resuelve dependencias ni evalúa la calidad semántica de los comentarios.
 */
public final class DocInventory {
    /** Impide crear instancias de esta utilidad de línea de comandos. */
    private DocInventory() { }

    /**
     * Analiza las rutas UTF-8 recibidas en un archivo, una por línea.
     * @param args archivo de rutas absolutas a fuentes Java versionadas
     * @throws Exception si una fuente no puede leerse o analizarse
     */
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || args.length != 1) {
            throw new IllegalArgumentException("Requiere JDK y un archivo de rutas");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var files = manager.getJavaFileObjectsFromStrings(Files.readAllLines(Path.of(args[0])));
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "-encoding", "UTF-8"), null, files);
            var docs = DocTrees.instance(task);
            System.out.println("archivo\ttipo\tnombre\tlinea\tjavadoc_presente");
            for (CompilationUnitTree unit : task.parse()) {
                new TreePathScanner<Void, String>() {
                    @Override
                    public Void visitClass(ClassTree node, String parent) {
                        String name = node.getSimpleName().toString();
                        if (name.isEmpty()) {
                            // Una clase anónima no tiene declaración de tipo documentable.
                            return super.visitClass(node, parent);
                        }
                        String qualified = parent == null ? name : parent + "." + name;
                        long position = docs.getSourcePositions().getStartPosition(unit, node);
                        System.out.printf("%s\t%s\t%s\t%d\t%s%n",
                                Path.of(unit.getSourceFile().toUri()), node.getKind(), qualified,
                                unit.getLineMap().getLineNumber(position),
                                docs.getDocCommentTree(getCurrentPath()) != null);
                        return super.visitClass(node, qualified);
                    }
                }.scan(unit, null);
            }
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
            throw new IllegalStateException("Error de sintaxis: inventario Java incompleto");
        }
    }
}
