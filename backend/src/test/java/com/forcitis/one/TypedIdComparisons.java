package com.forcitis.one;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

/**
 * Finds equality comparisons that take the raw value out of a typed id: a.value() == b.value(), Objects.equals(a.value(), b.value()),
 * a.value().equals(b.value()) and the same against a plain long or text such as subject.subjectKey() == actor.value(). Taking .value()
 * out is how an employee id and a user id become comparable by accident (ADR-0033 section 3, guard 14); the comparison belongs on the
 * typed ids. A comparison with a constant, and ordering (&lt;, &gt;), are not equality of two people and pass.
 *
 * It also finds a comparison between two different typed ids, which Error Prone cannot see because every record is comparable with
 * every other record: userId.equals(employeeId), Objects.equals(userId, employeeId), and the lookup of one kind of id in a collection
 * of another (Set&lt;UserId&gt;.contains(employeeId), Map&lt;UserId, …&gt;.get / containsKey / remove, List indexOf).
 *
 * <p>The scan follows the operand stack of each method in a straight line: a call of value() on a typed id leaves a marked operand,
 * boxing keeps the mark, and a comparison instruction or equals call that consumes a marked operand against another operand that is
 * not a constant is reported. Where the stack cannot be followed (the code after a switch, for instance) it loses the marks and
 * reports nothing.
 *
 * <p>Boundary of the cross-type part. The type of an operand is known from the descriptor of the field or method that produced it,
 * from the local variable table (Maven compiles with -g), and from a cast; the element type of a collection from a generic signature
 * of a local variable, or from the generic type of a field or a method of this code base. Not seen: the element type of a collection
 * that comes out of the JDK or a library (stream results, Optional chains, Map.values()), a nested generic such as the value of a
 * Map&lt;K, Set&lt;V&gt;&gt;, an id held in a variable typed Object, raw collections, containsAll / removeAll / retainAll with another
 * collection, and code behind a switch or a local variable slot whose type cannot be told. Those pass; the reverse tests of the
 * "own person" gates cover them.
 */
final class TypedIdComparisons {

    record Violation(String where, int line, String what) {
        @Override
        public String toString() {
            return where + " line " + line + ": " + what;
        }
    }

    private enum Kind { OTHER, CONSTANT, ID_VALUE, COMPARED }

    private record Operand(Kind kind, String idType, Operand left, Operand right, String type, String key) {
        static final Operand OTHER = new Operand(Kind.OTHER, null, null, null, null, null);
        static final Operand CONSTANT = new Operand(Kind.CONSTANT, null, null, null, null, null);

        static Operand idValue(String idType) {
            return new Operand(Kind.ID_VALUE, idType, null, null, null, null);
        }

        static Operand compared(Operand left, Operand right) {
            return new Operand(Kind.COMPARED, null, left, right, null, null);
        }

        /** type is the internal name of the class of an object operand; key the typed id that the collection or map it is holds, if known. */
        static Operand typed(String type, String key) {
            return type == null ? OTHER : new Operand(Kind.OTHER, null, null, null, type, key);
        }
    }

    private record Variable(int index, String descriptor, String signature, int start, int end) {
    }

    private static final Pattern SIGNATURE = Pattern.compile("^L([\\w/$]+)<[+-]?L([\\w/$]+)[;<]");
    private static final Set<String> LOOKUPS = Set.of("contains", "remove", "indexOf", "lastIndexOf", "get", "containsKey", "getOrDefault");

    private static final class Tables {
        final Predicate<String> typedId;
        final ClassLoader loader = TypedIdComparisons.class.getClassLoader();
        final Map<String, List<Variable>> variables = new HashMap<>();

        Tables(Predicate<String> typedId) {
            this.typedId = typedId;
        }

