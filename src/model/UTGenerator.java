package model;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Set;

import com.singularsys.jep.ParseException;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import utils.StoreFile;

public class UTGenerator
{
	// R. Pawlak, C. Noguera, and N. Petitprez. Spoon:
	// Program analysis and transformation in java.
	// Technical Report 5901, INRIA, 2006.

	private SpoonedClass		_spoonedClass;
	private Set<MethodToSelect>	_methods;
	private final String		compiledClassFolder	= "bin";
	private final String		packetNameOutput	= "tmp";
	private String				_packetClass;
	private TestClass			_testClass;

	public UTGenerator(SpoonedClass spoonedClass, Set<MethodToSelect> methods)
	{
		this._spoonedClass = spoonedClass;
		this._packetClass = this._spoonedClass.getSpoonedClass().getPackage().getSimpleName();
		this._methods = methods;
	}

	public String generarCasos(int k)
	{
		_testClass = new TestClass(_spoonedClass.getSpoonedClass().getSimpleName(), this._packetClass);
		// System.out.println(_spoonedClass.getSpoonedClass().toString());
		CtClass<?> ctClass = _spoonedClass.getSpoonedClass();
		Factory factory = _spoonedClass.getFactory();

		for (MethodToSelect M : this._methods)
		{
			if (!M.isSelected())
				continue;

			CtMethod<?> actualMethod = M.getCtMethod();

			// XXXXXX: Primero instrumento los métodos seleccionados
			try
			{
				instrument(k, ctClass, factory, actualMethod);
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// XXXXXX: Guardo la clase, la compilo y la cargo
		try
		{
			storeClass(ctClass);
		} catch (IOException e)
		{
			// TODO: tratar excepcion
			e.printStackTrace();
		}

		Class<?> classInstrumented = null;
		try
		{
			// System.out.println("package " + packetNameOutput + ";\n" +
			// ctClass.toString());
			// TODO tmp... antes de compilar borro .class si está generado
			String qualifiedClassName = packetNameOutput + "." + ctClass.getSimpleName();

			classInstrumented = CompilerTool.CompileAndGetClass(qualifiedClassName,
					"package " + packetNameOutput + ";\n" + ctClass.toString(), compiledClassFolder);
		} catch (ClassNotFoundException e)
		{
			// TODO: tratar excepcion
			e.printStackTrace();
		} catch (MalformedURLException e)
		{
			// TODO: tratar excepcion
			e.printStackTrace();
		}

		// XXXXXX: Ejecuto cada método instrumentado y guardo los valores
		// obtenidos por el solver
		for (MethodToSelect M : this._methods)
		{
			if (!M.isSelected())
				continue;
			CtMethod<?> actualMethod = M.getCtMethod();

			// XXXXXX: Obtengo los valores para testear el método
			InstrumentedMethod instrumentedMethod = new InstrumentedMethod(classInstrumented);
			List<List<Integer>> inputsGenerated = null;
			try
			{
				Class<?>[] parameterTypes = new Class<?>[actualMethod.getParameters().size()];
				for (int i = 0; i < actualMethod.getParameters().size(); i++)
				{
					parameterTypes[i] = actualMethod.getParameters().get(i).getType().getActualClass();
					// System.out.println(parameterTypes[i]);
				}
				inputsGenerated = instrumentedMethod.generateInputs(actualMethod.getSimpleName(), parameterTypes);
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// XXXXXX: creo los casos de test para los inputs generados
			for (List<Integer> inputs : inputsGenerated)
			{
				_testClass.addMethodTest(M.getCtMethod().getSimpleName(), inputs);
			}
		}
		// XXXXXX: Devuelvo el String con la clase que contiene los casos de
		// test
		return _testClass.getGenerateTestClass();
	}

	private void instrument(int k, CtClass<?> ctClass, Factory factory, CtMethod<?> actualMethod) throws Exception
	{
		Instrumentator instrumentator = new Instrumentator(actualMethod, factory);
		try
		{
			if (instrumentator.preProcessLoop(k))
			{
				storeClass(ctClass);

				SpoonedClass sc = new SpoonedClass(_spoonedClass.getPathJavaFile());
				sc.loadClass();
				instrument(k, sc.getSpoonedClass(), sc.getFactory(), sc.getSpoonedMethod(actualMethod.getSimpleName()));
				return;
			}

			instrumentator.process(k);
		} catch (ParseException e)
		{
			// TODO: tratar excepcion
			e.printStackTrace();
		}
	}

	public Integer getGeneratedMethodsCount()
	{
		return this._testClass.getGeneratedMethodsCount();
	}

	private void storeClass(CtClass<?> ctClas) throws IOException
	{
		String className = ctClas.getSimpleName();
		String Stringclass = "package " + packetNameOutput + ";\n" + ctClas.toString();
		StoreFile sf = new StoreFile(packetNameOutput + "/", ".java", Stringclass, className, "utf-8");
		sf.store();
	}

}
