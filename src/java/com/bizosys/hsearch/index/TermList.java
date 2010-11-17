package com.bizosys.hsearch.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bizosys.hsearch.common.IStorable;
import com.bizosys.hsearch.common.Storable;
import com.bizosys.hsearch.schema.ILanguageMap;

/**
 * Organization hint we will use for bucketting.
 * @author karan
 *
 */
public class TermList implements IStorable {
	
	private static final int TERM_SIZE_VECTOR = 8;

	private static final int TERM_SIZE_NOVECTOR = 5;

	public boolean termVectorStorageEnabled = false;
	
	/**
	 * Total terms present for this keyword
	 */
	public int totalTerms;
	
	/**
	 * Document type codes in a array for all terms
	 */
	public byte[] docTypesCodes;

	/**
	 * Term type codes in a array for all terms
	 */
	public byte[] termTypeCodes;

	/**
	 * Term weight in a array for all terms
	 */
	public byte[] termWeight;
	
	/**
	 * How many times the term is sightted in the document
	 */
	public byte[] termFreq;
	
	/**
	 * Which location of the document, the term is positioned
	 */
	public short[] termPosition;
	
	/**
	 * This document is at which location of the bucket
	 */
	public short[] docPos;
	
	/**
	 * All terms are listed here. we merge and keep the same term only in the list.
	 */
	public Map<Integer, List<Term>> lstKeywords = null; 
	
	private byte[] existingB = null;
	
	public TermList() {
	}
	
	public void setExistingBytes(byte[] existingB) {
		this.existingB = existingB;
	}
	
	
	/**
	 * Load by deserializing bytes
	 * @param bytes
	 */
	public void loadTerms(byte[] bytes) {
		if ( null == bytes) return;
		
		int readPosition = 0;
		
		if ( termVectorStorageEnabled ) {
			this.totalTerms = bytes.length / TERM_SIZE_VECTOR;
		} else {
			this.totalTerms = bytes.length / TERM_SIZE_NOVECTOR;
		}
		
		/**
		 * Document types codes
		 */
		docTypesCodes = new byte[this.totalTerms];
		for (int i=0; i<this.totalTerms; i++ ) {
			docTypesCodes[i] = bytes[readPosition++];
		}
		
		/**
		 * Term types codes
		 */
		termTypeCodes = new byte[this.totalTerms];
		for (int i=0; i<this.totalTerms; i++ ) {
			termTypeCodes[i] = bytes[readPosition++];
		}
		
		/**
		 * Term weight
		 */
		this.termWeight = new byte[this.totalTerms];
		for (int i=0; i<this.totalTerms; i++ ) {
			this.termWeight[i] = bytes[readPosition++];
		}
		
		if ( termVectorStorageEnabled ) {
			/**
			 * Term frequency
			 */
			this.termFreq = new byte[this.totalTerms];
			for (int i=0; i<this.totalTerms; i++ ) {
				this.termFreq[i] = bytes[readPosition++];
			}	

			/**
			 * Term Position
			 */
			this.termPosition = new short[this.totalTerms];
			for (int i=0; i<this.totalTerms; i++ ) {
				this.termPosition[i] = 
					(short) ((bytes[readPosition++] << 8 ) + ( bytes[readPosition++] & 0xff ) );
			}	
		}
		
		/**
		 * Document Position
		 */
		this.docPos = new short[this.totalTerms];
		for (int i=0; i<this.totalTerms; i++ ) {
			this.docPos[i] = 
				(short) ((bytes[readPosition++] << 8 ) + ( bytes[readPosition++] & 0xff ) );
		}
	}
	
	/**
	 * Add a keyword. Repetition are taken care
	 * @param aTerm
	 */
	public void add(Term aTerm) {
		if ( null == aTerm) return;

		int keywordHash = aTerm.term.hashCode();			
		if ( null == lstKeywords) {
			lstKeywords = new HashMap<Integer, List<Term>> (ILanguageMap.ALL_COLS.length);
			List<Term> lstTerms = new ArrayList<Term>(3);
			lstTerms.add(aTerm);
			lstKeywords.put(keywordHash, lstTerms);
			return;
		}
		
		boolean isMerged = false;
		if ( lstKeywords.containsKey(keywordHash)) {
			//Ids from same document is merged.
			for ( Term existing : lstKeywords.get(keywordHash)) {
				isMerged = existing.merge(aTerm);
				if ( isMerged ) break;
			}
			if ( !isMerged )  lstKeywords.get(keywordHash).add(aTerm);
			
		} else {
			List<Term> terms = new ArrayList<Term>(3);
			terms.add(aTerm);
			lstKeywords.put(keywordHash, terms);
		}
	}
	
