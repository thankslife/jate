package uk.ac.shef.dcs.jate.feature;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.solr.search.SolrIndexSearcher;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import org.apache.log4j.Logger;

/**
 * <p>This class creates context windows of a given size around candidate terms or words, and counts
 * frequency of candidate terms/words appearing in the context windows. Such frequencies can be later
 * used to create co-ocurrence statistics. (This is the master class that triggers multi-thread workers
 * that actually deals with the computation.)<p>
 *
 * </p>Due to the nature of term/word order in a sentence, it is very likely that context windows
 * can <b>overlap</b>, or in other words, a candidate can appear in multiple contexts and its frequency is double-counted.
 *
 * </p>For example:
 * </br>George was born in the <u>city</u> of <u>Leeds</u> to a rich <u>banker's</u> <u>family</u>.
 * Three candidate terms are found In the above sentence: city, Leeds, banker's. Given a window size of 6, i.e.,
 * the left 5 plus right 5 tokens around a term, and given that we create windows for each term, we get three windows:
 *
 * <ul>
 *     <li>start: George; end: banker's; contains: city, leeds, banker's</li>
 *     <li>start: was; end: family; contains: city, leeds, banker's, family</li>
 *     <li>start: of; end: family; contains: city, leeds, banker's, family</li>
 *     <li>start: of; end: family; contains: leeds, banker's, family</li>
 * </ul>
 *
 * </p>"leeds" and "banker" co-occur with each other in all four contexts, but in fact, their true co-occurrence count
 * should be 1, because the three contexts have substantial overlap.
 *
 * </p><b>This class instead, generates 'lesser overlapping' context windows as follows:</b> generate a context window
 * for the first candidate (order of appearance in a sentence) in the sentence. Then continue to generate a context
 * window for the next candidate in the same sentence and NOT already included in the previous context window. As an
 * example, the following windows are generated for the above sentence:
 *
 * <ul>
 *     <li>start: George; end: banker's; contains: city, leeds, banker's</li>
 *     <li>start: of; end: family; contains: leeds, banker's, family</li>
 * </ul>
 *
 *</p> And in the meantime, we also generate a ContextOverlap class object that keeps details of the previous and the
 * following contexts, and the list of terms (multi-set) appearing in the overlapping zone.
 *
 * </p> This design ensures that
 * <ul>
 *     <li>only two adjacent contexts can overlap with each other</li>
 *     <li>keep track of terms appearing in overlap and their frequency in the overlapping zone</li>
 * </ul>
 *
 * </p> Thus to calculate the co-occurrence of "leeds" and "banker's", we count the pair's frequency in each context
 * they appear in, then deduce their frequency in the overlapping zones, if any.
 *
 * </p>
 * @see ContextOverlap
 * @see CooccurrenceFBMaster
 * @see FrequencyCtxWindowBasedFBWorker
 */
public class FrequencyCtxWindowBasedFBMaster extends AbstractFeatureBuilder {
    private static final Logger LOG = Logger.getLogger(FrequencyCtxWindowBasedFBMaster.class.getName());

    private int termOrWord; //0 means term; 1 means word
    private int window;
    private Map<Integer, List<ContextWindow>> contextLookup;

    /**
     * @param solrIndexSearcher
     * @param properties
     * @param existingContextWindows  if we want to use context windows already generated by another process
     *                          of FrequencyCtxWindowBasedFBMaster, pass them here. In that case, the existing
     *                                context windows are used as references, within which candidate terms/words
     *                                are searched. Otherwise, context
     *                          windows will be generated, in this case pass null or an empty set
     * @param window
     * @param termOrWord
     */
    public FrequencyCtxWindowBasedFBMaster(SolrIndexSearcher solrIndexSearcher,
                                           JATEProperties properties,
                                           Set<ContextWindow> existingContextWindows,
                                           int window, int termOrWord) {
        super(solrIndexSearcher, properties);
        this.termOrWord = termOrWord;
        this.window = window;
        if (existingContextWindows != null) {
            contextLookup=new HashMap<>();
            for (ContextWindow ctx : existingContextWindows) {
                List<ContextWindow> container = contextLookup.get(ctx.getDocId());
                if (container == null)
                    container = new ArrayList<>();
                container.add(ctx);
                contextLookup.put(ctx.getDocId(), container);
            }
        }
    }

    @Override
    public AbstractFeature build() throws JATEException {
        FrequencyCtxBased feature = new FrequencyCtxBased();
        List<Integer> allDocs = new ArrayList<>();
        for (int i = 0; i < solrIndexSearcher.maxDoc(); i++) {
            allDocs.add(i);
        }

        try {
            Set<String> allCandidates;
            if (termOrWord == 0)
                allCandidates = getUniqueTerms();
            else
                allCandidates = getUniqueWords();


            //start workers
            int cores = properties.getMaxCPUCores();
            cores = cores == 0 ? 1 : cores;
            int maxPerThread = allDocs.size() / cores;
            if (maxPerThread == 0)
                maxPerThread = 50;

            FrequencyCtxWindowBasedFBWorker worker = new
                    FrequencyCtxWindowBasedFBWorker(feature, properties, allDocs, allCandidates,
                    solrIndexSearcher,
                    contextLookup,
                    window, maxPerThread
            );
            StringBuilder sb = new StringBuilder("Building features using cpu cores=");
            sb.append(cores).append(", total docs=").append(allDocs.size()).append(", max per worker=")
                    .append(maxPerThread);
            LOG.info(sb.toString());
            ForkJoinPool forkJoinPool = new ForkJoinPool(cores);
            int total = forkJoinPool.invoke(worker);
            sb = new StringBuilder("Complete building features. Total sentence ctx=");
            sb.append(feature.getMapCtx2TTF().size()).append(", from total processed docs=").append(total);
            LOG.info(sb.toString());
        } catch (IOException ioe) {
            StringBuilder sb = new StringBuilder("Failed to build features!");
            sb.append("\n").append(ExceptionUtils.getFullStackTrace(ioe));
            LOG.error(sb.toString());
            throw new JATEException(sb.toString());
        }
        return feature;
    }
}
