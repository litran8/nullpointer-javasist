package run;

import java.util.HashMap;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.Translator;
import controller.Iteration;

public class MyTranslator implements Translator {

	private Iteration iter = Iteration.getInstance();
	private HashMap<String, CtClass> analyzedClasses = new HashMap<String, CtClass>();

	@Override
	public void start(ClassPool pool) throws NotFoundException,
			CannotCompileException {
		// TODO Auto-generated method stub
	}

	@Override
	public void onLoad(ClassPool pool, String className)
			throws NotFoundException, CannotCompileException {

		if (!className.equalsIgnoreCase("controller.Iteration")) {

			CtClass cc;
			if (analyzedClasses.containsKey(className))
				return;
			else {
				cc = ClassPool.getDefault().get(className);
				analyzedClasses.put(cc.getName(), cc);
			}

			cc.stopPruning(true);

			try {
				iter.goThrough(cc);
			} catch (Throwable e) {
				e.printStackTrace();
			}

		}
	}
}