	/**
	 * Add a complete list here.
	 * @param anotherList
	 */
	public void add(TermList anotherList) {
		if ( null == anotherList.lstKeywords) return;
		for (List<Term> anotherTerms : anotherList.lstKeywords.values()) {
			for (Term anotherTerm : anotherTerms) {
				this.add(anotherTerm);
			}
		}
	}
	
	/**
	 * Remove all the ids from another which are absent here.
	 * Remove all the ids from here which are absent another
	 * @param another
	 * @return : After intersect has any element left?
	 */
	public boolean intersect(TermList another) {
		
		if ( 0 == this.totalTerms) another.cleanup();
		if ( 0 == another.totalTerms) this.cleanup();

		if ( null == this.docPos) another.cleanup();
		if ( null == another.docPos) this.cleanup();

		if ( 0 == this.totalTerms) return false;
		
		boolean notSubsetting = true;
		short aPos = -1;
		int totalMatching = 0;
		int posT = this.docPos.length;
		
		for (int i=0; i<posT; i++) {
			aPos = this.docPos[i];
			if ( -1 == aPos) continue;
			notSubsetting = true;
			for ( short bPos : another.docPos) {
				if ( aPos == bPos) {
					notSubsetting = false; totalMatching++; break;
				}
			}
			if ( notSubsetting )  this.docPos[i] = -1;
		}
		
		/**
		 * No terms matched
		 */
		if ( 0 == totalMatching) {
			this.cleanup();
			another.cleanup();
			return false;
		}
		
		/**
		 * Set other document positions also as -1
		 */
		posT = another.docPos.length;
		for (int i=0; i<posT; i++) {
			aPos = another.docPos[i];
			if ( -1 == aPos) continue;
			notSubsetting = true;
			
			//Is this existing in other list
			for ( short posAno : this.docPos) {
				if ( aPos == posAno) {
					notSubsetting = false; totalMatching--; break;
				}
			}
			if ( notSubsetting )  another.docPos[i] = -1;
			if ( -1 == totalMatching) break; //Don't process unnecessarily
		}
		return true;
	}
	
	/**
	 * This keeps matching ids only of another termlist
	 * @param another  After subsetting has any element left?
	 */
	public boolean subset(TermList another) {
		if ( 0 == another.totalTerms) this.cleanup();
		if ( null == another.docPos) this.cleanup();
		if ( 0 == this.totalTerms) return false;
		
		short aPos = -1;
		int posT = this.docPos.length;
		boolean eliminate = true;
		boolean noneFound = true;

		for ( int i=0; i<posT; i++) { //This term
			aPos = this.docPos[i];
			if ( -1 == aPos) continue;
			eliminate = true;
			for (short bPos : another.docPos) { //Any presence @ must terms
				if ( -1 == bPos) continue;
				if ( aPos == bPos) {
					eliminate = false;
					noneFound = false;
					break;
				}
			}
			if (eliminate) this.docPos[i] = -1;
		}

		/**
		 * No terms matched
		 */
		if ( noneFound ) {
			this.cleanup(); 
			return false;
		}
		
		return true;
	}
	
	/**
	 * The given document id will be applied to 
	 * @param position
	 */
	public void assignDocPos(int position) {
		if ( null == this.lstKeywords) return;
		short pos = (short) position;
		for (List<Term> terms : lstKeywords.values()) {
			for (Term term : terms) {
				term.setDocumentPosition(pos);
			}
		}
	}
	

