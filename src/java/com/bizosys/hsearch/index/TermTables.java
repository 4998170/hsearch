package com.bizosys.hsearch.index;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.bizosys.oneline.ApplicationFault;
import com.bizosys.oneline.SystemFault;

import com.bizosys.hsearch.common.IStorable;
import com.bizosys.hsearch.common.Storable;
import com.bizosys.hsearch.hbase.HLog;
import com.bizosys.hsearch.hbase.HReader;
import com.bizosys.hsearch.hbase.HWriter;
import com.bizosys.hsearch.hbase.NV;
import com.bizosys.hsearch.hbase.NVBytes;
import com.bizosys.hsearch.schema.ILanguageMap;
import com.bizosys.hsearch.schema.IOConstants;
import com.bizosys.hsearch.util.Record;
import com.bizosys.hsearch.util.RecordScalar;

public class TermTables {
	
	private static final byte[] BUCKET_COUNTER_BYTES = "BUCKET_COUNTER".getBytes();
	static {init();}
	
	public IStorable bucketId = null;
	public Map<Character, TermFamilies> tables = null;
	
	public TermTables() {
	}
	
	public TermTables(IStorable bucketId) {
		this.bucketId = bucketId;
	}
	
	public void add(Term aTerm, ILanguageMap lang) {
		if ( null == tables) tables = new HashMap<Character, TermFamilies>();
		
		Character table = lang.getTableName(aTerm.term);
		TermFamilies block = null;
		if ( tables.containsKey(table)) block  = tables.get(table);
		else {
			block = new TermFamilies();
			tables.put(table, block);
		}
		block.add(aTerm, lang);
	}
	
	public boolean add(TermTables another) {
		if ( null == another.bucketId) return false;
		
		byte[] anotherPK = another.bucketId.toBytes();
		if ( !Storable.compareBytes(this.bucketId.toBytes(), anotherPK) ) return false;
		
		/**
		 * Both belong to same bucket zone
		 */
		for (Character otherTable : another.tables.keySet()) {
			TermFamilies otherFamilies = another.tables.get(otherTable);
			
			if (this.tables.containsKey(otherTable)) {
				TermFamilies thisFamilies = this.tables.get(otherTable);
				thisFamilies.add(otherFamilies);
			} else {
				this.tables.put(otherTable, otherFamilies);
			}
		}
		return true;
	}

	public void assignDocumentPosition(int docPos) {
		if ( null == tables) return;
		for ( TermFamilies tf : tables.values()) {
			if ( null == tf ) continue;
			tf.assignDocumentPosition(docPos);
		}
	}
	
	public void persist(boolean merge) throws SystemFault, ApplicationFault {
		try {
			for ( Character tableName : tables.keySet()) {
				TermFamilies termFamilies = tables.get(tableName);
				
				if ( merge ) setExistingValue(
					tableName.toString(),termFamilies);
				
				List<NV> nvs = new Vector<NV>(200);
				termFamilies.toNVs(nvs);
				Record record = new Record(bucketId,nvs);
				if  (HLog.l.isDebugEnabled()) 
					HLog.l.debug("TermTables.persist Table " + tableName + record.toString());
				HWriter.insert(tableName.toString(), record, true);
			}
		} catch (Exception ex) {
			throw new SystemFault(ex);
		}
	}
	
	/**
	 * Populates the existing value.
	 * @param tableName
	 * @param termFamilies
	 * @throws ApplicationFault
	 */
	public void setExistingValue(String tableName, 
		TermFamilies termFamilies) throws ApplicationFault {
		
		List<NVBytes> existingB = 
			HReader.getCompleteRow(tableName, bucketId.toBytes());
		if ( null == existingB) return;
		
		for (char family: termFamilies.families.keySet()) {
			TermColumns cols = termFamilies.families.get(family);
			for (char col : cols.columns.keySet()) {
				TermList terms = cols.columns.get(col);
				
				for (NVBytes bytes : existingB) {
					if ( bytes.family[0] == family && bytes.name[0] == col) {
						terms.setExistingBytes(bytes.data);
						break;
					}
				}
			}
		}
	}
	