        Class<?> load(String internalName) {
            try {
                return Class.forName(internalName.replace('/', '.'), false, loader);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }

        boolean ours(String internalName) {
            return internalName.startsWith("com/forcitis/one/");
        }

        boolean isCollectionOrMap(String internalName) {
            Class<?> type = load(internalName);
            return type != null && (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type));
        }

        /** The typed id that a collection or map holds (its first type argument), from a reflected generic type. */
        String keyOf(java.lang.reflect.Type declared) {
            if (declared instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw
                    && (Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)) && parameterized.getActualTypeArguments().length > 0) {
                java.lang.reflect.Type argument = parameterized.getActualTypeArguments()[0];
                if (argument instanceof WildcardType wildcard) {
                    argument = wildcard.getUpperBounds()[0];
                }
                if (argument instanceof Class<?> type && typedId.test(Type.getInternalName(type))) {
                    return Type.getInternalName(type);
                }
            }
            return null;
        }

        /** The same from a generic signature of the class file, such as Ljava/util/Set&lt;Lcom/forcitis/one/kernel/UserId;&gt;;. */
        String keyOf(String signature) {
            if (signature == null) {
                return null;
            }
            Matcher matcher = SIGNATURE.matcher(signature);
            if (matcher.find() && typedId.test(matcher.group(2)) && isCollectionOrMap(matcher.group(1))) {
                return matcher.group(2);
            }
            return null;
        }