	/**
	 * Serialize this
	 * KeywordHash1/byte(SIZE > 256)/Integer(SIZE)/BYTES
	 * KeywordHash2/byte(SIZE)/Integer(SIZE)/BYTES
	 */
	public byte[] toBytes() {
		
		if ( null == lstKeywords) return null;
		
		this.mergeBytes();
		System.out.println("lstKeywords:" + this.toString());
		
		int totalBytes = 0;
		int termsT = 0;
		List<Term> lstTerms  = null;

		for (int hash : lstKeywords.keySet()) {
			totalBytes = totalBytes + 4; /**Keyword Hash*/ 
			lstTerms  = lstKeywords.get(hash);
			termsT = lstTerms.size();
			if ( termsT < Byte.MAX_VALUE) totalBytes++;  /**Low density*/ 
			else totalBytes = totalBytes + 5; /**High density*/
			if ( termVectorStorageEnabled ) { /**Terms*/
				totalBytes = totalBytes + termsT * 8;
			} else {
				totalBytes = totalBytes + termsT * 5;
			}
		} 
		
		byte[] bytes = new byte[totalBytes];
		int pos = 0;
		short tp = 0, dp = 0;

		for (int hash : lstKeywords.keySet()) {

			/**
			 * Add the keyword hash
			 */
			System.arraycopy(Storable.putInt(hash), 0, bytes, pos, 4);
			pos = pos + 4;
			
			/**
			 * Add the total terms
			 */
			lstTerms  = lstKeywords.get(hash);
			termsT = lstTerms.size();
			if ( termsT < Byte.MAX_VALUE) {
				bytes[pos++] = (byte)(termsT);
			} else {
				bytes[pos++] = (byte)(-1);  
				System.arraycopy(Storable.putInt(termsT), 0, bytes, pos, 4);
				pos = pos + 4;
			}
			
			/**
			 * Document types codes
			 */
			for (Term t : lstTerms) {
				bytes[pos++] = t.getDocumentTypeCode();
			}
			
			/**
			 * Term types codes
			 */
			for (Term t : lstTerms) {
				bytes[pos++] = t.getTermTypeCode();
			}
			
			/**
			 * Term weight
			 */
			for (Term t : lstTerms) {
				bytes[pos++] = t.getTermWeight();
			}
			
			if ( termVectorStorageEnabled ) {
				
				/**
				 * Term frequency
				 */
				for (Term t : lstTerms) {
					bytes[pos++] = t.getTermFrequency();
				}		
		
				/**
				 * Term Position
				 */
				for (Term t : lstTerms) {
					tp = t.getTermPosition();
					bytes[pos++] = (byte)(tp >> 8 & 0xff);
					bytes[pos++] = (byte)(tp & 0xff);
				}
			}

			/**
			 * Document Position
			 */
			for (Term t : lstTerms) {
				dp = t.getDocumentPosition();
				System.out.println("ToBytes getDocumentPosition :" + dp);
				bytes[pos++] = (byte)(dp >> 8 & 0xff);
				bytes[pos++] = (byte)(dp & 0xff);
			}
		}
		return bytes;
	}
	
