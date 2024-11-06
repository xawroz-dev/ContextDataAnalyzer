
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class ContextDataKeyExtractor {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Please provide the path to the Java source files.");
            System.exit(1);
        }

        String rootPath = args[0];
        System.out.println("Analyzing Java source files at: " + rootPath);

        Launcher launcher = new Launcher();
        launcher.addInputResource(rootPath);
        launcher.getEnvironment().setNoClasspath(true); // Analyze code without dependencies
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setLevel("INFO"); // Set logging level to INFO

        System.out.println("Building model...");
        CtModel model = launcher.buildModel();
        System.out.println("Model built.");

        Set<KeyUsage> keyUsages = new HashSet<>();

        System.out.println("Searching for method invocations...");
        int invocationCount = 0;

        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            invocationCount++;
            String methodName = invocation.getExecutable().getSimpleName();
            // Debugging output
            System.out.println("Invocation found: " + methodName + " at " + invocation.getPosition());
            if (methodName.equals("put") || methodName.equals("get")) {
                CtExpression<?> target = invocation.getTarget();
                if (target != null && isContextDataVariable(target)) {
                    if (!invocation.getArguments().isEmpty()) {
                        CtExpression<?> keyExpr = invocation.getArguments().get(0);
                        String keyValue = resolveExpressionValue(keyExpr);
                        if (keyValue != null) {
                            SourcePosition position = invocation.getPosition();
                            File file = position.getFile();
                            int line = position.getLine();
                            keyUsages.add(new KeyUsage(file.getPath(), line, methodName, keyValue));
                            System.out.println("Found key: " + keyValue + " in file: " + file.getPath() + " at line: " + line);
                        } else {
                            System.out.println("Could not resolve key value at " + invocation.getPosition());
                        }
                    }
                } else {
                    System.out.println("Target is not contextData at " + invocation.getPosition());
                }
            }
        }

        System.out.println("Total method invocations found: " + invocationCount);
        System.out.println("Total key usages found: " + keyUsages.size());

        if (keyUsages.isEmpty()) {
            System.out.println("No keys found in contextData.put/get method calls.");
        } else {
            // Output the results to a JSON file
            try (PrintWriter out = new PrintWriter("context_data_keys.json")) {
                out.println("[");
                int index = 0;
                int size = keyUsages.size();
                for (KeyUsage usage : keyUsages) {
                    out.println("  {");
                    out.println("    \"file\": \"" + usage.fileName.replace("\\", "\\\\") + "\",");
                    out.println("    \"line\": " + usage.lineNumber + ",");
                    out.println("    \"method\": \"" + usage.method + "\",");
                    out.println("    \"key\": \"" + usage.key.replace("\"", "\\\"") + "\"");
                    out.print("  }" + (index < size - 1 ? "," : ""));
                    out.println();
                    index++;
                }
                out.println("]");
            } catch (Exception e) {
                System.err.println("Error writing to file: " + e.getMessage());
            }
            System.out.println("Results written to context_data_keys.json");
        }
    }

    private static boolean isContextDataVariable(CtExpression<?> expr) {
        // Check if the expression is a variable named 'contextData'
        return expr.toString().equals("contextData");
    }

    private static String resolveExpressionValue(CtExpression<?> expr) {
        if (expr == null) {
            return null;
        }

        // Check if the expression is a literal (e.g., "key")
        if (expr instanceof CtLiteral) {
            CtLiteral<?> literal = (CtLiteral<?>) expr;
            Object value = literal.getValue();
            return value != null ? value.toString() : null;
        }

        // Check if the expression is a variable read (e.g., CONSTANT_NAME)
        if (expr instanceof CtVariableRead) {
            CtVariableRead<?> variableRead = (CtVariableRead<?>) expr;
            CtVariableReference<?> variableRef = variableRead.getVariable();
            CtVariable<?> declaration = variableRef.getDeclaration();

            if (declaration instanceof CtField) {
                CtField<?> field = (CtField<?>) declaration;
                if (field.isFinal() && field.isStatic()) {
                    CtExpression<?> initializer = field.getDefaultExpression();
                    return resolveExpressionValue(initializer);
                }
            }
            // Handle local final variables
            if (declaration instanceof CtLocalVariable) {
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) declaration;
                if (localVar.isFinal()) {
                    CtExpression<?> initializer = localVar.getDefaultExpression();
                    return resolveExpressionValue(initializer);
                }
            }
        }

        // Handle field access (e.g., ClassName.CONSTANT_NAME)
        if (expr instanceof CtFieldAccess) {
            CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) expr;
            CtFieldReference<?> fieldRef = fieldAccess.getVariable();
            CtField<?> field = fieldRef.getDeclaration();
            if (field != null && field.isFinal() && field.isStatic()) {
                CtExpression<?> initializer = field.getDefaultExpression();
                return resolveExpressionValue(initializer);
            }
        }

        // If unable to resolve, return null
        return null;
    }

    private static class KeyUsage {
        String fileName;
        int lineNumber;
        String method;
        String key;

        KeyUsage(String fileName, int lineNumber, String method, String key) {
            this.fileName = fileName;
            this.lineNumber = lineNumber;
            this.method = method;
            this.key = key;
        }
    }
}