	/**
	 * Get the Running bucket Id
	 */
	public static long getCurrentBucketId() throws ApplicationFault{
		HLog.l.info("TermBucket > aquiring the running bucket.");
		
		NV nv = new NV(IOConstants.NAME_VALUE_BYTES,IOConstants.NAME_VALUE_BYTES);
		RecordScalar scalar = new RecordScalar(BUCKET_COUNTER_BYTES, nv); 
		HReader.getScalar(IOConstants.TABLE_CONFIG,scalar);
		long currentBucket = Storable.getLong(0, nv.data.toBytes());

		if ( HLog.l.isInfoEnabled()) 
			HLog.l.info("AddToIndex > Running bucket = " + currentBucket);
		return currentBucket;
	}
	
	
	/**
	 * This creates bucket Id, unique across machines.
	 * @return
	 * @throws ApplicationFault
	 */
	public static long createBucketId() throws ApplicationFault{
		
		HLog.l.debug("TermBucket > Creating a new bucket Zone");
		
		/**
		 * Get next bucket Id
		 */
		NV nv = new NV(IOConstants.NAME_VALUE_BYTES,IOConstants.NAME_VALUE_BYTES);
		RecordScalar scalar = new RecordScalar(BUCKET_COUNTER_BYTES, nv); 
		long bucketId = HReader.generateKeys(IOConstants.TABLE_CONFIG,scalar,1);

		/**
		 * Put the bucket as a row for counting document serials. 
		 */
		HLog.l.debug("TermBucket > Setting serial counter for this bucket :" + bucketId);

		long startPos = Short.MIN_VALUE;
		nv.data = new Storable(startPos);
		RecordScalar docSerial = new RecordScalar(
			Storable.putLong(bucketId), nv); 
		try {
			HWriter.insert(IOConstants.TABLE_CONFIG, docSerial, true);
			HLog.l.info("TermBucket > Bucket setup completed :" + bucketId);
			return bucketId;
		} catch (IOException ex) {
			HLog.l.fatal("TermBucket > Setting serial counter Failed:" + bucketId, ex);
			throw new ApplicationFault(ex);
		}
	}
	
	/**
	 * This create document serial no inside a bucket id, unique across machines
	 * @param bucketId
	 * @param amount
	 * @return
	 * @throws ApplicationFault
	 * @throws BucketIsFullException
	 */
	public static short createDocumentSerialIds(long bucketId, int amount) 
		throws SystemFault, ApplicationFault, BucketIsFullException {
		
		/**
		 * Generate Ids for this bucket
		 */
		
		HLog.l.debug("Generating buckets keys");
		NV nv = new NV(IOConstants.NAME_VALUE_BYTES,IOConstants.NAME_VALUE_BYTES);
		byte[] pkBucketId = Storable.putLong(bucketId);
		RecordScalar scalar = new RecordScalar(pkBucketId, nv);
		long bucketMaxPos =  
			HReader.generateKeys(IOConstants.TABLE_CONFIG,scalar,amount);
		HLog.l.debug("Buckets keys generated :" + bucketMaxPos);

		int maxValue = Short.MAX_VALUE - Short.MIN_VALUE;
		if (  bucketMaxPos >= maxValue) {
			HLog.l.warn("Crossed the bucket limit of storage :" + bucketMaxPos);
			BucketIsFullException bife = new BucketIsFullException(bucketMaxPos);
			throw bife;
		}
		return new Long(bucketMaxPos).shortValue();
	}
	
	/**
	 * Initializes the term buckets
	 * Initial System: There will be no bucket. Start from Long.MIN_VALUE
	 * Second time onwards : Continue 
	 */
	public static void init() {
		try {
			NV nv = new NV(IOConstants.NAME_VALUE_BYTES,IOConstants.NAME_VALUE_BYTES);
			if ( ! HReader.exists(IOConstants.TABLE_CONFIG, BUCKET_COUNTER_BYTES)) {
				HLog.l.info("Bucket Counter setup is not there. Setting up bucket id counter.");
				RecordScalar bucketCounter = new RecordScalar(new Storable(BUCKET_COUNTER_BYTES), nv);
				nv.data = new Storable(Long.MIN_VALUE);
				HWriter.insert(IOConstants.TABLE_CONFIG, bucketCounter, true);
				HLog.l.info("Bucket Counter setup is complete.");
			}
		} catch (IOException ex) {
			HLog.l.fatal("TermBucket > Bucker Bucket Counter Creation Failure:", ex);
			System.exit(1);
		} catch (ApplicationFault ex) {
			HLog.l.fatal("TermBucket > Bucker Bucket Counter Creation Failure:", ex);
			System.exit(1);
		}
	}
}