	/**
	 * Merge the supplied document list with the documents
	 * already present in the bucket.
	 * 
	 * Ignore all the supplied documents while loading from bytes the existing ones
	 * Create the Term List 
	 *
	 */
	public void mergeBytes() {
		if ( null == existingB) return;

		short docPos;
		Set<Short> freshDocs = getFreshDocs();
		
		int bytesT = existingB.length;
		List<Term> priorDocTerms = new ArrayList<Term>();
		int keywordHash = -1, termsT = -1, shift = 0, pos = 0, readPos=0;
		byte docTyep=0,termTyep=0,termWeight=0,termFreq=0;
		short termPos=0;
		
		while ( pos < bytesT) {
			if ( L.l.isDebugEnabled() ) 
				L.l.debug("TermList Byte Marshalling: (pos:bytesT) = " + pos + ":" + bytesT);
			
			priorDocTerms.clear();
			keywordHash = Storable.getInt(pos, existingB);
			pos = pos + 4;

			/**
			 * Compute number of terms presence.
			 */
			termsT = existingB[pos++];
			if ( -1 == termsT ) {
				termsT =  Storable.getInt(pos, existingB);
				pos = pos + 4;
			} 
			if ( L.l.isDebugEnabled() ) L.l.debug("termsT:" + termsT + ":" + pos );
			
			/**
			 * Compute Each Term.
			 */
			shift = TERM_SIZE_NOVECTOR;
			if ( termVectorStorageEnabled ) shift = TERM_SIZE_VECTOR;
			for ( int i=0; i<termsT; i++) {
				if ( L.l.isDebugEnabled() ) L.l.debug("pos:" + pos );
				
				readPos = pos + ((shift - 2) * termsT )+ (i * 2);
				docPos = (short) ((existingB[readPos] << 8 ) + 
					( existingB[++readPos] & 0xff ));
				
				if ( freshDocs.contains(docPos)) continue;
				
				docTyep = existingB[pos+i];
				termTyep = existingB[pos + termsT + i];
				termWeight = existingB[pos + (2 * termsT) + i];
				
				if ( termVectorStorageEnabled ) {
					termFreq = existingB[pos + (3 * termsT) + i];
					readPos = pos + (4 * termsT) + i;
					termPos = (short) ( (existingB[readPos] << 8 ) + 
							( existingB[++readPos] & 0xff ) );
				}
				Term priorTerm = new Term(docPos,docTyep,termTyep,termWeight,termPos,termFreq);
				System.out.println(priorTerm.toString());
				priorDocTerms.add(priorTerm);
			}

			if ( termVectorStorageEnabled ) pos = pos + (8 * termsT);
			else pos = pos + (5 * termsT);
			mergePriorDocTerms(priorDocTerms, keywordHash);
		}
	}

	private void mergePriorDocTerms(List<Term> priorDocTerms, int keywordHash) {
		if ( priorDocTerms.size() > 0 ) {
			List<Term> terms = null;
			if ( lstKeywords.containsKey(keywordHash) ) { //This Keyword exists
				terms = lstKeywords.get(keywordHash);
				terms.addAll(priorDocTerms);
			} else {
				lstKeywords.put(keywordHash, priorDocTerms);
			}
		}
	}

	private Set<Short> getFreshDocs() {
		Set<Short> freshDocs = new HashSet<Short>();
		short docPos;
		for (int hash : lstKeywords.keySet()) {
			List<Term> terms = lstKeywords.get(hash);
			for (Term term : terms) {
				docPos = term.getDocumentPosition();
				if ( freshDocs.contains(docPos)) continue;
				freshDocs.add(docPos);
			}
		}
		if ( L.l.isDebugEnabled() ) 
			L.l.debug("Fresh Documents:" + freshDocs.toString());
		return freshDocs;
	}
		
	
	/**
	 * Does this list contain the keyword.
	 * @param bytes
	 * @param keywordHash
	 * @param pos
	 * @return
	 */
	public static boolean isMatchedTerm(byte[] bytes, 
			byte[] keywordHash, int pos) {
			
			return 	(bytes[pos++] == keywordHash[0]) &&
					(bytes[pos++] == keywordHash[1]) &&
					(bytes[pos++] == keywordHash[2]) &&
					(bytes[pos++] == keywordHash[3]);
	}
	
	public void cleanup() {
		totalTerms = 0;
		this.docTypesCodes = null;
		this.termTypeCodes = null;
		this.termWeight = null;
		this.termFreq = null;
		this.termPosition = null;
		this.docPos = null;
		if (null != lstKeywords) {
			for (List<Term> lt : lstKeywords.values()) {
				if ( null == lt) continue;
				lt.clear();	
			}
			lstKeywords.clear();
		}
		this.existingB = null;
	}
	
	@Override
	public String toString() {
		
		if ( null != this.lstKeywords) {
			StringBuilder sb = new StringBuilder(" TermList : ");
			for (int termHash: this.lstKeywords.keySet()) {
				sb.append(termHash).append(" : ");
				for ( Term aTerm : this.lstKeywords.get(termHash)) {
					sb.append("\n\t\t\t\t\t").append(aTerm.toString());
				}
			}
			return sb.toString();
		} else {
			StringBuilder sb = new StringBuilder("\nTermList Total : ");
			sb.append(totalTerms);
			for ( int i=0; i< totalTerms; i++) {
				sb.append("\nPositions: ").append(this.docPos[i]);
				sb.append(" Weight: ").append(this.termWeight[i]);
			}
			return sb.toString();
		}
		
	}
	
}