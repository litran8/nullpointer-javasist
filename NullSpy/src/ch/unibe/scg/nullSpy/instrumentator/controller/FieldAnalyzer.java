package ch.unibe.scg.nullSpy.instrumentator.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import ch.unibe.scg.nullSpy.model.Field;
import ch.unibe.scg.nullSpy.model.IndirectFieldObject;
import ch.unibe.scg.nullSpy.model.Variable;

/**
 * Instruments test-code after fields.
 */
public class FieldAnalyzer extends VariableAnalyzer {

	private ArrayList<Variable> fieldIsWritterInfoList;

	public FieldAnalyzer(CtClass cc, ArrayList<Variable> fieldIsWritterInfoList) {
		super(cc);
		this.fieldIsWritterInfoList = fieldIsWritterInfoList;
	}

	/**
	 * Search all fields and store them in an arrayList. It instruments code
	 * after each field assignments.
	 * 
	 * @param this.cc
	 * @param myClass
	 * @throws CannotCompileException
	 * @throws BadBytecode
	 * @throws NotFoundException
	 */
	public void instrumentAfterFieldAssignment() throws CannotCompileException,
			BadBytecode, NotFoundException {

		cc.instrument(new ExprEditor() {
			public void edit(FieldAccess field) throws CannotCompileException {

				if (field.isWriter()) {
					try {
						Variable var = null;

						// Printer p = new Printer();
						// System.out.println("\nBefore:");
						// p.printMethod(field.where(), 0);

						// fieldType is an object -> starts with L.*
						if (isFieldNotPrimitive(field)) {

							if (isFieldFromCurrentCtClass(field)) {
								// direct fields
								var = storeFieldOfCurrentClass(field);

							} else {
								// indirect fields
								var = storeFieldOfAnotherClass(field);
							}

							// System.out.println("Method: "
							// + var.getBehavior().getName());

							// insert code after assignment
							adaptByteCode(var);
						}
					} catch (NotFoundException | BadBytecode e) {
						e.printStackTrace();
					}
				}
			}

		});

		// Printer p = new Printer();
		//
		// CtBehavior classInit = cc.getClassInitializer();
		// if (classInit != null) {
		// System.out.println("\n" + classInit.getName());
		// p.printMethod(classInit, 0);
		// }
		//
		// for (CtBehavior b : cc.getDeclaredConstructors()) {
		//
		// if (b.getMethodInfo().getCodeAttribute() != null) {
		// System.out.println("\n" + b.getName());
		// System.out.println(b.getSignature());
		// p.printMethod(b, 0);
		// }
		// }
		//
		// for (CtBehavior b : cc.getDeclaredMethods()) {
		// if (b.getMethodInfo().getCodeAttribute() != null) {
		// System.out.println("\n" + b.getName());
		// p.printMethod(b, 0);
		// }
		// }
		//
		// System.out.println();
	}