        String keyOfField(String owner, String name) {
            Class<?> type = ours(owner) ? load(owner) : null;
            if (type == null) {
                return null;
            }
            try {
                return keyOf(type.getDeclaredField(name).getGenericType());
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }

        String keyOfMethod(String owner, String name, String descriptor) {
            Class<?> type = ours(owner) ? load(owner) : null;
            if (type == null) {
                return null;
            }
            try {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                        return keyOf(method.getGenericReturnType());
                    }
                }
                for (Method method : type.getMethods()) {
                    if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                        return keyOf(method.getGenericReturnType());
                    }
                }
            } catch (LinkageError unavailable) {
                return null;
            }
            return null;
        }
    }

    private static final Set<String> BOXES = Set.of("java/lang/Long", "java/lang/Integer", "java/lang/Short", "java/lang/Byte");

    private TypedIdComparisons() {
    }

    static List<Violation> inspect(InputStream classFile, Predicate<String> typedId) throws IOException {
        List<Violation> found = new ArrayList<>();
        ClassReader reader = new ClassReader(classFile.readAllBytes());
        String owner = Type.getObjectType(reader.getClassName()).getClassName();
        Tables tables = new Tables(typedId);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                List<Variable> variables = tables.variables.computeIfAbsent(name + descriptor, key -> new ArrayList<>());
                Map<Label, Integer> order = new IdentityHashMap<>();
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLabel(Label label) {
                        order.put(label, order.size());
                    }

                    @Override
                    public void visitLocalVariable(String variable, String variableDescriptor, String variableSignature, Label start, Label end,
                            int index) {
                        variables.add(new Variable(index, variableDescriptor, variableSignature, order.getOrDefault(start, 0),
                                order.getOrDefault(end, Integer.MAX_VALUE)));
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new Scan(owner + "." + name, tables, tables.variables.get(name + descriptor), found);
            }
        }, ClassReader.SKIP_FRAMES);
        return found;
    }

    private static final class Scan extends MethodVisitor {
        private final String where;
        private final Tables tables;
        private final Predicate<String> typedId;
        private final List<Variable> variables;
        private final List<Violation> found;
        private final Deque<Operand> stack = new ArrayDeque<>();
        private int line;
        private int labels;

        Scan(String where, Tables tables, List<Variable> variables, List<Violation> found) {
            super(Opcodes.ASM9);
            this.where = where;
            this.tables = tables;
            this.typedId = tables.typedId;
            this.variables = variables == null ? List.of() : variables;
            this.found = found;
        }

        /** Labels are numbered in the order they are visited, in both passes alike; a variable lives from its start label to its end label. */
        @Override
        public void visitLabel(Label label) {
            labels++;
        }

        private Variable variableAt(int index) {
            Variable result = null;
            int current = labels - 1;
            for (Variable variable : variables) {
                if (variable.index() == index && variable.start() <= current && current < variable.end()) {
                    result = variable;
                }
            }
            return result;
        }

        private static String internalNameOf(String descriptor) {
            Type type = Type.getType(descriptor);
            return type.getSort() == Type.OBJECT ? type.getInternalName() : null;
        }

        private boolean isId(String internalName) {
            return internalName != null && typedId.test(internalName);
        }

        private void checkTwoKinds(String left, String right) {
            if (isId(left) && isId(right) && !left.equals(right)) {
                found.add(new Violation(where, line, "compares a " + Type.getObjectType(left).getClassName() + " with a "
                        + Type.getObjectType(right).getClassName() + "; they are different people"));
            }
        }

        private void checkLookup(Operand collection, Operand argument) {
            if (collection.key() != null && isId(argument.type()) && !collection.key().equals(argument.type())) {
                found.add(new Violation(where, line, "looks up a " + Type.getObjectType(argument.type()).getClassName() + " in a collection of "
                        + Type.getObjectType(collection.key()).getClassName()));
            }
        }

        private Operand pop() {
            return stack.isEmpty() ? Operand.OTHER : stack.pop();
        }

        private void push(Operand operand) {
            stack.push(operand);
        }

        private void pop(int count) {
            for (int i = 0; i < count; i++) {
                pop();
            }
        }

        private void compare(Operand a, Operand b) {
            boolean raw = a.kind() == Kind.ID_VALUE || b.kind() == Kind.ID_VALUE;
            boolean constant = a.kind() == Kind.CONSTANT || b.kind() == Kind.CONSTANT;
            if (raw && !constant) {
                String id = a.kind() == Kind.ID_VALUE ? a.idType() : b.idType();
                found.add(new Violation(where, line, "compares the raw value of " + id + "; compare the typed ids"));
            }
        }

        @Override
        public void visitLineNumber(int number, Label start) {
            line = number;
        }

        @Override
        public void visitInsn(int opcode) {
            switch (opcode) {
                case Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                        Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1,
                        Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1 -> push(Operand.CONSTANT);
                case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD,
                        Opcodes.SALOAD -> {
                    pop(2);
                    push(Operand.OTHER);
                }
                case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE,
                        Opcodes.CASTORE, Opcodes.SASTORE -> pop(3);
                case Opcodes.POP, Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> pop();
                case Opcodes.DUP -> {
                    Operand top = pop();
                    push(top);
                    push(top);
                }
                case Opcodes.POP2, Opcodes.DUP_X1, Opcodes.DUP_X2, Opcodes.DUP2, Opcodes.DUP2_X1, Opcodes.DUP2_X2, Opcodes.SWAP -> stack.clear();
                case Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L, Opcodes.F2D,
                        Opcodes.D2I, Opcodes.D2L, Opcodes.D2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S -> {
                    // a widening or narrowing keeps the operand: (int) id.value() is still the raw value
                }
                case Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG, Opcodes.ARRAYLENGTH -> {
                    pop();
                    push(Operand.OTHER);
                }
                case Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> {
                    Operand right = pop();
                    Operand left = pop();
                    push(Operand.compared(left, right));
                }
                case Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.RETURN,
                        Opcodes.ATHROW -> stack.clear();
                default -> {
                    if (opcode >= Opcodes.IADD && opcode <= Opcodes.LXOR) {
                        pop(2);
                        push(Operand.OTHER);
                    }
                }
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == Opcodes.NEWARRAY) {
                pop();
                push(Operand.OTHER);
            } else {
                push(Operand.CONSTANT);
            }
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (opcode == Opcodes.ALOAD) {
                Variable variable = variableAt(varIndex);
                push(variable == null ? Operand.OTHER : Operand.typed(internalNameOf(variable.descriptor()), tables.keyOf(variable.signature())));
            } else if (opcode >= Opcodes.ILOAD && opcode < Opcodes.ALOAD) {
                push(Operand.OTHER);
            } else if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
                pop();
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            switch (opcode) {
                case Opcodes.NEW -> push(Operand.OTHER);
                case Opcodes.ANEWARRAY, Opcodes.INSTANCEOF -> {
                    pop();
                    push(Operand.OTHER);
                }
                case Opcodes.CHECKCAST -> {
                    Operand cast = pop();
                    push(Operand.typed(type, cast.key()));
                }
                default -> {
                }
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            switch (opcode) {
                case Opcodes.GETSTATIC -> push(Operand.typed(internalNameOf(descriptor), tables.keyOfField(owner, name)));
                case Opcodes.PUTSTATIC -> pop();
                case Opcodes.GETFIELD -> {
                    pop();
                    push(Operand.typed(internalNameOf(descriptor), tables.keyOfField(owner, name)));
                }
                default -> pop(2);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            Type[] arguments = Type.getArgumentTypes(descriptor);
            Operand[] taken = new Operand[arguments.length];
            for (int i = arguments.length - 1; i >= 0; i--) {
                taken[i] = pop();
            }
            Operand receiver = opcode == Opcodes.INVOKESTATIC ? null : pop();
            Operand result = Operand.OTHER;
            if (name.equals("value") && arguments.length == 0 && receiver != null && typedId.test(owner)) {
                result = Operand.idValue(Type.getObjectType(owner).getClassName());
            } else if (opcode == Opcodes.INVOKESTATIC && BOXES.contains(owner) && name.equals("valueOf") && arguments.length == 1
                    && taken[0].kind() == Kind.ID_VALUE) {
                result = taken[0];
            } else if (name.equals("equals") && descriptor.equals("(Ljava/lang/Object;)Z") && receiver != null) {
                compare(receiver, taken[0]);
                checkTwoKinds(isId(owner) ? owner : receiver.type(), taken[0].type());
            } else if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/util/Objects") && name.equals("equals") && arguments.length == 2) {
                compare(taken[0], taken[1]);
                checkTwoKinds(taken[0].type(), taken[1].type());
            } else if (receiver != null && owner.startsWith("java/util/") && LOOKUPS.contains(name) && arguments.length > 0
                    && arguments[0].getDescriptor().equals("Ljava/lang/Object;")) {
                checkLookup(receiver, taken[0]);
            }
            if (Type.getReturnType(descriptor).getSort() != Type.VOID) {
                if (result == Operand.OTHER) {
                    String key = tables.keyOfMethod(owner, name, descriptor);
                    if (key == null && receiver != null && name.equals("keySet") && owner.equals("java/util/Map")) {
                        key = receiver.key();
                    }
                    result = Operand.typed(internalNameOf(Type.getReturnType(descriptor).getDescriptor()), key);
                }
                push(result);
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, org.springframework.asm.Handle bootstrap, Object... arguments) {
            pop(Type.getArgumentTypes(descriptor).length);
            if (Type.getReturnType(descriptor).getSort() != Type.VOID) {
                push(Operand.OTHER);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            switch (opcode) {
                case Opcodes.IFEQ, Opcodes.IFNE -> {
                    Operand tested = pop();
                    if (tested.kind() == Kind.COMPARED) {
                        compare(tested.left(), tested.right());
                    }
                }
                case Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> pop();
                case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                    Operand right = pop();
                    compare(pop(), right);
                }
                case Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE -> pop(2);
                default -> {
                    // GOTO leaves the stack as it is
                }
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            push(Operand.CONSTANT);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            pop();
            stack.clear();
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            pop();
            stack.clear();
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
            pop(dimensions);
            push(Operand.OTHER);
        }
    }
}
