package com.bizosys.hsearch.inpipe;

import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import com.bizosys.oneline.ApplicationFault;
import com.bizosys.oneline.SystemFault;
import com.bizosys.oneline.conf.Configuration;
import com.bizosys.oneline.pipes.PipeIn;

import com.bizosys.hsearch.index.Doc;
import com.bizosys.hsearch.index.TermStream;
import com.bizosys.hsearch.inpipe.util.ReaderType;

public class TokenizeKeyword extends TokenizeBase implements PipeIn {

	public TokenizeKeyword() {
		super();
	}
	
	public PipeIn getInstance() {
		return this;
	}

	public String getName() {
		return "TokenizeKeyword";
	}

	public boolean init(Configuration conf) throws ApplicationFault,SystemFault {
		return true;
	}

	public boolean visit(Object docObj) throws ApplicationFault, SystemFault {
		if ( null == docObj) return false;
		Doc doc = (Doc) docObj;
		List<ReaderType> readers = super.getReaders(doc);
    	if (null == readers) return true;
		
		try {
	    	for (ReaderType reader : readers) {
	    		Analyzer analyzer = new KeywordAnalyzer();
	    		TokenStream stream = analyzer.tokenStream(
	    				reader.type, reader.reader);
	    		TermStream ts = new TermStream(
	    			reader.docSection, stream, reader.type); 
	    		doc.terms.addTokenStream(ts);
	    		//Note : The reader.reader stream is already closed.
			}
	    	return true;
    	} catch (Exception ex) {
    		throw new ApplicationFault(ex);
    	}
	}

	public boolean commit() throws ApplicationFault, SystemFault {
		return true;
	}
}