	/**
	 * If the field is of another class than the current analyzed one. E.g. p.a:
	 * p->PersonClass
	 * 
	 * @param field
	 * @throws NotFoundException
	 * @throws BadBytecode
	 */
	private Variable storeFieldOfAnotherClass(FieldAccess field)
			throws NotFoundException, BadBytecode {

		CtBehavior behavior = field.where();
		boolean isAnotherClassAnInnerClass = isAnotherClassAnInnerClass(field);

		CodeAttribute codeAttribute = behavior.getMethodInfo()
				.getCodeAttribute();
		CodeIterator codeIterator = codeAttribute.iterator();

		LineNumberAttribute lineNrAttr = (LineNumberAttribute) codeAttribute
				.getAttribute(LineNumberAttribute.tag);

		LocalVariableAttribute localVariableTable = (LocalVariableAttribute) codeAttribute
				.getAttribute(LocalVariableAttribute.tag);
		ArrayList<LocalVariableTableEntry> localVariableTableList = getStableLocalVariableTableAsList(localVariableTable);

		int pos = getPos(field);
		int startPos = getStartPos(field, pos);
		System.out.println();
		codeIterator.move(pos);
		codeIterator.next();

		int afterPos = 0;
		if (codeIterator.hasNext()) {
			afterPos = codeIterator.next();
		}

		int fieldLineNr = lineNrAttr.toLineNumber(pos);

		// innerclass
		String innerClassFieldName = "";

		String fieldName = field.getFieldName();

		// object_FIELD
		int op = codeIterator.byteAt(startPos);
		String opCode_field = Mnemonic.OPCODE[op];

		// OBJECT_field
		IndirectFieldObject indirectFieldObject;

		String objectName_field = "";
		String objectType_field = "";
		String objectBelongedClassName_field = "";
		boolean isfieldStatic_field = false;

		if (!field.isStatic()) {

			if (Mnemonic.OPCODE[op].matches("aload.*")) {
				// localVar.field
				// store locVar e.g. p.a -> get p
				int localVarTableIndex = 0;

				localVarTableIndex = getLocalVarTableIndex(codeIterator,
						localVariableTableList, startPos, "aload.*");
				int locVarSlot = getLocVarArraySlot(codeIterator, startPos);
				opCode_field = "aload_" + locVarSlot;

				// localVarName.field
				objectName_field = localVariableTableList
						.get(localVarTableIndex).varName;
				objectType_field = localVariableTableList
						.get(localVarTableIndex).varType;

			} else {
				// field.field
				String instruction = InstructionPrinter.instructionString(
						codeIterator, startPos, field.where().getMethodInfo2()
								.getConstPool());
				int brace = instruction.indexOf("(");
				instruction = instruction.substring(
						instruction.lastIndexOf(".") + 1, brace);
				objectName_field = instruction;

				CtField ctField_field = cc.getField(objectName_field);

				objectBelongedClassName_field = ctField_field
						.getDeclaringClass().getName();
				objectType_field = ctField_field.getSignature();
				isfieldStatic_field = op == Opcode.GETSTATIC;
				// testMethodByteCode.addGetfield(belongedClassNameOfVariable,
				// variableName, variableType);
			}

		}

		if (isAnotherClassAnInnerClass && objectName_field.equals("this")) {
			// innerClass: this.innerClassField.field
			codeIterator.move(startPos);
			codeIterator.next();
			int innerClassGetFieldPos = codeIterator.next();
			op = codeIterator.byteAt(innerClassGetFieldPos);
			opCode_field = Mnemonic.OPCODE[op];
			int index = codeIterator.u16bitAt(innerClassGetFieldPos + 1);

			// innerClassField_field
			innerClassFieldName = behavior.getMethodInfo2().getConstPool()
					.getFieldrefName(index);

			CtField ctField_field = cc.getField(innerClassFieldName);
			objectName_field = ctField_field.getName();
			objectBelongedClassName_field = ctField_field.getDeclaringClass()
					.getName();
			objectType_field = ctField_field.getSignature();
			isfieldStatic_field = op == Opcode.GETSTATIC;
		}

		indirectFieldObject = new IndirectFieldObject(objectName_field,
				objectType_field, objectBelongedClassName_field,
				isfieldStatic_field, opCode_field);

		String fieldType = field.getSignature();
		String fieldBelongedClassName = field.getClassName();

		Field var = new Field("field", fieldName, fieldType,
				fieldBelongedClassName, fieldLineNr, pos, startPos, afterPos,
				cc, behavior, field.isStatic(), indirectFieldObject);

		fieldIsWritterInfoList.add(var);

		return var;
	}

