package uk.ac.shef.dcs.jate.app;

import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;

import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.algorithm.ATTF;
import uk.ac.shef.dcs.jate.algorithm.Algorithm;
import uk.ac.shef.dcs.jate.feature.FrequencyTermBased;
import uk.ac.shef.dcs.jate.feature.FrequencyTermBasedFBMaster;
import uk.ac.shef.dcs.jate.model.JATETerm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppATTF extends App {
	public static void main(String[] args) {
		if (args.length < 1) {
			printHelp();
			System.exit(1);
		}
		String solrHomePath = args[args.length - 3];
		String solrCoreName = args[args.length - 2];
		String jatePropertyFile = args[args.length - 1];

		Map<String, String> params = getParams(args);

		String paramValue = params.get(AppParams.OUTPUT_FILE.getParamKey());
		List<JATETerm> terms;
		try {
			terms = new AppATTF(params).extract(solrHomePath, solrCoreName, jatePropertyFile);

			write(terms, paramValue);
		} catch (IOException e) {
			System.err.println("IO Exception when exporting terms!");
			e.printStackTrace();
		} catch (JATEException e) {
			e.printStackTrace();
		}
	}

	public AppATTF(Map<String, String> initParams) throws JATEException {
		super(initParams);
	}

	@Override
	public List<JATETerm> extract(SolrCore core, String jatePropertyFile)
			throws IOException, JATEException {
		SolrIndexSearcher searcher = core.getSearcher().get();
		try {
			JATEProperties properties = new JATEProperties(jatePropertyFile);
			this.freqFeatureBuilder = new FrequencyTermBasedFBMaster(searcher, properties, 0);
			this.freqFeature = (FrequencyTermBased) freqFeatureBuilder.build();
			
			Algorithm attf = new ATTF();
			attf.registerFeature(FrequencyTermBased.class.getName(), freqFeature);

			List<String> candidates = new ArrayList<>(freqFeature.getMapTerm2TTF().keySet());
			
			filterByTTF(candidates, this.prefilterMinTTF);
			
			List<JATETerm> terms = attf.execute(candidates);

			terms = filter(terms);

			addAdditionalTermInfo(terms, searcher, properties.getSolrFieldnameJATENGramInfo(),
					properties.getSolrFieldnameID());
			
			return terms;
		} finally {
			searcher.close();
		}
	}

}
