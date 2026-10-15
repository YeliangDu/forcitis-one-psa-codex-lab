package com.forcitis.one;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
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
 * <p>The scan follows the operand stack of each method in a straight line: a call of value() on a typed id leaves a marked operand,
 * boxing keeps the mark, and a comparison instruction or equals call that consumes a marked operand against another operand that is
 * not a constant is reported. Where the stack cannot be followed (the code after a switch, for instance) it loses the marks and
 * reports nothing.
 */
final class TypedIdComparisons {

    record Violation(String where, int line, String what) {
        @Override
        public String toString() {
            return where + " line " + line + ": " + what;
        }
    }

    private enum Kind { OTHER, CONSTANT, ID_VALUE, COMPARED }

    private record Operand(Kind kind, String idType, Operand left, Operand right) {
        static final Operand OTHER = new Operand(Kind.OTHER, null, null, null);
        static final Operand CONSTANT = new Operand(Kind.CONSTANT, null, null, null);

        static Operand idValue(String idType) {
            return new Operand(Kind.ID_VALUE, idType, null, null);
        }

        static Operand compared(Operand left, Operand right) {
            return new Operand(Kind.COMPARED, null, left, right);
        }
    }

    private static final Set<String> BOXES = Set.of("java/lang/Long", "java/lang/Integer", "java/lang/Short", "java/lang/Byte");

    private TypedIdComparisons() {
    }

    static List<Violation> inspect(InputStream classFile, Predicate<String> typedId) throws IOException {
        List<Violation> found = new ArrayList<>();
        ClassReader reader = new ClassReader(classFile);
        String owner = Type.getObjectType(reader.getClassName()).getClassName();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new Scan(owner + "." + name, typedId, found);
            }
        }, ClassReader.SKIP_FRAMES);
        return found;
    }

    private static final class Scan extends MethodVisitor {
        private final String where;
        private final Predicate<String> typedId;
        private final List<Violation> found;
        private final Deque<Operand> stack = new ArrayDeque<>();
        private int line;

        Scan(String where, Predicate<String> typedId, List<Violation> found) {
            super(Opcodes.ASM9);
            this.where = where;
            this.typedId = typedId;
            this.found = found;
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
            if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
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
                default -> {
                    // CHECKCAST keeps the operand
                }
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            switch (opcode) {
                case Opcodes.GETSTATIC -> push(Operand.OTHER);
                case Opcodes.PUTSTATIC -> pop();
                case Opcodes.GETFIELD -> {
                    pop();
                    push(Operand.OTHER);
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
            } else if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/util/Objects") && name.equals("equals") && arguments.length == 2) {
                compare(taken[0], taken[1]);
            }
            if (Type.getReturnType(descriptor).getSort() != Type.VOID) {
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