	private boolean isAnotherClassAnInnerClass(FieldAccess field)
			throws NotFoundException {
		for (CtClass c : cc.getNestedClasses()) {
			if (c.getName().equals(field.getClassName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * If the field is of the current analyzed class.
	 * 
	 * @param field
	 * @throws NotFoundException
	 * @throws BadBytecode
	 */
	private Variable storeFieldOfCurrentClass(FieldAccess field)
			throws NotFoundException, BadBytecode {

		CtBehavior behavior = field.where();
		int fieldLineNr = field.getLineNumber();
		int pos = 0;
		int startPos = 0;
		int posAfterAssignment = 0;

		// if field is initiated outside a method -> method is null
		if (behavior != null) {
			CodeAttribute codeAttribute = behavior.getMethodInfo()
					.getCodeAttribute();
			CodeIterator codeIterator = codeAttribute.iterator();

			pos = getPos(field);
			startPos = getStartPos(field, pos);
			codeIterator.move(pos);
			codeIterator.next();

			if (codeIterator.hasNext()) {
				posAfterAssignment = codeIterator.next();
			}

			LineNumberAttribute lineNrAttr = (LineNumberAttribute) codeAttribute
					.getAttribute(LineNumberAttribute.tag);
			fieldLineNr = lineNrAttr.toLineNumber(pos);

		} else {

			// field instantiated outside behavior, that means insert in every
			// constructor

			CtBehavior voidConstructor = cc.getDeclaredConstructors()[0];
			CodeAttribute codeAttr = voidConstructor.getMethodInfo()
					.getCodeAttribute();
			CodeIterator codeIter = codeAttr.iterator();

			if (fieldIsWritterInfoList.size() != 0) {

				if (field.isStatic()) {
					int fieldListSize = fieldIsWritterInfoList.size();

					Variable lastOutsideInstatiatedStaticField = null;

					// gets the last static outside field
					for (int i = fieldListSize - 1; i >= 0; i--) {
						lastOutsideInstatiatedStaticField = fieldIsWritterInfoList
								.get(i);

						if (lastOutsideInstatiatedStaticField.getBehavior() == null
								&& lastOutsideInstatiatedStaticField.isStatic()
								&& cc.getName().equals(
										lastOutsideInstatiatedStaticField
												.getBelongedClass().getName())) {
							break;
						}
					}

					if (lastOutsideInstatiatedStaticField == null) {
						// first static outside field
						pos = 1;
					} else {
						// set pos to last static outside field afterPos
						// iterate until inovke.* -> pos = invoke.*-pc
						codeIter.move(lastOutsideInstatiatedStaticField
								.getAfterPos());
						pos = codeIter.next();
						int op = codeIter.byteAt(pos);

						while (!Mnemonic.OPCODE[op].matches("invoke.*")) {
							pos = codeIter.next();
							op = codeIter.byteAt(pos);
						}
					}
				} else {
					pos = field.indexOfBytecode();
				}

			} else {

				// first field (static or nonStatic)
				// should be pc 1
				pos = field.indexOfBytecode();
			}

			codeIter.move(pos);
			codeIter.next();
			posAfterAssignment = codeIter.next();

		}

		String fieldName = field.getFieldName();
		String fieldType = field.getSignature();
		String fieldBelongedClassName = cc.getName();

		Field var = new Field("field", fieldName, fieldType,
				fieldBelongedClassName, fieldLineNr, pos, startPos,
				posAfterAssignment, cc, behavior, field.isStatic(), null);
		fieldIsWritterInfoList.add(var);
		return var;
	}

	private int getStartPos(FieldAccess field, int pos) throws BadBytecode {
		int res = 0;
		CtBehavior behavior = field.where();
		CodeAttribute codeAttr = behavior.getMethodInfo().getCodeAttribute();
		CodeIterator iter = codeAttr.iterator();

		LineNumberAttribute lineNrAttr = (LineNumberAttribute) codeAttr
				.getAttribute(LineNumberAttribute.tag);
		int line = lineNrAttr.toLineNumber(pos);
		res = lineNrAttr.toStartPc(line);
		HashMap<Integer, Integer> lineNrMap = getLineNumberMap(behavior);

		Object[] keys = lineNrMap.keySet().toArray();
		Arrays.sort(keys);

		if (fieldIsWritterInfoList.size() != 0) {
			Variable lastVar = fieldIsWritterInfoList
					.get(fieldIsWritterInfoList.size() - 1);

			if (isSameBehavior(field, lastVar)) {

				iter.move(lastVar.getStorePos());
				iter.next();
				int nextPosAfterLastVar = iter.next();

				if (iter.hasNext() && nextPosAfterLastVar == res) {
					int op = iter.byteAt(res);
					String instr = Mnemonic.OPCODE[op];
					if (instr.matches("ldc.*")) {

						while (iter.hasNext()
								&& !instr.matches("invokestatic.*")) {
							nextPosAfterLastVar = iter.next();
							op = iter.byteAt(nextPosAfterLastVar);
							instr = Mnemonic.OPCODE[op];
						}
						res = iter.next();
					}
				}
			}
		}

		return res;
	}

	private int getPos(FieldAccess field) throws BadBytecode {
		CtBehavior behavior = field.where();
		CodeAttribute codeAttr = behavior.getMethodInfo().getCodeAttribute();
		CodeIterator codeIter = codeAttr.iterator();

		ConstPool constPool = behavior.getMethodInfo2().getConstPool();

		int fieldListSize = fieldIsWritterInfoList.size();

		int pos = 0;

		if (fieldListSize == 0) {
			// list is empty, read pos from original codeAttr
			pos = field.indexOfBytecode();
		} else if (fieldListSize != 0) {
			// list is not empty
			Variable lastVar = fieldIsWritterInfoList.get(fieldListSize - 1);
			CtBehavior lastFieldBehavior = lastVar.getBehavior();

			if (isSameBehavior(behavior, lastFieldBehavior)) {
				// last field is in same behavior -> set pos to last field's pos
				// because codeAttr has changed, not the original anymore
				pos = lastVar.getAfterPos();
			} else {
				// last field not from same behavior -> codeAttr of the current
				// behavior is still the original one
				field.indexOfBytecode();
			}
		}

		// set codeIter to pos
		codeIter.move(pos);
		int op = codeIter.byteAt(codeIter.next());

		// iterate until opcode put.*
		while ((op != Opcode.PUTFIELD) && (op != Opcode.PUTSTATIC)) {
			pos = codeIter.next();
			op = codeIter.byteAt(pos);
		}

		// get signature of the field at pos
		// this is for skipping every primitive field
		int index = codeIter.u16bitAt(pos + 1);
		String signatureOfTestPos = constPool.getFieldrefType(index);
		String signature = field.getSignature();

		// if is not the same signature
		// iterate to next field, check signature etc.
		while (!signature.equals(signatureOfTestPos)) {
			pos = codeIter.next();
			op = codeIter.byteAt(pos);

			while ((op != Opcode.PUTFIELD) && (op != Opcode.PUTSTATIC)) {
				pos = codeIter.next();
				op = codeIter.byteAt(pos);
			}

			index = codeIter.u16bitAt(pos + 1);
			signatureOfTestPos = constPool.getFieldrefType(index);
		}

		return pos;

	}

	private boolean isSameBehavior(CtBehavior behavior,
			CtBehavior lastFieldBehavior) {
		return behavior.getName().equals(lastFieldBehavior.getName())
				&& behavior.getSignature().equals(
						lastFieldBehavior.getSignature())
				&& behavior
						.getDeclaringClass()
						.getName()
						.equals(lastFieldBehavior.getDeclaringClass().getName());
	}

	private boolean isSameBehavior(FieldAccess field, Variable lastVar) {
		boolean inSameBehavior = false;
		CtBehavior currentBehavior = field.where();
		CtBehavior lastBehavior = lastVar.getBehavior();
		if (!(lastBehavior == null)) {
			inSameBehavior = currentBehavior.getName().equals(
					lastBehavior.getName())
					&& currentBehavior.getDeclaringClass().getName()
							.equals(lastVar.getBelongedClass().getName())
					&& currentBehavior.getSignature().equals(
							lastBehavior.getSignature());
		}
		return inSameBehavior;
	}

	private boolean isFieldFromCurrentCtClass(FieldAccess field)
			throws NotFoundException {
		if (field.getClassName().equals(cc.getName()))
			return true;
		else
			return false;
	}

	private boolean isFieldNotPrimitive(FieldAccess field) {
		if (field.getSignature().matches("L.*"))
			return true;
		else
			return false;
	}

}
