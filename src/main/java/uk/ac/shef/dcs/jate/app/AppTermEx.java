package uk.ac.shef.dcs.jate.app;

import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.algorithm.TermEx;
import uk.ac.shef.dcs.jate.feature.*;
import uk.ac.shef.dcs.jate.model.JATETerm;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppTermEx extends App {
	private final Logger log = LoggerFactory.getLogger(AppTermEx.class.getName());

	/**
	 * @param args
	 *            command-line params accepting solr home path, solr core name
	 *            <p>
	 *            and more optional run-time parameters
	 * @see uk.ac.shef.dcs.jate.app.AppParams
	 *      <p>
	 *      TermEx required parameter: reference frequency file
	 * @see AppParams#REFERENCE_FREQUENCY_FILE
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			printHelp();
			System.exit(1);
		}
		String solrHomePath = args[args.length - 2];
		String solrCoreName = args[args.length - 1];

		Map<String, String> params = getParams(args);
		String jatePropertyFile = getJATEProperties(params);
		String corpusDir = getCorpusDir(params);

		List<JATETerm> terms;
		try {
			App termex = new AppTermEx(params);
			if (isCorpusProvided(corpusDir)) {
				termex.index(Paths.get(corpusDir), Paths.get(solrHomePath), solrCoreName, jatePropertyFile);
			}

			terms = termex.extract(solrHomePath, solrCoreName, jatePropertyFile);

			if (isExport(params)) {
				termex.write(terms);
			}

			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JATEException e) {
			e.printStackTrace();
		}

	}

	/**
	 * @param initParams
	 *            pre-filtering, post-filtering parameters, and TermEx specific
	 *            parameter
	 * @throws JATEException
	 * @see AppParams
	 * @see AppParams#REFERENCE_FREQUENCY_FILE
	 */
	public AppTermEx(Map<String, String> initParams) throws JATEException {
		super(initParams);
		initalizeRefFreqParam(initParams);
	}

	public AppTermEx() {

	}

	@Override
	public List<JATETerm> extract(SolrCore core, String jatePropertyFile) throws IOException, JATEException {
		JATEProperties properties = getJateProperties(jatePropertyFile);

		return extract(core, properties);
	}

	public List<JATETerm> extract(SolrCore core, JATEProperties properties) throws JATEException {
		SolrIndexSearcher searcher = core.getSearcher().get();
		try {
			this.freqFeatureBuilder = new FrequencyTermBasedFBMaster(searcher, properties, 0);
			this.freqFeature = (FrequencyTermBased) freqFeatureBuilder.build();

			FrequencyTermBasedFBMaster fwbb = new FrequencyTermBasedFBMaster(searcher, properties, 1);
			FrequencyTermBased fwb = (FrequencyTermBased) fwbb.build();

			TTFReferenceFeatureFileBuilder ftrb = new TTFReferenceFeatureFileBuilder(this.referenceFrequencyFilePath);
			FrequencyTermBased frb = ftrb.build();

			FrequencyCtxDocBasedFBMaster fdbb = new FrequencyCtxDocBasedFBMaster(searcher, properties, 0);
			FrequencyCtxBased fdb = (FrequencyCtxBased) fdbb.build();

			TermEx termex = new TermEx();
			termex.registerFeature(FrequencyTermBased.class.getName(), this.freqFeature);
			termex.registerFeature(FrequencyTermBased.class.getName() + TermEx.SUFFIX_WORD, fwb);
			// you can register multiple reference feature, as long as the key
			// has prefix = FrequencyTermBased.class.getName()+TermEx.SUFFIX_REF
			termex.registerFeature(FrequencyTermBased.class.getName() + TermEx.SUFFIX_REF, frb);
			termex.registerFeature(FrequencyCtxBased.class.getName() + TermEx.SUFFIX_DOC, fdb);

			List<String> candidates = new ArrayList<>(this.freqFeature.getMapTerm2TTF().keySet());

			filterByTTF(candidates);

			List<JATETerm> terms = termex.execute(candidates);
			terms = cutoff(terms);

			addAdditionalTermInfo(terms, searcher, properties.getSolrFieldNameJATENGramInfo(),
					properties.getSolrFieldNameID());
			return terms;
		} finally {
			try {
				searcher.close();
			} catch (IOException e) {
				log.error(e.toString());
			}
		}
	}

	protected static void printHelp() {
		StringBuilder sb = new StringBuilder("TermEx Usage:\n");
		sb.append("java -cp '[CLASSPATH]' ").append(AppATTF.class.getName()).append(" [OPTIONS] ")
				.append("-r [REF_TERM_TF_FILE] [SOLR_HOME_PATH] [SOLR_CORE_NAME] [JATE_PROPERTY_FILE]")
				.append("\nE.g.:\n");
		sb.append(
				"java -cp '/libs/*' -t 20 -r /resource/bnc_unifrqs.normal /solr/server/solr/ jate jate.properties ...\n\n");
		sb.append("[OPTIONS]:\n")
				.append("\t\t-c\t\t'true' or 'false'. Whether to collect term information, e.g., offsets in documents. Default is false.\n")
				.append("\t\t-t\t\tA number. Score threshold for selecting terms. If not set then default -n is used.")
				.append("\n")
				.append("\t\t-n\t\tA number. If an integer is given, top N candidates are selected as terms. \n")
				.append("\t\t\t\tIf a decimal number is given, top N% of candidates are selected. Default is 0.25.\n");
		sb.append("\t\t-o\t\tA file path. If provided, the output is written to the file. \n")
				.append("\t\t\t\tOtherwise, output is written to the console.");
		System.out.println(sb);
	}
}
