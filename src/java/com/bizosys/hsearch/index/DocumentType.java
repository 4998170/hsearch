package com.bizosys.hsearch.index;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.bizosys.oneline.ApplicationFault;
import com.bizosys.oneline.services.batch.BatchTask;

import com.bizosys.hsearch.common.Storable;
import com.bizosys.hsearch.hbase.HReader;
import com.bizosys.hsearch.hbase.HWriter;
import com.bizosys.hsearch.hbase.NV;
import com.bizosys.hsearch.schema.IOConstants;
import com.bizosys.hsearch.util.Record;
import com.bizosys.hsearch.util.RecordScalar;

public class DocumentType implements BatchTask {
	
	public static String TYPE_KEY = "DOCUMENT_TYPE";
	public static byte[] TYPE_KEY_BYTES = TYPE_KEY.getBytes();

	public static Byte NONE_TYPECODE = Byte.MIN_VALUE;
	
	public static DocumentType instance = null;
	public static DocumentType getInstance() throws ApplicationFault {
		if ( null != instance ) return instance;
		synchronized (DocumentType.class) {
			if ( null != instance ) return instance;
			instance = new DocumentType();
			return instance;
		}
	}
	
	public Map<String, Byte> types = new HashMap<String, Byte>();
	
	public DocumentType() throws ApplicationFault {
		this.process();
	}
	
	public byte getTypeCode(String type) {
		if (this.types.containsKey(type))
			return this.types.get(type);
		else return ' ';
	}
	
	public void persist() throws IOException {
		
		try {
			int totalSize = 0;
			for (String type : types.keySet()) {
				totalSize = totalSize + 
					1 /** Type char length */  + type.length() + 1 /** Reserved for byte mapping */;  
			}
			if ( 0 == totalSize ) return;
			
			byte[] bytes = new byte[totalSize];
			
			int pos = 0;
			int len = 0;
			for (String type : types.keySet()) {
				len =  type.length();
				bytes[pos++] = (byte)len;
				System.arraycopy(type.getBytes(), 0, bytes, pos,len);
				pos = pos + len;
				bytes[pos++] = types.get(type);
			}
			NV nv = new NV(IOConstants.NAME_VALUE_BYTES, 
				IOConstants.NAME_VALUE_BYTES, new Storable(bytes));
			Record record = new Record(new Storable(TYPE_KEY_BYTES), nv);
			HWriter.update(IOConstants.TABLE_CONFIG, record, true);
		} catch (IOException e) {
			e.printStackTrace(System.out);
			throw e;
		}
	}

	public String getJobName() {
		return "TermType";
	}

	public Object process() throws ApplicationFault {
		NV nv = new NV(IOConstants.NAME_VALUE_BYTES, IOConstants.NAME_VALUE_BYTES);
		
		RecordScalar scalar = new RecordScalar(TYPE_KEY_BYTES, nv);
		HReader.getScalar(IOConstants.TABLE_CONFIG, scalar);
		if ( null == nv.data) return 1;
		
		byte[] bytes = nv.data.toBytes();
		int total = bytes.length;
		
		int pos = 0;
		byte len = 0;
		
		Map<String, Byte> newTypes = new HashMap<String, Byte>();
		while ( pos < total) {
			len =  bytes[pos++];
			byte[] typeB = new byte[len];
			System.arraycopy(bytes, pos, typeB, 0,len);
			pos = pos + len;
			byte typeCode = bytes[pos++];
			newTypes.put(new String(typeB), typeCode);
		}
		Map<String, Byte> temp = this.types;
		this.types = newTypes;
		temp.clear();
		temp = null;
		return 0;
	}

	public void setJobName(String jobName) {
	}
	
	public static void main(String[] args) throws Exception {
		DocumentType type = new DocumentType();
		type.types.put("employee", (byte) -128);
		type.types.put("customer", (byte) -127);
		type.persist();
		
		System.out.println(type.types.get("customer").toString() );
	}
	
}