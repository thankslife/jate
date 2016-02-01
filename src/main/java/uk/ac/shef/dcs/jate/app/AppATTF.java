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

        List<JATETerm> terms;
        try {
            AppATTF app = new AppATTF(params);
            terms = app.extract(solrHomePath, solrCoreName, jatePropertyFile);
            app.write(terms);
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

    public AppATTF() {
    }

    @Override
    public List<JATETerm> extract(SolrCore core, String jatePropertyFile)
            throws IOException, JATEException {
        JATEProperties properties = new JATEProperties(jatePropertyFile);

        return extract(core, properties);
    }

    public List<JATETerm> extract(SolrCore core, JATEProperties properties) throws JATEException, IOException {
        SolrIndexSearcher searcher = core.getSearcher().get();
        try {
            this.freqFeatureBuilder = new FrequencyTermBasedFBMaster(searcher, properties, FrequencyTermBasedFBMaster.FEATURE_TYPE_TERM);
            this.freqFeature = (FrequencyTermBased) freqFeatureBuilder.build();

            Algorithm attf = new ATTF();
            attf.registerFeature(FrequencyTermBased.class.getName(), freqFeature);

            List<String> candidates = new ArrayList<>(freqFeature.getMapTerm2TTF().keySet());

            filterByTTF(candidates);

            List<JATETerm> terms = attf.execute(candidates);

            terms = cutoff(terms);

            addAdditionalTermInfo(terms, searcher, properties.getSolrFieldNameJATENGramInfo(),
                    properties.getSolrFieldNameID());

            return terms;
        } finally {
            searcher.close();
        }
    }

//	public List<JATETerm> extract(SolrCore core, JATEProperties jateProperties) {
//		SolrIndexSearcher searcher = core.getSearcher().get();
//
//	}

}
