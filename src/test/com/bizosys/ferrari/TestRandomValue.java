package com.bizosys.ferrari;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bizosys.oneline.util.StringUtils;

import junit.framework.Test;

public class TestRandomValue  {

	protected Map<String, Float> responseTimes = new HashMap<String, Float >();
	protected int totalRun = 0;
	protected int totalSucess = 0;
	protected List<String> failedFunctions = new ArrayList<String>();
	protected int iteration = 1;
	protected boolean verbose = true;
	protected boolean displayText = true;
	
	public void run(Test testCase) throws Exception {
		if ( displayText ) DisplayText.printStartLabel(testCase.getClass().getName());
		Method[] allMethods = testCase.getClass().getDeclaredMethods();
		if ( null == allMethods) {
			System.out.println("... There is no methods available in the testcase.");
			return;
		}
		for (int i = 0; i < allMethods.length; i++) {
			Method aMethod = allMethods[i];
			if ( "main".equals(aMethod.getName()) ) continue;
			runMethod(aMethod, testCase);

		}    
		if ( displayText ) DisplayText.printEndLabel(testCase.getClass().getName());
	}
	
	protected void runMethod(Method runMethod, Test testCase) {
		if (! Modifier.isPublic(runMethod.getModifiers())) {
			return;
		}
		String methodName = runMethod.getName();
		if ( "main".equals(methodName) ) return;
		
		if ( verbose )
			System.out.println("\n... Checking Random Value test method >>" +  methodName + "<<" );
		Class[] params = runMethod.getParameterTypes();
		int iteration = getIterations();
		List<Object[]> values = new ArrayList<Object[]>(iteration);
		
		for (int i=0; i< iteration; i++) {
			values.add(new Object[params.length]); 
		}
		
		int counter = 0;
		for (Class param : params) {
			
			if ( String.class == param) {
				List<String> samples = DataRandomPrimitives.getString(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Double.class == param) { 
				List<Double> samples = DataRandomPrimitives.getDouble(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Long.class == param) { 
				List<Long> samples = DataRandomPrimitives.getLong(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Integer.class == param) { 
				List<Integer> samples = DataRandomPrimitives.getInteger(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Float.class == param) { 
				List<Float> samples = DataRandomPrimitives.getFloat(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Short.class == param) { 
				List<Short> samples = DataRandomPrimitives.getShort(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Byte.class == param) { 
				List<Byte> samples = DataRandomPrimitives.getByte(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Boolean.class == param) { 
				List<Boolean> samples = DataRandomPrimitives.getBoolean(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			} else if (Date.class == param) { 
				List<Date> samples = DataRandomPrimitives.getDates(iteration);
				for ( int i=0; i< iteration; i++) values.get(i)[counter] = samples.get(i);
			}

			counter++;
		}
		try {
			totalRun++;
			for (Object[] objects : values) {
				invoke(runMethod, testCase, objects);
			}
			totalSucess++;
		} catch (Exception ex) {
			StringBuilder sb = new StringBuilder();
			sb.append(testCase.getClass().getName() + ":" + runMethod.getName()); 
			for (Object[] objects : values) {
				if ( null == objects) sb.append(" [Params : N/A]");
				else {
					for (Object obj : objects ) {
						if ( null == obj) sb.append(" [Param : Null] " );
						else sb.append(" [Param : " + obj.toString() + "]");
					}
				}
			}
			
			System.out.println("########## Failed :" + sb.toString());
			ex.printStackTrace(System.err);
			failedFunctions.add(runMethod.getName());
		}
	}

	protected void invoke(Method runMethod, Test testCase, Object[] values) throws Exception {
		runMethod.invoke(testCase, values);
		this.totalSucess++;
	}
	
	public int getIterations() {
		return this.iteration;
	}
	
	public void setIterations(int itr) {
		this.iteration = itr;
	}	

	public String toString() {
		StringBuilder sb = new StringBuilder(100);
		String className = this.getClass().getName();
		sb.append(className.substring(className.lastIndexOf('.') + 1));
		sb.append("  | Run :").append(totalRun).append("   ,   ");
		sb.append("Sucess :").append(totalSucess).append("   ,   ");
		sb.append("Failed : ");
		if ( null == failedFunctions || 0 == failedFunctions.size())
			sb.append("None");
		else sb.append(StringUtils.listToString(failedFunctions, ',') );
		return sb.toString();
	}
    
    public static void main(String[] args) throws Exception {
        Test testCase = new DryRunTest();
        TestRandomValue tester = new TestRandomValue();
        tester.setIterations(5);
        tester.run(testCase);
        System.out.println(tester);
    }
	
}